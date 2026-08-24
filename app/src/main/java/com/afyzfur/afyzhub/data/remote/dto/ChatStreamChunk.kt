package com.afyzfur.afyzhub.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * SSE 流式返回的单个数据块。
 *
 * 对应 `data: {...}` 行的内容，字段与非流式响应不同：
 * 增量文本位于 `choices[].delta.content`，且可能为空。
 */
@Serializable
data class ChatStreamChunk(
    val id: String? = null,
    val choices: List<StreamChoice> = emptyList(),
    /**
     * 仅当请求带 `stream_options.include_usage` 时，OpenAI 会在最后一个
     * chunk（choices 为空）中返回 usage。中转服务可能不支持，故可空。
     */
    val usage: Usage? = null
)

@Serializable
data class StreamChoice(
    val index: Int = 0,
    val delta: StreamDelta = StreamDelta(),
    val finish_reason: String? = null
)

@Serializable
data class StreamDelta(
    val role: String? = null,
    val content: String? = null
)
