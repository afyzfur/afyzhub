package com.afyzfur.afyzhub.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int? = null
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)