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
    
    // Bug 5修复: 对话列表
    private val _conversationList = MutableStateFlow<List<ChatConversation>>(emptyList())
    val conversationList: StateFlow<List<ChatConversation>> = _conversationList.asStateFlow()
    
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
    
    // Bug 3修复: 保存待发送的图片base64列表
    private var pendingImageBase64List: List<String> = emptyList()
    
    companion object {
        private const val TAG = "ChatViewModel"
        private const val RESPONSE_TIMEOUT_MS = 120_000L
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
        loadConversationList()
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
    
    // Bug 5修复: 加载对话列表
    private fun loadConversationList() {
        _conversationList.value = prefs.loadConversations()
    }
    
    fun refreshConversationList() {
        _conversationList.value = prefs.loadConversations()
    }
    
    fun newConversation() {
        // Bug 5修复: 保存当前对话到列表
        saveCurrentConversationToList()
        
        val conv = ChatConversation()
        _currentConversation.value = conv
        _messages.value = emptyList()
        _newMessageCount.value = 0
        _timeoutMessage.value = null
        lastUserMessage = null
        pendingImageBase64List = emptyList()
        timeoutJob?.cancel()
        prefs.saveCurrentConversation(conv)
        prefs.activeConversationId = conv.id
        
        refreshConversationList()
    }
    
    // Bug 5修复: 切换到指定对话
    fun switchToConversation(conversationId: String) {
        // 先保存当前对话
        saveCurrentConversationToList()
        
        val conversations = prefs.loadConversations()
        val target = conversations.find { it.id == conversationId }
        if (target != null) {
            _currentConversation.value = target
            _messages.value = target.messages.toList()
            prefs.saveCurrentConversation(target)
            prefs.activeConversationId = target.id
            _newMessageCount.value = 0
        }
    }
    
    // Bug 5修复: 删除对话
    fun deleteConversation(conversationId: String) {
        prefs.deleteConversation(conversationId)
        refreshConversationList()
        
        // 如果删除的是当前对话，创建新对话
        if (_currentConversation.value?.id == conversationId) {
            newConversation()
        }
    }
    
    // Bug 5修复: 保存当前对话到列表
    private fun saveCurrentConversationToList() {
        val conv = _currentConversation.value ?: return
        if (conv.messages.isEmpty()) return
        
        // 自动生成标题：取第一条用户消息的前20个字符
        if (conv.title == "New Chat") {
            val firstUserMsg = conv.messages.firstOrNull { it.isUser }
            if (firstUserMsg != null) {
                conv.title = firstUserMsg.content.take(20).replace("\n", " ")
                if (firstUserMsg.content.length > 20) conv.title += "..."
            }
        }
        conv.updatedAt = System.currentTimeMillis()
        prefs.addOrUpdateConversation(conv)
    }
    
    fun clearCurrentConversation() {
        val conv = ChatConversation()
        _currentConversation.value = conv
        _messages.value = emptyList()
        _newMessageCount.value = 0
        _timeoutMessage.value = null
        lastUserMessage = null
        pendingImageBase64List = emptyList()
        timeoutJob?.cancel()
        saveConversation()
    }
    
    fun retryLastMessage() {
        lastUserMessage?.let { message ->
            if (!_isLoading.value) {
                sendMessage(message)
            }
        }
    }
    
    val canRetry: Boolean
        get() = lastUserMessage != null && !_isLoading.value
    
    // Bug 3修复: 设置待发送图片
    fun setPendingImages(base64List: List<String>) {
        pendingImageBase64List = base64List
    }
    
    fun sendMessage(content: String) {
        if (content.isBlank() && pendingImageBase64List.isEmpty()) return
        if (_isLoading.value) return
        
        lastUserMessage = content
        
        // Bug 3修复: 构建带图片的消息
        val imageUris = if (pendingImageBase64List.isNotEmpty()) {
            pendingImageBase64List.map { "data:image/jpeg;base64,$it" }
        } else {
            emptyList()
        }
        pendingImageBase64List = emptyList()
        
        val userMessage = Message(content = content, isUser = true, imageUris = imageUris)
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
        
        startTimeoutTimer()
        
        viewModelScope.launch {
            try {
                // Bug 3修复: 使用支持多模态的消息发送
                val result = apiClient.sendMessage(
                    endpoints = endpoints,
                    apiKey = apiKey,
                    model = currentModel,
                    messages = updatedMessages,
                    systemPrompt = systemPrompt
                )
                
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
                        
                        lastUserMessage = null
                    },
                    onFailure = { e ->
                        _error.value = e.message ?: "未知错误"
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "sendMessage error", e)
                _error.value = e.message ?: "未知错误"
            } finally {
                _isLoading.value = false
                _waitingForReply.value = false
                saveConversation()
            }
        }
    }
    
    private fun startTimeoutTimer() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(RESPONSE_TIMEOUT_MS)
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
                updatedAt = currentTime
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
        // Bug 5修复: 保存当前对话到列表
        saveCurrentConversationToList()
    }
}
