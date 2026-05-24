package com.newgrokchat.model

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
    
    // Bug 3修复: messages类型改为 List<Map<String, Any>> 支持多模态content
    data class ChatRequest(
        val model: String,
        val messages: List<Map<String, Any>>,
        val stream: Boolean = false
    )
    
    data class ChatResponse(
        val id: String?,
        val choices: List<Choice>?,
        val error: Error?
    )
    
    data class Choice(
        val delta: Map<String, Any?>?,
        val message: Map<String, Any?>?,
        val finish_reason: String?
    )
    
    data class Error(
        val message: String?,
        val type: String?,
        val code: String?
    )
}
