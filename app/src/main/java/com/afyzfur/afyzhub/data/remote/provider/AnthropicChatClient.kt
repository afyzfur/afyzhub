package com.afyzfur.afyzhub.data.remote.provider

import com.afyzfur.afyzhub.data.log.RequestLogContext
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
            body = body,
            logContext = RequestLogContext(
                provider = settings.provider.id,
                model = settings.model
            )
        )
        val response = json.decodeFromString(MessagesResponse.serializer(), text)
        return CompletionResult(
            content = response.extractText(),
            usage = response.usage?.let {
                TokenUsage(it.input, it.output)
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

        // Claude 把思考和正文推在不同的 delta 类型里，需要折回内嵌标签
        val wrapper = ThinkingStreamWrapper()

        transport.postForSse(
            baseUrl = settings.baseUrl,
            path = MESSAGES_PATH,
            headers = authHeaders(settings),
            body = body,
            logContext = RequestLogContext(
                provider = settings.provider.id,
                model = settings.model
            )
        ).collect { payload ->
            val event = parseEvent(payload) ?: return@collect
            when (event.type) {
                "content_block_delta" -> {
                    // thinking 模型的 delta 分两种：thinking_delta 把思考放在
                    // thinking 字段，text_delta 把正文放在 text 字段。此前只取
                    // text，思考内容被静默丢弃，开了思考也什么都看不到
                    val delta = event.delta
                    val piece = when {
                        !delta?.thinking.isNullOrEmpty() ->
                            wrapper.onThinking(delta.thinking.orEmpty())
                        !delta?.text.isNullOrEmpty() ->
                            wrapper.onText(delta.text.orEmpty())
                        else -> ""
                    }
                    if (piece.isNotEmpty()) emit(StreamEvent.TextDelta(piece))
                }

                "message_start" ->
                    event.message?.usage?.let { inputTokens = it.input }

                "message_delta" ->
                    event.usage?.let { outputTokens = it.output }

                // 部分中转服务把 Claude 请求转成 OpenAI 协议后转发，
                // 返回的 SSE 因而是 OpenAI 格式（无 type 字段，正文在
                // choices[].delta.content）。不兼容会导致整段回复丢失
                "" -> {
                    // 这类中转把思考放在 OpenAI 的 reasoning_content 字段里，
                    // 同样要折回标签，否则用中转访问 Claude 就看不到思考
                    val d = event.choices?.firstOrNull()?.delta
                    val piece = when {
                        !d?.reasoningContent.isNullOrEmpty() ->
                            wrapper.onThinking(d.reasoningContent.orEmpty())
                        !d?.content.isNullOrEmpty() ->
                            wrapper.onText(d.content.orEmpty())
                        else -> ""
                    }
                    if (piece.isNotEmpty()) emit(StreamEvent.TextDelta(piece))
                    event.usage?.let {
                        inputTokens = it.input
                        outputTokens = it.output
                    }
                }
            }
        }

        // 补齐未闭合的标签：全程都在思考、正文一个字都没来的
        // 情况下（被 token 上限截断）会留下半个标签
        wrapper.finish().takeIf { it.isNotEmpty() }
            ?.let { emit(StreamEvent.TextDelta(it)) }

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
            headers = authHeaders(settings),
            logContext = RequestLogContext(
                provider = settings.provider.id,
                model = settings.model
            )
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

        // Claude 的思考是 thinking 对象而非枚举档位，需把与厂商无关的
        // 档位翻译成 token 预算。OFF 时整个字段不出现——不支持思考的
        // 模型收到这个字段会报错
        val effort = settings.thinkingEffort
        val thinking = effort.tokenBudget?.let {
            ThinkingConfig(type = "enabled", budgetTokens = it)
        }

        return MessagesRequest(
            model = settings.model,
            messages = messages,
            system = systemPrompt,
            maxTokens = effort.anthropicMaxTokens(Constants.DEFAULT_MAX_TOKENS),
            stream = stream,
            thinking = thinking
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
        val stream: Boolean,
        val thinking: ThinkingConfig? = null
    )

    /**
     * Claude 的思考开关。
     *
     * type 只有 "enabled" 一种取值；关闭思考的做法是整个字段不出现，
     * 而不是传 "disabled"。
     */
    @Serializable
    private data class ThinkingConfig(
        val type: String,
        @SerialName("budget_tokens") val budgetTokens: Int
    )

    @Serializable
    private data class MessageItem(val role: String, val content: String)

    /**
     * 非流式响应。
     *
     * 同时兼容两种协议：Anthropic 的正文在 content[].text，
     * 转成 OpenAI 格式的中转服务放在 choices[].message.content。
     */
    @Serializable
    private data class MessagesResponse(
        val content: List<ContentBlock> = emptyList(),
        val usage: AnthropicUsage? = null,
        val choices: List<OpenAiFullChoice>? = null
    ) {
        fun extractText(): String {
            // 思考是 type=thinking 的独立块，不能和正文一起拼接，
            // 否则推理过程会直接混进回答里
            val reasoning = content
                .filter { it.type == "thinking" }
                .joinToString("") { it.thinking.orEmpty() }
            val body = content
                .filter { it.type == "text" }
                .joinToString("") { it.text.orEmpty() }
            if (body.isNotEmpty() || reasoning.isNotEmpty()) {
                return if (reasoning.isNotEmpty()) "<think>$reasoning</think>$body" else body
            }
            return choices?.firstOrNull()?.message?.contentWithThinking.orEmpty()
        }
    }

    @Serializable
    private data class OpenAiFullChoice(val message: OpenAiMessage? = null)

    @Serializable
    private data class OpenAiMessage(
        val content: String = "",
        @SerialName("reasoning_content") val reasoningContent: String? = null
    ) {
        /** 中转把思考单独放在 reasoning_content，折回内嵌标签 */
        val contentWithThinking: String
            get() {
                val r = reasoningContent?.takeIf { it.isNotBlank() } ?: return content
                return "<think>$r</think>$content"
            }
    }

    @Serializable
    private data class ContentBlock(
        val type: String = "",
        val text: String? = null,
        val thinking: String? = null
    )

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
        val usage: AnthropicUsage? = null,

        // 兼容把 Claude 转成 OpenAI 格式的中转服务。这类响应没有 type 字段，
        // 因此 type 取到默认空串，正文在 choices[].delta.content
        val choices: List<OpenAiChoice>? = null
    )

    @Serializable
    private data class OpenAiChoice(val delta: OpenAiDelta? = null)

    @Serializable
    private data class OpenAiDelta(
        val content: String? = null,
        // 转成 OpenAI 协议的中转把思考放在这里
        @SerialName("reasoning_content") val reasoningContent: String? = null
    )

    @Serializable
    private data class EventMessage(val usage: AnthropicUsage? = null)

    /**
     * Token 用量。
     *
     * 同时声明两套字段名：Anthropic 用 input_tokens / output_tokens，
     * 而转成 OpenAI 格式的中转服务用 prompt_tokens / completion_tokens。
     * 取值时哪套非零用哪套，避免为两种协议维护两个 usage 字段
     * （同一个 JSON key 无法映射到两个属性）。
     */
    @Serializable
    private data class AnthropicUsage(
        @SerialName("input_tokens") val inputTokens: Int = 0,
        @SerialName("output_tokens") val outputTokens: Int = 0,
        @SerialName("prompt_tokens") val promptTokens: Int = 0,
        @SerialName("completion_tokens") val completionTokens: Int = 0
    ) {
        val input: Int get() = if (inputTokens > 0) inputTokens else promptTokens
        val output: Int get() = if (outputTokens > 0) outputTokens else completionTokens
    }

    /**
     * content_block_delta 的增量。
     *
     * text_delta 用 text 字段，thinking_delta 用 thinking 字段。
     * 两者互斥出现，按哪个非空取哪个。
     */
    @Serializable
    private data class Delta(
        val text: String? = null,
        val thinking: String? = null
    )

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
