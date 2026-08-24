package com.afyzfur.afyzhub.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<RequestMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int? = null,
    /** 为 true 时服务端以 SSE 分块返回。 */
    val stream: Boolean = false,
    /**
     * 流式请求的附加选项，用于索取 usage。
     *
     * 必须为 null 时省略而非发 null：非流式请求带上这个字段会被
     * 部分中转服务拒绝。序列化配置已开启 explicitNulls = false，
     * 因此 null 不会出现在请求体中。
     */
    val stream_options: StreamOptions? = null
)

/** OpenAI 流式选项。目前只用到索取 usage。 */
@Serializable
data class StreamOptions(
    val include_usage: Boolean = true
)

@Serializable
data class RequestMessage(
    val role: String,
    val content: String
)