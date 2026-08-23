package com.afyzfur.afyzhub.data.remote.provider

import com.afyzfur.afyzhub.data.remote.dto.ChatRequest
import com.afyzfur.afyzhub.data.remote.dto.ChatResponse
import com.afyzfur.afyzhub.data.remote.dto.ChatStreamChunk
import com.afyzfur.afyzhub.data.remote.dto.RequestMessage
import com.afyzfur.afyzhub.data.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
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

    override suspend fun complete(turns: List<ChatTurn>, settings: AppSettings): String {
        val body = json.encodeToString(ChatRequest.serializer(), buildRequest(turns, settings, false))
        val text = transport.postForText(
            baseUrl = settings.baseUrl,
            path = CHAT_PATH,
            headers = authHeaders(settings),
            body = body
        )
        return json.decodeFromString(ChatResponse.serializer(), text)
            .choices.firstOrNull()?.message?.content.orEmpty()
    }

    override fun stream(turns: List<ChatTurn>, settings: AppSettings): Flow<String> {
        val body = json.encodeToString(ChatRequest.serializer(), buildRequest(turns, settings, true))
        return transport.postForSse(
            baseUrl = settings.baseUrl,
            path = CHAT_PATH,
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

    private fun buildRequest(
        turns: List<ChatTurn>,
        settings: AppSettings,
        stream: Boolean
    ) = ChatRequest(
        model = settings.model,
        messages = turns.map { RequestMessage(role = it.role, content = it.content) },
        stream = stream
    )

    private fun authHeaders(settings: AppSettings) = mapOf(
        "Content-Type" to "application/json",
        "Authorization" to "Bearer ${settings.apiKey}"
    )

    /** 单个数据块解析失败不应中断整段回复，返回 null 表示跳过。 */
    private fun parseDelta(payload: String): String? = try {
        json.decodeFromString(ChatStreamChunk.serializer(), payload)
            .choices.firstOrNull()?.delta?.content?.takeIf { it.isNotEmpty() }
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
