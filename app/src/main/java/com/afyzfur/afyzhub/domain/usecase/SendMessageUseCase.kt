package com.afyzfur.afyzhub.domain.usecase

import com.afyzfur.afyzhub.data.repository.ChatRepository
import com.afyzfur.afyzhub.domain.model.Message

class SendMessageUseCase(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(
        conversationId: Long,
        content: String,
        apiKey: String
    ): Result<Message> {
        return repository.sendMessage(conversationId, content, apiKey)
    }
}