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
    
    data class ChatRequest(
        val model: String,
        val messages: List<Map<String, String>>,
        val stream: Boolean = true
    )
    
    data class ChatResponse(
        val id: String?,
        val choices: List<Choice>?,
        val error: Error?
    )
    
    data class Choice(
        val delta: Map<String, String>?,
        val message: Map<String, String>?,
        val finish_reason: String?
    )
    
    data class Error(
        val message: String?,
        val type: String?,
        val code: String?
    )
}
