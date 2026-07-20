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

/** 上下文长度模式。 */
enum class ContextMode {
    /** 自定义：按用户指定的 token 长度上限裁剪历史。 */
    LIMITED,

    /** MAX 模式：尽可能多地携带历史，由模型上下文窗口自动决定上限。 */
    MAX
}

data class ApiConfig(
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o-mini",
    val systemPrompt: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val useStream: Boolean = true,
    /** 自定义主题种子色（ARGB 十六进制，如 0xFFF57C00），null 表示跟随系统动态取色。 */
    val themeSeedColor: Long? = null,
    /** 上下文模式。 */
    val contextMode: ContextMode = ContextMode.LIMITED,
    /** LIMITED 模式下携带历史的 token 长度上限。 */
    val contextLimit: Int = 32_768
)

/** 从 /models 接口获取的模型条目。 */
data class ModelInfo(
    val id: String,
    /** 该模型的上下文窗口 token 上限，未知时为 null。 */
    val contextWindow: Int? = null
)

data class AppState(
    val conversations: List<Conversation> = emptyList(),
    val selectedConversationId: String? = null,
    val config: ApiConfig = ApiConfig(),
    val isGenerating: Boolean = false,
    val notice: String? = null,
    val hasApiKey: Boolean = false,
    val lastFailedInput: String? = null,
    /** 从 /models 拉取到的可选模型列表。 */
    val availableModels: List<ModelInfo> = emptyList(),
    /** 是否正在拉取模型列表。 */
    val isLoadingModels: Boolean = false
)