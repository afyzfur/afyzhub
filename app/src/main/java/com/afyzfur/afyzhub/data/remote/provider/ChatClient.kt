package com.afyzfur.afyzhub.data.remote.provider

import com.afyzfur.afyzhub.data.settings.AppSettings
import kotlinx.coroutines.flow.Flow

/** 与提供商无关的一条对话消息。 */
data class ChatTurn(
    val role: String,
    val content: String
)

/** Token 用量，来自各提供商的 usage 字段。 */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int
)

/**
 * 非流式请求的完整结果。
 *
 * [usage] 可空：部分提供商或错误情况下可能缺失。
 */
data class CompletionResult(
    val content: String,
    val usage: TokenUsage? = null
)

/**
 * 流式请求的增量事件。
 *
 * 正常流程：先 emit 若干 [TextDelta]，最后 emit 一个 [Finished]。
 * [Finished.usage] 可空，因为某些提供商的流式响应不带 usage。
 */
sealed interface StreamEvent {
    /** 增量文本 */
    data class TextDelta(val delta: String) : StreamEvent
    /** 流结束，可能携带 usage */
    data class Finished(val usage: TokenUsage? = null) : StreamEvent
}

/**
 * 统一的对话客户端。
 *
 * 每个提供商实现自己的请求构造与响应解析，上层只依赖这个接口，
 * 不需要知道具体是 OpenAI、Claude 还是 Gemini。
 */
interface ChatClient {
    /** 一次性返回完整回复及 usage。 */
    suspend fun complete(turns: List<ChatTurn>, settings: AppSettings): CompletionResult
    /** 流式返回增量文本及最终 usage。调用方需处理 [StreamEvent] 的两种子类型。 */
    fun stream(turns: List<ChatTurn>, settings: AppSettings): Flow<StreamEvent>

    /**
     * 拉取该提供商当前可用的模型名列表。
     *
     * 由各家的模型列表接口实时获取，避免应用内硬编码的名单过期。
     * 结果按名称排序，调用方可直接展示。
     */
    suspend fun listModels(settings: AppSettings): List<String>
}
