package com.newgrokchat.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.newgrokchat.model.ChatConversation

class Prefs(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()
    
    var selectedEndpoint: String
        get() = prefs.getString(KEY_ENDPOINT, ApiConfig.ENDPOINTS.first()) ?: ApiConfig.ENDPOINTS.first()
        set(value) = prefs.edit().putString(KEY_ENDPOINT, value).apply()
    
    var selectedModel: String
        get() = prefs.getString(KEY_MODEL, ApiConfig.MODELS.first()) ?: ApiConfig.MODELS.first()
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()
    
    fun saveConversations(conversations: List<ChatConversation>) {
        val json = gson.toJson(conversations)
        prefs.edit().putString(KEY_CONVERSATIONS, json).apply()
    }
    
    fun loadConversations(): List<ChatConversation> {
        val json = prefs.getString(KEY_CONVERSATIONS, null) ?: return emptyList()
        val type = object : TypeToken<List<ChatConversation>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun saveCurrentConversation(conversation: ChatConversation?) {
        if (conversation == null) {
            prefs.edit().remove(KEY_CURRENT_CONVERSATION).apply()
        } else {
            val json = gson.toJson(conversation)
            prefs.edit().putString(KEY_CURRENT_CONVERSATION, json).apply()
        }
    }
    
    fun loadCurrentConversation(): ChatConversation? {
        val json = prefs.getString(KEY_CURRENT_CONVERSATION, null) ?: return null
        return try {
            gson.fromJson(json, ChatConversation::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    companion object {
        private const val PREFS_NAME = "newgrokchat_prefs"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_MODEL = "model"
        private const val KEY_CONVERSATIONS = "conversations"
        private const val KEY_CURRENT_CONVERSATION = "current_conversation"
    }
}

object ApiConfig {
    val ENDPOINTS = listOf(
        "http://vip.apiyi.com:16888",
        "http://api-cf.apiyi.com:16888",
        "http://api.apiyi.com:16888",
        "http://b.apiyi.com:16888"
    )
    
    val MODELS = listOf(
        "grok-4.20-beta",
        "grok-3",
        "grok-2-latest"
    )
}
