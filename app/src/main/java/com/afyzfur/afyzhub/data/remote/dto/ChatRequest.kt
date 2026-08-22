package com.afyzfur.afyzhub.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<RequestMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int? = null,
    /** 为 true 时服务端以 SSE 分块返回。 */
    val stream: Boolean = false
)

@Serializable
data class RequestMessage(
    val role: String,
    val content: String
)