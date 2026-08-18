package com.afyzfur.afyzhub.domain.model

data class Message(
    val id: Long,
    val conversationId: Long,
    val content: String,
    val role: String,
    val createdAt: Long
)