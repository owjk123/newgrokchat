package com.newgrokchat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newgrokchat.NewGrokChatApp
import com.newgrokchat.data.api.GrokApiClient
import com.newgrokchat.model.ApiConfig
import com.newgrokchat.model.ChatConversation
import com.newgrokchat.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    
    private val prefs = NewGrokChatApp.instance.prefs
    private val apiClient = GrokApiClient()
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _currentConversation = MutableStateFlow<ChatConversation?>(null)
    val conversation: StateFlow<ChatConversation?> = _currentConversation.asStateFlow()
    
    // 新消息数量（用户查看历史时计数）
    private val _newMessageCount = MutableStateFlow(0)
    val newMessageCount: StateFlow<Int> = _newMessageCount.asStateFlow()
    
    // 是否在查看历史
    private val _isViewingHistory = MutableStateFlow(false)
    val isViewingHistory: StateFlow<Boolean> = _isViewingHistory.asStateFlow()
    
    val endpoints = ApiConfig.ENDPOINTS
    val models = ApiConfig.MODELS
    
    var currentEndpoint: String
        get() = prefs.selectedEndpoint
        set(value) { prefs.selectedEndpoint = value }
    
    var currentModel: String
        get() = prefs.selectedModel
        set(value) { prefs.selectedModel = value }
    
    var apiKey: String
        get() = prefs.apiKey
        set(value) { prefs.apiKey = value }
    
    var systemPrompt: String
        get() = prefs.systemPrompt
        set(value) { prefs.systemPrompt = value }
    
    var aiAvatar: String
        get() = prefs.aiAvatar
        set(value) { prefs.aiAvatar = value }
    
    init {
        loadConversation()
    }
    
    private fun loadConversation() {
        val conversation = prefs.loadCurrentConversation()
        if (conversation != null) {
            _currentConversation.value = conversation
            _messages.value = conversation.messages.toList()
        } else {
            newConversation()
        }
    }
    
    fun newConversation() {
        val conv = ChatConversation()
        _currentConversation.value = conv
        _messages.value = emptyList()
        _newMessageCount.value = 0
        prefs.saveCurrentConversation(conv)
    }
    
    fun sendMessage(content: String) {
        if (content.isBlank() || _isLoading.value) return
        
        val userMessage = Message(content = content, isUser = true)
        val updatedMessages = _messages.value.toMutableList()
        updatedMessages.add(userMessage)
        _messages.value = updatedMessages
        
        _isLoading.value = true
        _error.value = null
        
        if (!_isViewingHistory.value) {
            _newMessageCount.value = 0
        }
        
        viewModelScope.launch {
            try {
                val result = apiClient.sendMessage(
                    endpoints = endpoints,
                    apiKey = apiKey,
                    model = currentModel,
                    messages = updatedMessages,
                    systemPrompt = systemPrompt
                )
                
                result.fold(
                    onSuccess = { response ->
                        val aiMessage = Message(content = response, isUser = false)
                        val finalMessages = _messages.value.toMutableList()
                        finalMessages.add(aiMessage)
                        _messages.value = finalMessages
                        
                        if (_isViewingHistory.value) {
                            _newMessageCount.value = _newMessageCount.value + 1
                        }
                    },
                    onFailure = { e ->
                        _error.value = e.message ?: "未知错误"
                    }
                )
            } catch (e: Exception) {
                _error.value = e.message ?: "未知错误"
            } finally {
                _isLoading.value = false
                saveConversation()
            }
        }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun setViewingHistory(viewing: Boolean) {
        _isViewingHistory.value = viewing
        if (!viewing) {
            _newMessageCount.value = 0
        }
    }
    
    fun clearNewMessageCount() {
        _newMessageCount.value = 0
    }
    
    private fun saveConversation() {
        val conv = _currentConversation.value?.copy(
            messages = _messages.value.toMutableList(),
            updatedAt = System.currentTimeMillis()
        )
        _currentConversation.value = conv
        prefs.saveCurrentConversation(conv)
    }
}
