package com.afyzfur.afyzhub.data.repository

import com.afyzfur.afyzhub.domain.model.Conversation
import com.afyzfur.afyzhub.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getAllConversations(): Flow<List<Conversation>>
    fun getMessagesByConversationId(conversationId: Long): Flow<List<Message>>
    suspend fun createConversation(title: String): Long
    suspend fun sendMessage(conversationId: Long, content: String, apiKey: String): Result<Message>
    suspend fun deleteConversation(conversationId: Long)
}