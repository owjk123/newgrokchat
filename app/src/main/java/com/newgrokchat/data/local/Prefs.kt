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
    
    // 系统提示词
    var systemPrompt: String
        get() = prefs.getString(KEY_SYSTEM_PROMPT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SYSTEM_PROMPT, value).apply()
    
    // AI头像 (可以是emoji或图片URI，默认🤖)
    var aiAvatar: String
        get() = prefs.getString(KEY_AI_AVATAR, "🤖") ?: "🤖"
        set(value) = prefs.edit().putString(KEY_AI_AVATAR, value).apply()
    
    // TTS开关
    var ttsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TTS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_TTS_ENABLED, value).apply()
    
    // TTS语速 (0.5 - 2.0)
    var ttsSpeed: Float
        get() = prefs.getFloat(KEY_TTS_SPEED, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_TTS_SPEED, value).apply()
    
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
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_AI_AVATAR = "ai_avatar"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_TTS_SPEED = "tts_speed"
        private const val KEY_CONVERSATIONS = "conversations"
        private const val KEY_CURRENT_CONVERSATION = "current_conversation"
    }
}
