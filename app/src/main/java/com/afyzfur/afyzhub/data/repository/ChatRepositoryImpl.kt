package com.afyzfur.afyzhub.data.repository

import com.afyzfur.afyzhub.data.local.dao.ConversationDao
import com.afyzfur.afyzhub.data.local.dao.MessageDao
import com.afyzfur.afyzhub.data.local.entity.ConversationEntity
import com.afyzfur.afyzhub.data.local.entity.MessageEntity
import com.afyzfur.afyzhub.data.remote.OpenAIApi
import com.afyzfur.afyzhub.data.remote.dto.ChatRequest
import com.afyzfur.afyzhub.domain.model.Conversation
import com.afyzfur.afyzhub.domain.model.Message
import com.afyzfur.afyzhub.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepositoryImpl(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val openAIApi: OpenAIApi
) : ChatRepository {

    override fun getAllConversations(): Flow<List<Conversation>> {
        return conversationDao.getAllConversations().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getMessagesByConversationId(conversationId: Long): Flow<List<Message>> {
        return messageDao.getMessagesByConversationId(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun createConversation(title: String): Long {
        return conversationDao.insertConversation(
            ConversationEntity(title = title)
        )
    }

    override suspend fun sendMessage(
        conversationId: Long,
        content: String,
        apiKey: String
    ): Result<Message> {
        return try {
            // Save user message
            val userMessage = MessageEntity(
                conversationId = conversationId,
                content = content,
                role = "user"
            )
            messageDao.insertMessage(userMessage)

            // Get conversation history
            val messages = messageDao.getMessagesByConversationId(conversationId)
            val chatMessages = mutableListOf<com.afyzfur.afyzhub.data.remote.dto.ChatMessage>()
            
            // Build request with history (simplified)
            chatMessages.add(
                com.afyzfur.afyzhub.data.remote.dto.ChatMessage(
                    role = "user",
                    content = content
                )
            )

            val request = ChatRequest(
                model = Constants.DEFAULT_MODEL,
                messages = chatMessages
            )

            // Call API
            val response = openAIApi.createChatCompletion(request)
            val assistantContent = response.choices.firstOrNull()?.message?.content 
                ?: throw Exception("Empty response")

            // Save assistant message
            val assistantMessage = MessageEntity(
                conversationId = conversationId,
                content = assistantContent,
                role = "assistant"
            )
            val messageId = messageDao.insertMessage(assistantMessage)

            // Update conversation timestamp
            conversationDao.getConversationById(conversationId)?.let {
                conversationDao.updateConversation(
                    it.copy(updatedAt = System.currentTimeMillis())
                )
            }

            Result.success(
                Message(
                    id = messageId,
                    conversationId = conversationId,
                    content = assistantContent,
                    role = "assistant",
                    createdAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteConversation(conversationId: Long) {
        conversationDao.getConversationById(conversationId)?.let {
            conversationDao.deleteConversation(it)
        }
    }

    private fun ConversationEntity.toDomain() = Conversation(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun MessageEntity.toDomain() = Message(
        id = id,
        conversationId = conversationId,
        content = content,
        role = role,
        createdAt = createdAt
    )
}