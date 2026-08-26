package com.afyzfur.afyzhub.data.remote.provider

import com.afyzfur.afyzhub.data.remote.dto.ChatRequest
import com.afyzfur.afyzhub.data.remote.dto.ChatResponse
import com.afyzfur.afyzhub.data.remote.dto.ChatStreamChunk
import com.afyzfur.afyzhub.data.remote.dto.RequestMessage
import com.afyzfur.afyzhub.data.remote.dto.StreamOptions
import com.afyzfur.afyzhub.data.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * OpenAI 及兼容其协议的服务（多数中转服务）。
 *
 * 鉴权用 `Authorization: Bearer`，流式数据块的增量在
 * `choices[].delta.content`。
 */
class OpenAiChatClient(
    private val transport: Transport,
    private val json: Json
) : ChatClient {

    override suspend fun complete(turns: List<ChatTurn>, settings: AppSettings): CompletionResult {
        val body = json.encodeToString(ChatRequest.serializer(), buildRequest(turns, settings, false))
        val text = transport.postForText(
            baseUrl = settings.baseUrl,
            path = CHAT_PATH,
            headers = authHeaders(settings),
            body = body
        )
        val response = json.decodeFromString(ChatResponse.serializer(), text)
        return CompletionResult(
            // 用 contentWithThinking 而非 content：思考走独立字段的模型
            // 需要拼成 think 标签，否则思考过程会丢失
            content = response.choices.firstOrNull()?.message?.contentWithThinking.orEmpty(),
            usage = response.usage?.let {
                TokenUsage(it.prompt_tokens, it.completion_tokens)
            }
        )
    }

    override fun stream(turns: List<ChatTurn>, settings: AppSettings): Flow<StreamEvent> = flow {
        val body = json.encodeToString(ChatRequest.serializer(), buildRequest(turns, settings, true))
        var usage: TokenUsage? = null
        // 思考是否已开始/已结束，用于把独立字段拼成 think 标签
        var thinkingOpen = false

        transport.postForSse(
            baseUrl = settings.baseUrl,
            path = CHAT_PATH,
            headers = authHeaders(settings),
            body = body
        ).collect { payload ->
            val chunk = parseChunk(payload) ?: return@collect
            // 带 usage 的那个 chunk 通常 choices 为空，两者需分别处理
            chunk.usage?.let {
                usage = TokenUsage(it.prompt_tokens, it.completion_tokens)
            }
            val delta = chunk.choices.firstOrNull()?.delta ?: return@collect

            // 思考走独立字段的模型（DeepSeek 系等）：包成 think 标签发出，
            // 这样下游只需要认一种形式，不必区分思考来自哪里
            delta.thinkingDelta?.let { piece ->
                if (!thinkingOpen) {
                    thinkingOpen = true
                    emit(StreamEvent.TextDelta("<think>"))
                }
                emit(StreamEvent.TextDelta(piece))
            }

            delta.content?.takeIf { it.isNotEmpty() }?.let { piece ->
                // 正文开始意味着思考结束，先补上闭合标签
                if (thinkingOpen) {
                    thinkingOpen = false
                    emit(StreamEvent.TextDelta("</think>"))
                }
                emit(StreamEvent.TextDelta(piece))
            }
        }

        // 只有思考没有正文时（异常中断或纯推理响应）也要闭合，
        // 否则留下未闭合标签，界面会一直显示为"思考中"
        if (thinkingOpen) emit(StreamEvent.TextDelta("</think>"))

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

    private fun buildRequest(
        turns: List<ChatTurn>,
        settings: AppSettings,
        stream: Boolean
    ) = ChatRequest(
        model = settings.model,
        messages = turns.map { RequestMessage(role = it.role, content = it.content) },
        stream = stream,
        // 仅流式请求索取 usage。非流式的 usage 本来就在响应体里
        stream_options = if (stream) StreamOptions() else null,
        // OFF 时为 null，序列化会省略该字段
        reasoning_effort = settings.thinkingEffort.openAiEffort
    )

    private fun authHeaders(settings: AppSettings) = mapOf(
        "Content-Type" to "application/json",
        "Authorization" to "Bearer ${settings.apiKey}"
    )

    /** 单个数据块解析失败不应中断整段回复，返回 null 表示跳过。 */
    private fun parseChunk(payload: String): ChatStreamChunk? = try {
        json.decodeFromString(ChatStreamChunk.serializer(), payload)
    } catch (e: Exception) {
        null
    }

    @Serializable
    private data class ModelListResponse(val data: List<ModelEntry> = emptyList())

    @Serializable
    private data class ModelEntry(val id: String = "")

    private companion object {
        const val CHAT_PATH = "v1/chat/completions"
        const val MODELS_PATH = "v1/models"
    }
}
