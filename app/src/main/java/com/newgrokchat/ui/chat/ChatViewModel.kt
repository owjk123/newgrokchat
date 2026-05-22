package com.newgrokchat.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newgrokchat.NewGrokChatApp
import com.newgrokchat.data.api.GrokApiClient
import com.newgrokchat.model.ApiConfig
import com.newgrokchat.model.ChatConversation
import com.newgrokchat.model.Message
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    
    private val prefs by lazy { NewGrokChatApp.instance.prefs }
    private val apiClient by lazy { GrokApiClient() }
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _currentConversation = MutableStateFlow<ChatConversation?>(null)
    val conversation: StateFlow<ChatConversation?> = _currentConversation.asStateFlow()
    
    private val _newMessageCount = MutableStateFlow(0)
    val newMessageCount: StateFlow<Int> = _newMessageCount.asStateFlow()
    
    private val _isViewingHistory = MutableStateFlow(false)
    val isViewingHistory: StateFlow<Boolean> = _isViewingHistory.asStateFlow()
    
    private val _timeoutMessage = MutableStateFlow<String?>(null)
    val timeoutMessage: StateFlow<String?> = _timeoutMessage.asStateFlow()
    
    private val _waitingForReply = MutableStateFlow(false)
    val waitingForReply: StateFlow<Boolean> = _waitingForReply.asStateFlow()
    
    // 保存最后一条用户消息，用于重试
    private var lastUserMessage: String? = null
    
    // 超时检测 Job
    private var timeoutJob: Job? = null
    
    companion object {
        private const val TAG = "ChatViewModel"
        private const val RESPONSE_TIMEOUT_MS = 120_000L // 120秒超时
    }
    
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
        try {
            val conversation = prefs.loadCurrentConversation()
            if (conversation != null) {
                _currentConversation.value = conversation
                _messages.value = conversation.messages.toList()
            } else {
                newConversation()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load conversation", e)
            newConversation()
        }
    }
    
    fun newConversation() {
        val conv = ChatConversation()
        _currentConversation.value = conv
        _messages.value = emptyList()
        _newMessageCount.value = 0
        _timeoutMessage.value = null
        lastUserMessage = null
        timeoutJob?.cancel()
        prefs.saveCurrentConversation(conv)
    }
    
    /**
     * 清空当前对话（不创建新对话，保留 Conversation 对象）
     */
    fun clearCurrentConversation() {
        val conv = ChatConversation()
        _currentConversation.value = conv
        _messages.value = emptyList()
        _newMessageCount.value = 0
        _timeoutMessage.value = null
        lastUserMessage = null
        timeoutJob?.cancel()
        saveConversation()
    }
    
    /**
     * 重发上一条失败的消息
     */
    fun retryLastMessage() {
        lastUserMessage?.let { message ->
            if (!_isLoading.value) {
                sendMessage(message)
            }
        }
    }
    
    /**
     * 检查是否可以重试
     */
    val canRetry: Boolean
        get() = lastUserMessage != null && !_isLoading.value
    
    fun sendMessage(content: String) {
        if (content.isBlank() || _isLoading.value) return
        
        // 保存用户消息用于可能的重试
        lastUserMessage = content
        
        val userMessage = Message(content = content, isUser = true)
        val updatedMessages = _messages.value.toMutableList()
        updatedMessages.add(userMessage)
        _messages.value = updatedMessages
        
        _isLoading.value = true
        _error.value = null
        _timeoutMessage.value = null
        _waitingForReply.value = true
        
        if (!_isViewingHistory.value) {
            _newMessageCount.value = 0
        }
        
        // 启动超时检测
        startTimeoutTimer()
        
        viewModelScope.launch {
            try {
                val result = apiClient.sendMessage(
                    endpoints = endpoints,
                    apiKey = apiKey,
                    model = currentModel,
                    messages = updatedMessages,
                    systemPrompt = systemPrompt
                )
                
                // 取消超时检测
                timeoutJob?.cancel()
                
                result.fold(
                    onSuccess = { response ->
                        val aiMessage = Message(content = response, isUser = false)
                        val finalMessages = _messages.value.toMutableList()
                        finalMessages.add(aiMessage)
                        _messages.value = finalMessages
                        
                        if (_isViewingHistory.value) {
                            _newMessageCount.value = _newMessageCount.value + 1
                        }
                        
                        // 成功响应后清除重试消息
                        lastUserMessage = null
                    },
                    onFailure = { e ->
                        _error.value = e.message ?: "未知错误"
                        // 失败后保留用户消息用于重试
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "sendMessage error", e)
                _error.value = e.message ?: "未知错误"
                // 失败后保留用户消息用于重试
            } finally {
                _isLoading.value = false
                _waitingForReply.value = false
                saveConversation()
            }
        }
    }
    
    /**
     * 启动超时检测定时器
     */
    private fun startTimeoutTimer() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(RESPONSE_TIMEOUT_MS)
            // 只有在仍然等待回复时才显示超时
            if (_waitingForReply.value && _isLoading.value) {
                _isLoading.value = false
                _waitingForReply.value = false
                _timeoutMessage.value = "响应超时，请重试"
                Log.w(TAG, "Response timeout after ${RESPONSE_TIMEOUT_MS / 1000}s")
            }
        }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun clearTimeoutMessage() {
        _timeoutMessage.value = null
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
        try {
            val currentTime = System.currentTimeMillis()
            val conv = _currentConversation.value?.copy(
                messages = _messages.value.toMutableList(),
                updatedAt = currentTime  // 同步更新会话时间戳
            )
            _currentConversation.value = conv
            prefs.saveCurrentConversation(conv)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save conversation", e)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        timeoutJob?.cancel()
    }
}
