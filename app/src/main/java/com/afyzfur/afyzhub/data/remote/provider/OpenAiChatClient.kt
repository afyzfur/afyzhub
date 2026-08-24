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
            content = response.choices.firstOrNull()?.message?.content.orEmpty(),
            usage = response.usage?.let {
                TokenUsage(it.prompt_tokens, it.completion_tokens)
            }
        )
    }

    override fun stream(turns: List<ChatTurn>, settings: AppSettings): Flow<StreamEvent> = flow {
        val body = json.encodeToString(ChatRequest.serializer(), buildRequest(turns, settings, true))
        var usage: TokenUsage? = null

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
            chunk.choices.firstOrNull()?.delta?.content
                ?.takeIf { it.isNotEmpty() }
                ?.let { emit(StreamEvent.TextDelta(it)) }
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

    private fun buildRequest(
        turns: List<ChatTurn>,
        settings: AppSettings,
        stream: Boolean
    ) = ChatRequest(
        model = settings.model,
        messages = turns.map { RequestMessage(role = it.role, content = it.content) },
        stream = stream,
        // 仅流式请求索取 usage。非流式的 usage 本来就在响应体里
        stream_options = if (stream) StreamOptions() else null
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
