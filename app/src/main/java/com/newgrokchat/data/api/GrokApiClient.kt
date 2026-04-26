package com.newgrokchat.data.api

import com.google.gson.Gson
import com.newgrokchat.model.ApiConfig
import com.newgrokchat.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GrokApiClient {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
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
    
    private fun trySendMessage(
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
            
            val requestBody = ApiConfig.ChatRequest(model = model, messages = chatMessages, stream = false)
            val json = gson.toJson(requestBody)
            
            val request = Request.Builder()
                .url("$endpoint/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return Result.failure(Exception("HTTP ${response.code}: $errorBody"))
            }
            
            val responseBody = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            
            try {
                val chatResponse = gson.fromJson(responseBody, ApiConfig.ChatResponse::class.java)
                val content = chatResponse.choices?.firstOrNull()?.message?.get("content")
                    ?: chatResponse.error?.message
                    ?: "No response content"
                
                if (chatResponse.error != null) {
                    Result.failure(Exception(chatResponse.error.message ?: "API Error"))
                } else {
                    Result.success(content)
                }
            } catch (e: Exception) {
                Result.failure(Exception("Parse error: ${e.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
