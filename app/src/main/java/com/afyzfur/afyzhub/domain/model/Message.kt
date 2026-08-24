package com.afyzfur.afyzhub.domain.model

import com.afyzfur.afyzhub.util.Constants

data class Message(
    val id: Long,
    val conversationId: Long,
    val content: String,
    val role: String,
    val status: String = Constants.STATUS_SUCCESS,
    val errorMessage: String? = null,
    val createdAt: Long,

    // 元信息，v0.2.0 起记录。历史消息为 null，UI 据此跳过渲染
    val model: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val latencyMs: Long? = null
) {
    val isFromUser: Boolean get() = role == Constants.ROLE_USER
    val isFailed: Boolean get() = status == Constants.STATUS_FAILED
    val isSending: Boolean get() = status == Constants.STATUS_SENDING

    /**
     * 生成速度（token/秒）。
     *
     * 仅在同时有输出 token 数与耗时且耗时为正时可算。
     * 返回 null 表示数据不足，调用方不应显示该项。
     */
    val tokensPerSecond: Double?
        get() {
            val tokens = completionTokens ?: return null
            val millis = latencyMs ?: return null
            if (millis <= 0L || tokens <= 0) return null
            return tokens * 1000.0 / millis
        }
}
