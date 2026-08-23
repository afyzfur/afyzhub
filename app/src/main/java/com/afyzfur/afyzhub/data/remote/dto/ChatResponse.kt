package com.afyzfur.afyzhub.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * OpenAI 兼容的对话响应。
 *
 * 除必需的 choices 外，其余字段都给默认值：部分中转服务会省略
 * `id`、`index` 或 `usage`，缺字段时应当仍能取到正文，
 * 而不是抛出反序列化异常导致回复整条丢失。
 */
@Serializable
data class ChatResponse(
    val id: String = "",
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: ResponseMessage? = null,
    val finish_reason: String? = null
)

@Serializable
data class ResponseMessage(
    val role: String = "",
    val content: String = ""
)

@Serializable
data class Usage(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val total_tokens: Int = 0
)
