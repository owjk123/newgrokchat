package com.newgrokchat.data.api

import android.util.Log
import com.google.gson.Gson
import com.newgrokchat.model.ApiConfig
import com.newgrokchat.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketException
import java.util.concurrent.TimeUnit

class GrokApiClient {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .build()
    
    private val gson = Gson()
    
    companion object {
        private const val TAG = "GrokApiClient"
        private const val MAX_RETRIES = 3
        private val RETRY_DELAYS = listOf(1000L, 2000L, 3000L) // 指数退避延迟
    }
    
    /**
     * 发送消息，自动在所有端点之间轮换重试
     */
    suspend fun sendMessage(
        endpoints: List<String>,
        apiKey: String,
        model: String,
        messages: List<Message>,
        systemPrompt: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        
        for (endpoint in endpoints) {
            try {
                val result = trySendMessage(endpoint, apiKey, model, messages, systemPrompt)
                if (result.isSuccess) {
                    return@withContext result
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    errors.add("$endpoint: $errorMsg")
                }
            } catch (e: Exception) {
                errors.add("$endpoint: ${e.message ?: "Unknown error"}")
            }
        }
        
        val errorMessage = if (errors.isNotEmpty()) {
            "所有端点均不可用:\n${errors.joinToString("\n")}"
        } else {
            "所有端点均不可用"
        }
        Result.failure(Exception(errorMessage))
    }
    
    /**
     * 内部发送方法，支持自动重试
     */
    private fun trySendMessage(
        endpoint: String,
        apiKey: String,
        model: String,
        messages: List<Message>,
        systemPrompt: String
    ): Result<String> {
        var lastException: Exception? = null
        
        for (attempt in 0 until MAX_RETRIES) {
            try {
                val result = doSendMessage(endpoint, apiKey, model, messages, systemPrompt)
                if (result.isSuccess || shouldNotRetry(result.exceptionOrNull())) {
                    return result
                }
                // 如果需要重试且还有重试次数，记录并继续
                lastException = result.exceptionOrNull()
            } catch (e: Exception) {
                lastException = e
                if (!shouldRetryOnException(e) || attempt >= MAX_RETRIES - 1) {
                    break
                }
            }
            
            // 如果不是最后一次尝试，等待后重试
            if (attempt < MAX_RETRIES - 1) {
                val delay = RETRY_DELAYS.getOrElse(attempt) { 3000L }
                Log.w(TAG, "Request failed, retrying in ${delay}ms (attempt ${attempt + 1}/$MAX_RETRIES)")
                Thread.sleep(delay)
            }
        }
        
        return Result.failure(lastException ?: Exception("Unknown error after $MAX_RETRIES retries"))
    }
    
