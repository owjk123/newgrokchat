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
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    suspend fun sendMessage(
        endpoint: String,
        apiKey: String,
        model: String,
        messages: List<Message>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val chatMessages = messages.map { mapOf("role" to if (it.isUser) "user" else "assistant", "content" to it.content) }
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
                return@withContext Result.failure(Exception("HTTP ${response.code}: $errorBody"))
            }
            
            val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            
            try {
                val chatResponse = gson.fromJson(responseBody, ApiConfig.ChatResponse::class.java)
                val content = chatResponse.choices?.firstOrNull()?.message?.get("content")
                    ?: chatResponse.error?.message
                    ?: "No response content"
                Result.success(content)
            } catch (e: Exception) {
                Result.failure(Exception("Parse error: ${e.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
