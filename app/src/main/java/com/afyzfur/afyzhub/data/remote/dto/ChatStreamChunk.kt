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
    val content: String? = null,
    /**
     * 思考过程的增量。
     *
     * DeepSeek 系与部分中转把推理放在这个独立字段里，而不是像
     * 有些模型那样内嵌 `<think>` 标签到 content。不读它的话
     * 这些模型的思考过程在应用里完全看不到。
     *
     * `reasoning` 是另一种常见拼法（OpenRouter 等），一并接受。
     */
    val reasoning_content: String? = null,
    val reasoning: String? = null
) {
    /** 两种字段名取先有值的那个 */
    val thinkingDelta: String?
        get() = reasoning_content?.takeIf { it.isNotEmpty() }
            ?: reasoning?.takeIf { it.isNotEmpty() }
}
