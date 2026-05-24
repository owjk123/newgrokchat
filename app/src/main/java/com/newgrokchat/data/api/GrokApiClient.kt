package com.newgrokchat.data.api

import android.util.Base64
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
import java.io.InterruptedIOException
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
        private val RETRY_DELAYS = listOf(1000L, 2000L, 3000L)
    }
    
    /**
     * 发送消息，自动在所有端点之间轮换重试
     * Bug 3修复: 支持多模态消息(图片+文本)
     */
    suspend fun sendMessage(
        endpoints: List<String>,
        apiKey: String,
        model: String,
        messages: List<Message>,
        systemPrompt: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        
        // Bug 3修复: 构建支持多模态的消息列表
        val chatMessages = buildChatMessages(messages, systemPrompt)
        
        for (endpoint in endpoints) {
            try {
                val result = trySendMessage(endpoint, apiKey, model, chatMessages)
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
     * Bug 3修复: 构建多模态消息列表
     * 如果消息包含图片，content使用数组格式；否则使用简单字符串格式
     */
    private fun buildChatMessages(messages: List<Message>, systemPrompt: String): List<Map<String, Any>> {
        val chatMessages = mutableListOf<Map<String, Any>>()
        
        if (systemPrompt.isNotBlank()) {
            chatMessages.add(mapOf("role" to "system", "content" to systemPrompt))
        }
        
        for (msg in messages) {
            val role = if (msg.isUser) "user" else "assistant"
            
            if (msg.isUser && msg.imageUris.isNotEmpty()) {
                // Bug 3修复: 带图片的消息使用多模态content格式
                val contentParts = mutableListOf<Map<String, Any>>()
                if (msg.content.isNotBlank()) {
                    contentParts.add(mapOf("type" to "text", "text" to msg.content))
                }
                for (imageUri in msg.imageUris) {
                    try {
                        val base64Data = imageUriToBase64(imageUri)
                        if (base64Data != null) {
                            contentParts.add(mapOf(
                                "type" to "image_url",
                                "image_url" to mapOf("url" to "data:image/jpeg;base64,$base64Data")
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to encode image: $imageUri", e)
                    }
                }
                if (contentParts.isNotEmpty()) {
                    chatMessages.add(mapOf("role" to role, "content" to contentParts))
                }
            } else {
                // 纯文本消息使用简单格式
                chatMessages.add(mapOf("role" to role, "content" to msg.content))
            }
        }
        
        return chatMessages
    }
    
    /**
     * Bug 3修复: 将本地图片URI转为base64编码
     */
    private fun imageUriToBase64(uriString: String): String? {
        return try {
            when {
                uriString.startsWith("data:image") -> {
                    // 已经是base64格式
                    uriString.substringAfter("base64,")
                }
                uriString.startsWith("content://") || uriString.startsWith("file://") -> {
                    // 从文件读取并编码 - 需要Context，这里暂不处理
                    // 实际编码在ChatFragment中完成，这里接收已编码的数据
                    null
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert image URI to base64", e)
            null
        }
    }
    
    /**
     * 发送消息（接收已构建好的消息列表）
     */
    suspend fun sendMessageRaw(
        endpoints: List<String>,
        apiKey: String,
        model: String,
        chatMessages: List<Map<String, Any>>
    ): Result<String> = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        
        for (endpoint in endpoints) {
            try {
                val result = trySendMessage(endpoint, apiKey, model, chatMessages)
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
        chatMessages: List<Map<String, Any>>
    ): Result<String> {
        var lastException: Exception? = null
        
        for (attempt in 0 until MAX_RETRIES) {
            try {
                val result = doSendMessage(endpoint, apiKey, model, chatMessages)
                if (result.isSuccess || shouldNotRetry(result.exceptionOrNull())) {
                    return result
                }
                lastException = result.exceptionOrNull() as? Exception
            } catch (e: Exception) {
                lastException = e
                if (!shouldRetryOnException(e) || attempt >= MAX_RETRIES - 1) {
                    break
                }
            }
            
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
        chatMessages: List<Map<String, Any>>
    ): Result<String> {
        return try {
            val requestBody = ApiConfig.ChatRequest(model = model, messages = chatMessages, stream = false)
            val json = gson.toJson(requestBody)
            
            val request = Request.Builder()
                .url("$endpoint/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            val responseBodyString = response.body?.string()
            
            if (!response.isSuccessful) {
                val errorMessage = parseHttpErrorMessage(response.code, responseBodyString)
                return Result.failure(Exception(errorMessage))
            }
            
            if (responseBodyString.isNullOrBlank()) {
                return Result.failure(Exception("服务器返回空响应"))
            }
            
            try {
                val chatResponse = gson.fromJson(responseBodyString, ApiConfig.ChatResponse::class.java)
                
                if (chatResponse.error != null) {
                    val errorDetail = chatResponse.error.message ?: chatResponse.error.type ?: "API Error"
                    val errorCode = chatResponse.error.code
                    val fullError = if (errorCode != null) "$errorDetail (code: $errorCode)" else errorDetail
                    return Result.failure(Exception(fullError))
                }
                
                if (chatResponse.choices.isNullOrEmpty()) {
                    return Result.failure(Exception("响应格式异常: 缺少choices字段"))
                }
                
                val firstChoice = chatResponse.choices.firstOrNull()
                val messageMap = firstChoice?.message
                
                if (messageMap == null) {
                    return Result.failure(Exception("响应格式异常: 缺少message字段"))
                }
                
                val content = messageMap["content"] as? String
                if (content.isNullOrBlank()) {
                    return Result.failure(Exception("AI返回了空响应内容，请重试"))
                }
                
                Result.success(content)
            } catch (e: Exception) {
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
    
    private fun parseHttpErrorMessage(httpCode: Int, responseBody: String?): String {
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
    
    private fun shouldRetryOnException(e: Exception): Boolean {
        return when (e) {
            is SocketException -> true
            is IOException -> true
            is java.net.ConnectException -> true
            is java.net.UnknownHostException -> true
            is InterruptedIOException -> true
            else -> false
        }
    }
    
    private fun shouldNotRetry(e: Throwable?): Boolean {
        if (e == null) return false
        val message = e.message ?: ""
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