    /**
     * 执行实际的HTTP请求
     */
    private fun doSendMessage(
        endpoint: String,
        apiKey: String,
        model: String,
        messages: List<Message>,
        systemPrompt: String
    ): Result<String> {
        return try {
            val chatMessages = mutableListOf<Map<String, String>>()
            
            if (systemPrompt.isNotBlank()) {
                chatMessages.add(mapOf("role" to "system", "content" to systemPrompt))
            }
            
            messages.forEach { 
                chatMessages.add(mapOf(
                    "role" to if (it.isUser) "user" else "assistant", 
                    "content" to it.content
                ))
            }
            
            // 使用 stream = false，与非流式解析逻辑一致
            val requestBody = ApiConfig.ChatRequest(model = model, messages = chatMessages, stream = false)
            val json = gson.toJson(requestBody)
            
            val request = Request.Builder()
                .url("$endpoint/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            
            // 先读取 response body 到变量，避免重复读取
            val responseBodyString = response.body?.string()
            
            if (!response.isSuccessful) {
                // HTTP错误，先尝试解析error信息
                val errorMessage = parseHttpErrorMessage(response.code, responseBodyString)
                return Result.failure(Exception(errorMessage))
            }
            
            // 检查空响应
            if (responseBodyString.isNullOrBlank()) {
                return Result.failure(Exception("服务器返回空响应"))
            }
            
            try {
                val chatResponse = gson.fromJson(responseBodyString, ApiConfig.ChatResponse::class.java)
                
                // 优先检查 API 返回的错误
                if (chatResponse.error != null) {
                    val errorDetail = chatResponse.error.message ?: chatResponse.error.type ?: "API Error"
                    val errorCode = chatResponse.error.code
                    val fullError = if (errorCode != null) "$errorDetail (code: $errorCode)" else errorDetail
                    return Result.failure(Exception(fullError))
                }
                
                // 检查 choices 是否为空
                if (chatResponse.choices.isNullOrEmpty()) {
                    return Result.failure(Exception("响应格式异常: 缺少choices字段"))
                }
                
                // 提取 content
                val firstChoice = chatResponse.choices.firstOrNull()
                val messageMap = firstChoice?.message
                
                if (messageMap == null) {
                    return Result.failure(Exception("响应格式异常: 缺少message字段"))
                }
                
                // content 可能为 null，需要检查
                val content = messageMap["content"]
                if (content.isNullOrBlank()) {
                    return Result.failure(Exception("AI返回了空响应内容，请重试"))
                }
                
                Result.success(content)
            } catch (e: Exception) {
                // 解析失败时返回详细信息
                val truncatedBody = if (responseBodyString.length > 200) {
                    responseBodyString.take(200) + "..."
                } else {
                    responseBodyString
                }
                Result.failure(Exception("响应解析失败: ${e.message} | 原始响应: $truncatedBody"))
            }
        } catch (e: SocketException) {
            Log.e(TAG, "Socket exception: ${e.message}")
            Result.failure(e)
        } catch (e: IOException) {
            Log.e(TAG, "IO exception: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 解析HTTP错误信息，提供本地化的错误提示
     */
    private fun parseHttpErrorMessage(httpCode: Int, responseBody: String?): String {
        // 尝试从响应体中提取错误信息
        val apiErrorMessage = try {
            if (!responseBody.isNullOrBlank()) {
                val errorResponse = gson.fromJson(responseBody, ApiConfig.ChatResponse::class.java)
                errorResponse.error?.message
            } else null
        } catch (e: Exception) {
            null
        }
        
        val localizedMessage = when (httpCode) {
            401 -> "API Key 无效，请在设置中重新填写"
            402 -> "余额不足，请到 api.apiyi.com 充值"
            403 -> "访问被拒绝，请检查API Key权限"
            404 -> "请求的资源不存在"
            429 -> "请求过于频繁，请稍后再试"
            in 500..599 -> "服务器繁忙，请稍后再试"
            else -> null
        }
        
        return if (localizedMessage != null) {
            localizedMessage
        } else {
            val detail = apiErrorMessage ?: (responseBody?.take(100) ?: "Unknown error")
            "请求失败 ($httpCode): $detail"
        }
    }
    
    /**
     * 判断是否应该根据异常类型重试
     */
    private fun shouldRetryOnException(e: Exception): Boolean {
        return when (e) {
            is SocketException -> true
            is IOException -> true
            is java.net.ConnectException -> true
            is java.net.UnknownHostException -> true
            is java.net.InterruptedIOException -> true
            else -> false
        }
    }
    
    /**
     * 判断是否不应该重试（例如明确的应用层错误）
     */
    private fun shouldNotRetry(e: Exception?): Boolean {
        if (e == null) return false
        val message = e.message ?: ""
        // 这些错误重试也不会改变结果
        return message.contains("API Key") ||
               message.contains("余额不足") ||
               message.contains("访问被拒绝") ||
               message.contains("资源不存在") ||
               message.contains("请求失败 (401)") ||
               message.contains("请求失败 (402)") ||
               message.contains("请求失败 (403)") ||
               message.contains("请求失败 (404)") ||
               message.contains("响应解析失败") ||
               message.contains("AI返回了空响应")
    }
}
