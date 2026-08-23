package com.afyzfur.afyzhub.data.remote.provider

import com.afyzfur.afyzhub.data.settings.AppSettings
import com.afyzfur.afyzhub.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Anthropic Claude。
 *
 * 与 OpenAI 的三处关键差异：
 * 1. 鉴权用 `x-api-key`，并须带 `anthropic-version` 头；
 * 2. system 提示是顶层独立字段，不能混在 messages 里；
 * 3. `max_tokens` 是必填项，缺失会直接返回 400。
 */
class AnthropicChatClient(
    private val transport: Transport,
    private val json: Json
) : ChatClient {

    override suspend fun complete(turns: List<ChatTurn>, settings: AppSettings): String {
        val body = json.encodeToString(
            MessagesRequest.serializer(),
            buildRequest(turns, settings, stream = false)
        )
        val text = transport.postForText(
            baseUrl = settings.baseUrl,
            path = MESSAGES_PATH,
            headers = authHeaders(settings),
            body = body
        )
        return json.decodeFromString(MessagesResponse.serializer(), text)
            .content
            .filter { it.type == "text" }
            .joinToString("") { it.text.orEmpty() }
    }

    override fun stream(turns: List<ChatTurn>, settings: AppSettings): Flow<String> {
        val body = json.encodeToString(
            MessagesRequest.serializer(),
            buildRequest(turns, settings, stream = true)
        )
        return transport.postForSse(
            baseUrl = settings.baseUrl,
            path = MESSAGES_PATH,
            headers = authHeaders(settings),
            body = body
        ).mapNotNull { payload -> parseDelta(payload) }
    }

    override suspend fun listModels(settings: AppSettings): List<String> {
        val text = transport.getForText(
            baseUrl = settings.baseUrl,
            path = MODELS_PATH,
            headers = authHeaders(settings)
        )
        return json.decodeFromString(ModelListResponse.serializer(), text)
            .data
            .map { it.id }
            .filter { it.isNotBlank() }
            .sorted()
    }

    /**
     * 构造请求体。
     *
     * system 角色的消息被提取到顶层 `system` 字段；多条 system 消息合并，
     * 其余消息保持顺序。
     */
    private fun buildRequest(
        turns: List<ChatTurn>,
        settings: AppSettings,
        stream: Boolean
    ): MessagesRequest {
        val systemPrompt = turns
            .filter { it.role == Constants.ROLE_SYSTEM }
            .joinToString("\n") { it.content }
            .takeIf { it.isNotBlank() }

        val messages = turns
            .filter { it.role != Constants.ROLE_SYSTEM }
            .map { MessageItem(role = it.role, content = it.content) }

        return MessagesRequest(
            model = settings.model,
            messages = messages,
            system = systemPrompt,
            maxTokens = Constants.DEFAULT_MAX_TOKENS,
            stream = stream
        )
    }

    private fun authHeaders(settings: AppSettings) = mapOf(
        "Content-Type" to "application/json",
        "x-api-key" to settings.apiKey,
        "anthropic-version" to ANTHROPIC_VERSION
    )

    /**
     * 解析流式事件。
     *
     * Claude 的 SSE 有多种事件类型（message_start、ping、content_block_stop 等），
     * 只有 `content_block_delta` 携带正文增量，其余忽略。
     */
    private fun parseDelta(payload: String): String? = try {
        val event = json.decodeFromString(StreamEvent.serializer(), payload)
        if (event.type == "content_block_delta") {
            event.delta?.text?.takeIf { it.isNotEmpty() }
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }

    @Serializable
    private data class MessagesRequest(
        val model: String,
        val messages: List<MessageItem>,
        val system: String? = null,
        @SerialName("max_tokens") val maxTokens: Int,
        val stream: Boolean
    )

    @Serializable
    private data class MessageItem(val role: String, val content: String)

    @Serializable
    private data class MessagesResponse(val content: List<ContentBlock> = emptyList())

    @Serializable
    private data class ContentBlock(val type: String = "", val text: String? = null)

    @Serializable
    private data class StreamEvent(val type: String = "", val delta: Delta? = null)

    @Serializable
    private data class Delta(val text: String? = null)

    @Serializable
    private data class ModelListResponse(val data: List<ModelEntry> = emptyList())

    @Serializable
    private data class ModelEntry(val id: String = "")

    private companion object {
        const val MESSAGES_PATH = "v1/messages"
        const val MODELS_PATH = "v1/models"
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
