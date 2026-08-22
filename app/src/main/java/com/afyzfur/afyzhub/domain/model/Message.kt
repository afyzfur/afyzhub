package com.afyzfur.afyzhub.domain.model

import com.afyzfur.afyzhub.util.Constants

data class Message(
    val id: Long,
    val conversationId: Long,
    val content: String,
    val role: String,
    val status: String = Constants.STATUS_SUCCESS,
    val errorMessage: String? = null,
    val createdAt: Long
) {
    val isFromUser: Boolean get() = role == Constants.ROLE_USER
    val isFailed: Boolean get() = status == Constants.STATUS_FAILED
    val isSending: Boolean get() = status == Constants.STATUS_SENDING
}
