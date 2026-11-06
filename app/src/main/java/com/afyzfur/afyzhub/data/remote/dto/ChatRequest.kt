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
    val stream_options: StreamOptions? = null,
    /**
     * 思考程度。应用内部有五档，但不同 OpenAI 兼容服务支持的取值
     * 不完全一致，provider 层会将高档位安全映射到受支持的值。
     *
     * 与 stream_options 同理，为 null 时会被省略——不支持思考的模型
     * 收到这个字段可能直接报错，所以关闭或不支持时必须省略该字段。
     */
    val reasoning_effort: String? = null
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
