package com.afyzfur.afyzhub.data.remote.provider

import com.afyzfur.afyzhub.data.settings.AppSettings
import com.afyzfur.afyzhub.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Google Gemini。
 *
 * 与 OpenAI 的关键差异：
 * 1. 模型名写在 URL 路径里（`models/{model}:generateContent`），不在请求体；
 * 2. 消息结构是 `contents[].parts[].text`，助手角色叫 `model` 而非 `assistant`；
 * 3. system 提示走独立的 `systemInstruction` 字段；
 * 4. 鉴权用 `x-goog-api-key` 头。官方也支持 `?key=` 查询参数，
 *    但那会把密钥暴露在 URL 与日志中，因此这里只用请求头。
 * 5. 流式需显式加 `alt=sse`，否则返回的是 JSON 数组而非 SSE。
 */
class GeminiChatClient(
    private val transport: Transport,
    private val json: Json
) : ChatClient {

    override suspend fun complete(turns: List<ChatTurn>, settings: AppSettings): CompletionResult {
        val body = json.encodeToString(GenerateRequest.serializer(), buildRequest(turns))
        val text = transport.postForText(
            baseUrl = settings.baseUrl,
            path = "$MODELS_PREFIX/${settings.model}:generateContent",
            headers = authHeaders(settings),
            body = body
        )
        val response = json.decodeFromString(GenerateResponse.serializer(), text)
        return CompletionResult(
            content = response.extractText(),
            usage = response.usageMetadata?.toTokenUsage()
        )
    }

    override fun stream(turns: List<ChatTurn>, settings: AppSettings): Flow<StreamEvent> = flow {
        val body = json.encodeToString(GenerateRequest.serializer(), buildRequest(turns))
        var usage: TokenUsage? = null

        transport.postForSse(
            baseUrl = settings.baseUrl,
            path = "$MODELS_PREFIX/${settings.model}:streamGenerateContent",
            headers = authHeaders(settings),
            body = body,
            query = mapOf("alt" to "sse")
        ).collect { payload ->
            val response = parseResponse(payload) ?: return@collect
            // Gemini 每个 chunk 都可能带 usageMetadata，且为累计值，
            // 因此直接覆盖，最后一个即为最终结果
            response.usageMetadata?.toTokenUsage()?.let { usage = it }
            response.extractText()
                .takeIf { it.isNotEmpty() }
                ?.let { emit(StreamEvent.TextDelta(it)) }
        }

        emit(StreamEvent.Finished(usage))
    }

    override suspend fun listModels(settings: AppSettings): List<String> {
        val text = transport.getForText(
            baseUrl = settings.baseUrl,
            path = MODELS_PREFIX,
            headers = authHeaders(settings)
        )
        return json.decodeFromString(ModelListResponse.serializer(), text)
            .models
            // 只保留支持文本生成的模型，嵌入类模型无法用于对话。
            .filter { it.supportedGenerationMethods.any { m -> m in CHAT_METHODS } }
            // 接口返回的是 "models/gemini-2.0-flash"，去掉前缀才能拼回请求路径。
            .map { it.name.removePrefix("models/") }
            .filter { it.isNotBlank() }
            .sorted()
    }

    private fun buildRequest(turns: List<ChatTurn>): GenerateRequest {
        val systemPrompt = turns
            .filter { it.role == Constants.ROLE_SYSTEM }
            .joinToString("\n") { it.content }
            .takeIf { it.isNotBlank() }

        val contents = turns
            .filter { it.role != Constants.ROLE_SYSTEM }
            .map { turn ->
                Content(
                    role = if (turn.role == Constants.ROLE_ASSISTANT) "model" else "user",
                    parts = listOf(Part(turn.content))
                )
            }

        return GenerateRequest(
            contents = contents,
            systemInstruction = systemPrompt?.let { Content(role = null, parts = listOf(Part(it))) }
        )
    }

    private fun authHeaders(settings: AppSettings) = mapOf(
        "Content-Type" to "application/json",
        "x-goog-api-key" to settings.apiKey
    )

    /** 单个 chunk 解析失败不应中断整段回复，返回 null 表示跳过。 */
    private fun parseResponse(payload: String): GenerateResponse? = try {
        json.decodeFromString(GenerateResponse.serializer(), payload)
    } catch (e: Exception) {
        null
    }

    @Serializable
    private data class GenerateRequest(
        val contents: List<Content>,
        val systemInstruction: Content? = null
    )

    @Serializable
    private data class Content(val role: String? = null, val parts: List<Part> = emptyList())

    @Serializable
    private data class Part(val text: String = "")

    @Serializable
    private data class GenerateResponse(
        val candidates: List<Candidate> = emptyList(),
        val usageMetadata: UsageMetadata? = null
    ) {
        fun extractText(): String = candidates
            .firstOrNull()
            ?.content
            ?.parts
            ?.joinToString("") { it.text }
            .orEmpty()
    }

    @Serializable
    private data class Candidate(val content: Content? = null)

    /**
     * Gemini 的 token 统计。字段名与 OpenAI / Claude 都不同，
     * 且 candidatesTokenCount 在流式早期 chunk 中可能缺失。
     */
    @Serializable
    private data class UsageMetadata(
        val promptTokenCount: Int = 0,
        val candidatesTokenCount: Int = 0
    ) {
        fun toTokenUsage() = TokenUsage(
            promptTokens = promptTokenCount,
            completionTokens = candidatesTokenCount
        )
    }

    @Serializable
    private data class ModelListResponse(val models: List<ModelEntry> = emptyList())

    @Serializable
    private data class ModelEntry(
        val name: String = "",
        @SerialName("supportedGenerationMethods")
        val supportedGenerationMethods: List<String> = emptyList()
    )

    private companion object {
        const val MODELS_PREFIX = "v1beta/models"
        val CHAT_METHODS = setOf("generateContent", "streamGenerateContent")
    }
}
