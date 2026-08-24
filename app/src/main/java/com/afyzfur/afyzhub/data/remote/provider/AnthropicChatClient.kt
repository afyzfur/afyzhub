package com.afyzfur.afyzhub.data.remote.provider

import com.afyzfur.afyzhub.data.settings.AppSettings
import com.afyzfur.afyzhub.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

    override suspend fun complete(turns: List<ChatTurn>, settings: AppSettings): CompletionResult {
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
        val response = json.decodeFromString(MessagesResponse.serializer(), text)
        return CompletionResult(
            content = response.content
                .filter { it.type == "text" }
                .joinToString("") { it.text.orEmpty() },
            usage = response.usage?.let {
                TokenUsage(it.inputTokens, it.outputTokens)
            }
        )
    }

    override fun stream(turns: List<ChatTurn>, settings: AppSettings): Flow<StreamEvent> = flow {
        val body = json.encodeToString(
            MessagesRequest.serializer(),
            buildRequest(turns, settings, stream = true)
        )

        // Claude 把 usage 拆到两个事件里：message_start 给输入 token，
        // message_delta 给输出 token。需要跨事件累积后在结束时合并
        var inputTokens: Int? = null
        var outputTokens: Int? = null

        transport.postForSse(
            baseUrl = settings.baseUrl,
            path = MESSAGES_PATH,
            headers = authHeaders(settings),
            body = body
        ).collect { payload ->
            val event = parseEvent(payload) ?: return@collect
            when (event.type) {
                "content_block_delta" ->
                    event.delta?.text
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { emit(StreamEvent.TextDelta(it)) }

                "message_start" ->
                    event.message?.usage?.let { inputTokens = it.inputTokens }

                "message_delta" ->
                    event.usage?.let { outputTokens = it.outputTokens }
            }
        }

        val usage = if (inputTokens != null || outputTokens != null) {
            TokenUsage(inputTokens ?: 0, outputTokens ?: 0)
        } else {
            null
        }
        emit(StreamEvent.Finished(usage))
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
     * 调用方按 type 分发；单个事件解析失败返回 null 表示跳过。
     */
    private fun parseEvent(payload: String): AnthropicEvent? = try {
        json.decodeFromString(AnthropicEvent.serializer(), payload)
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
    private data class MessagesResponse(
        val content: List<ContentBlock> = emptyList(),
        val usage: AnthropicUsage? = null
    )

    @Serializable
    private data class ContentBlock(val type: String = "", val text: String? = null)

    /**
     * 流式事件的统一形状。
     *
     * 命名为 AnthropicEvent 而非 StreamEvent：后者在本包内已被
     * ChatClient.kt 的对外事件类型占用。
     *
     * usage 出现在两个位置：message_start 事件的 message.usage 里带
     * input_tokens，message_delta 事件的顶层 usage 里带 output_tokens。
     */
    @Serializable
    private data class AnthropicEvent(
        val type: String = "",
        val delta: Delta? = null,
        val message: EventMessage? = null,
        val usage: AnthropicUsage? = null
    )

    @Serializable
    private data class EventMessage(val usage: AnthropicUsage? = null)

    @Serializable
    private data class AnthropicUsage(
        @SerialName("input_tokens") val inputTokens: Int = 0,
        @SerialName("output_tokens") val outputTokens: Int = 0
    )

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
