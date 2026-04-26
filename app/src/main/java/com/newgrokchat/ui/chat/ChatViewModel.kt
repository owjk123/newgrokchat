package com.newgrokchat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newgrokchat.NewGrokChatApp
import com.newgrokchat.data.api.GrokApiClient
import com.newgrokchat.data.local.ApiConfig
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
        
        viewModelScope.launch {
            val aiMessage = Message(content = "", isUser = false, isStreaming = true)
            val streamingMessages = _messages.value.toMutableList()
            streamingMessages.add(aiMessage)
            _messages.value = streamingMessages
            
            val fullResponse = StringBuilder()
            
            try {
                apiClient.streamMessage(
                    endpoint = currentEndpoint,
                    apiKey = apiKey,
                    model = currentModel,
                    messages = updatedMessages
                ).collect { chunk ->
                    fullResponse.append(chunk)
                    val currentIdx = _messages.value.indexOfLast { it.isStreaming }
                    if (currentIdx >= 0) {
                        val updated = _messages.value.toMutableList()
                        updated[currentIdx] = Message(
                            id = updated[currentIdx].id,
                            content = fullResponse.toString(),
                            isUser = false,
                            isStreaming = false
                        )
                        _messages.value = updated
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
                val currentIdx = _messages.value.indexOfLast { it.isStreaming }
                if (currentIdx >= 0) {
                    val updated = _messages.value.toMutableList()
                    updated[currentIdx] = Message(
                        id = updated[currentIdx].id,
                        content = "Error: ${e.message}",
                        isUser = false,
                        isStreaming = false
                    )
                    _messages.value = updated
                }
            } finally {
                _isLoading.value = false
                saveConversation()
            }
        }
    }
    
    fun clearError() {
        _error.value = null
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
