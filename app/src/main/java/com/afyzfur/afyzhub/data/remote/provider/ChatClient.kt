package com.afyzfur.afyzhub.data.remote.provider

import com.afyzfur.afyzhub.data.settings.AppSettings
import kotlinx.coroutines.flow.Flow

/** 与提供商无关的一条对话消息。 */
data class ChatTurn(
    val role: String,
    val content: String
)

/**
 * 统一的对话客户端。
 *
 * 每个提供商实现自己的请求构造与响应解析，上层只依赖这个接口，
 * 不需要知道具体是 OpenAI、Claude 还是 Gemini。
 */
interface ChatClient {

    /** 一次性返回完整回复。 */
    suspend fun complete(turns: List<ChatTurn>, settings: AppSettings): String

    /** 流式返回增量文本。 */
    fun stream(turns: List<ChatTurn>, settings: AppSettings): Flow<String>

    /**
     * 拉取该提供商当前可用的模型名列表。
     *
     * 由各家的模型列表接口实时获取，避免应用内硬编码的名单过期。
     * 结果按名称排序，调用方可直接展示。
     */
    suspend fun listModels(settings: AppSettings): List<String>
}
