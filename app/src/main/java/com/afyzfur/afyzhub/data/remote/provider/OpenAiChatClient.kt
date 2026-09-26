package com.afyzfur.afyzhub.data.remote.provider

import com.afyzfur.afyzhub.data.remote.dto.ChatRequest
import com.afyzfur.afyzhub.data.remote.dto.ChatResponse
import com.afyzfur.afyzhub.data.remote.dto.cachedTokens
import com.afyzfur.afyzhub.data.remote.dto.ChatStreamChunk
import com.afyzfur.afyzhub.data.remote.dto.RequestMessage
import com.afyzfur.afyzhub.data.remote.dto.StreamOptions
import com.afyzfur.afyzhub.data.log.RequestLogContext
import com.afyzfur.afyzhub.data.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull

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
            body = body,
            logContext = RequestLogContext(
                provider = settings.profileLabel,
                model = settings.model
            )
        )
        val response = json.decodeFromString(ChatResponse.serializer(), text)
        val dtoUsage = response.usage?.let {
            TokenUsage(it.prompt_tokens, it.completion_tokens, it.cachedTokens)
        }
        // 与流式同源的双保险: DTO 缺字段时从原始响应体提取
        val finalUsage = dtoUsage ?: extractUsageManually(text)
        if (finalUsage != null) {
            println("[AfyzUsage] non-stream usage=" + finalUsage)
        }
        return CompletionResult(
            // 用 contentWithThinking 而非 content：思考走独立字段的模型
            // 需要拼成 think 标签，否则思考过程会丢失
            content = response.choices.firstOrNull()?.message?.contentWithThinking.orEmpty(),
            usage = finalUsage
        )
    }

    override fun stream(turns: List<ChatTurn>, settings: AppSettings): Flow<StreamEvent> = flow {
        val body = json.encodeToString(ChatRequest.serializer(), buildRequest(turns, settings, true))
        var usage: TokenUsage? = null
        var rawUsageLogged = false
        // 思考是否已开始/已结束，用于把独立字段拼成 think 标签
        var thinkingOpen = false

        transport.postForSse(
            baseUrl = settings.baseUrl,
            path = CHAT_PATH,
            headers = authHeaders(settings),
            body = body,
            logContext = RequestLogContext(
                provider = settings.profileLabel,
                model = settings.model
            )
        ).collect { payload ->
            // DTO 解析失败/字段缺失时, 手动从原始 JSON 提取 usage 兜底。
            // 双路径: DTO 能解就用 DTO, 解不出再从 JsonElement 里按多套
            // 字段名(DepthSeek/OpenAI/Anthropic)找, 提高对中转服务的兼容性
            val chunk = parseChunk(payload)
            val dtoUsage = chunk?.usage?.let {
                TokenUsage(it.prompt_tokens, it.completion_tokens, it.cachedTokens)
            }
            val extracted = dtoUsage ?: extractUsageManually(payload)
            if (extracted != null) {
                if (!rawUsageLogged) {
                    // 打印首个 usage 原文, 之后缓存为 0 时可据此定位真实字段名
                    println("[AfyzUsage] raw chunk usage json=" + payload.take(600))
                    rawUsageLogged = true
                }
                usage = mergeUsage(usage, extracted)
            }
            if (chunk == null) return@collect
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

    private fun mergeUsage(old: TokenUsage?, next: TokenUsage): TokenUsage = TokenUsage(
        promptTokens = maxOf(old?.promptTokens ?: 0, next.promptTokens),
        completionTokens = maxOf(old?.completionTokens ?: 0, next.completionTokens),
        cachedTokens = listOfNotNull(old?.cachedTokens, next.cachedTokens).maxOrNull()
    )

    override suspend fun listModels(settings: AppSettings): List<String> {
        val text = transport.getForText(
            baseUrl = settings.baseUrl,
            path = MODELS_PATH,
            headers = authHeaders(settings),
            logContext = RequestLogContext(
                provider = settings.profileLabel,
                model = settings.model
            )
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

    /**
     * 手动从 chunk 原始 JSON 里提取 usage。
     *
     * DTO 之外的第二道防线: 不同服务商把缓存命中放在不同字段
     * (DeepSeek 顶层 / OpenAI 嵌套 / Anthropic 风格), DTO 漏掉哪个
     * 字段名时这里仍能取到。
     */
    private fun extractUsageManually(payload: String): TokenUsage? = try {
        val obj = json.parseToJsonElement(payload).jsonObject
        val u = obj["usage"]?.jsonObject ?: return null
        // 数值容错: 部分中转把数字序列化成字符串("123"), intOrNull 对
        // 字符串原语返回 null, 补一路 content 转换
        fun num(e: kotlinx.serialization.json.JsonElement?): Int? =
            e?.jsonPrimitive?.intOrNull ?: e?.jsonPrimitive?.contentOrNull?.trim()?.toIntOrNull()
        val prompt = num(u["prompt_tokens"]) ?: num(u["input_tokens"])
        val completion = num(u["completion_tokens"]) ?: num(u["output_tokens"])
        // 嵌套字段两套命名: OpenAI 的 prompt_tokens_details 与
        // OpenRouter 的 details
        val details = u["prompt_tokens_details"]?.jsonObject ?: u["details"]?.jsonObject
        val hit = num(u["prompt_cache_hit_tokens"])
        val miss = num(u["prompt_cache_miss_tokens"])
        val cached = hit
            ?: num(details?.get("cached_tokens"))
            ?: num(u["cache_read_input_tokens"])
            // 只回报 miss 字段的商用 prompt-miss 反推命中数
            ?: miss?.let { m -> prompt?.let { p -> (p - m).takeIf { v -> v > 0 } } }
        if (prompt == null && completion == null) null
        else TokenUsage(prompt ?: 0, completion ?: 0, cached)
    } catch (e: Exception) {
        null
    }

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
