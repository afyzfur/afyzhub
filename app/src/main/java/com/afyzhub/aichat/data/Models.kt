package com.afyzhub.aichat.data

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String
)

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新对话",
    val messages: List<ChatMessage> = emptyList()
)

data class ApiConfig(
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o-mini",
    val systemPrompt: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048
)

data class AppState(
    val conversations: List<Conversation> = emptyList(),
    val selectedConversationId: String? = null,
    val config: ApiConfig = ApiConfig(),
    val isGenerating: Boolean = false,
    val notice: String? = null,
    val hasApiKey: Boolean = false
)