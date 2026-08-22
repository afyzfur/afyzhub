package com.afyzfur.afyzhub.data.repository

import com.afyzfur.afyzhub.data.local.dao.ConversationDao
import com.afyzfur.afyzhub.data.local.dao.MessageDao
import com.afyzfur.afyzhub.data.local.entity.ConversationEntity
import com.afyzfur.afyzhub.data.local.entity.MessageEntity
import com.afyzfur.afyzhub.data.remote.OpenAIApi
import com.afyzfur.afyzhub.data.remote.dto.ChatRequest
import com.afyzfur.afyzhub.data.remote.dto.RequestMessage
import com.afyzfur.afyzhub.data.settings.SettingsProvider
import com.afyzfur.afyzhub.domain.model.Conversation
import com.afyzfur.afyzhub.domain.model.Message
import com.afyzfur.afyzhub.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepositoryImpl(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val openAIApi: OpenAIApi,
    private val settingsProvider: SettingsProvider
) : ChatRepository {

    override fun getAllConversations(): Flow<List<Conversation>> =
        conversationDao.getAllConversations().map { list -> list.map { it.toDomain() } }

    override fun getMessagesByConversationId(conversationId: Long): Flow<List<Message>> =
        messageDao.getMessagesByConversationId(conversationId).map { list -> list.map { it.toDomain() } }

    override suspend fun createConversation(title: String): Long =
        conversationDao.insertConversation(ConversationEntity(title = title))

    override suspend fun sendMessage(conversationId: Long, content: String): Result<Message> {
        val userMessageId = messageDao.insertMessage(
            MessageEntity(
                conversationId = conversationId,
                content = content,
                role = Constants.ROLE_USER,
                status = Constants.STATUS_SENDING
            )
        )
        return requestCompletion(conversationId, userMessageId)
    }

    override suspend fun retryMessage(messageId: Long): Result<Message> {
        val message = messageDao.getMessageById(messageId)
            ?: return Result.failure(IllegalStateException("消息不存在"))
        if (message.role != Constants.ROLE_USER) {
            return Result.failure(IllegalStateException("只能重试用户消息"))
        }
        messageDao.updateStatus(messageId, Constants.STATUS_SENDING, null)
        return requestCompletion(message.conversationId, messageId)
    }

    /**
     * 携带会话历史请求模型回复。
     *
     * [userMessageId] 对应的用户消息在成功后标记为 success，失败则标记为 failed
     * 并记录原因，界面据此提供重试入口，不会留下无回复的孤立消息。
     */
    private suspend fun requestCompletion(
        conversationId: Long,
        userMessageId: Long
    ): Result<Message> {
        return try {
            val settings = settingsProvider.current()
            if (settings.apiKey.isBlank()) {
                throw IllegalStateException("请先在设置中配置 API Key")
            }

            val request = ChatRequest(
                model = settings.model,
                messages = buildContext(conversationId, userMessageId)
            )

            val response = openAIApi.createChatCompletion(request)
            val reply = response.choices.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("模型返回内容为空")

            messageDao.updateStatus(userMessageId, Constants.STATUS_SUCCESS, null)

            val assistantId = messageDao.insertMessage(
                MessageEntity(
                    conversationId = conversationId,
                    content = reply,
                    role = Constants.ROLE_ASSISTANT,
                    status = Constants.STATUS_SUCCESS
                )
            )

            touchConversation(conversationId)

            Result.success(
                Message(
                    id = assistantId,
                    conversationId = conversationId,
                    content = reply,
                    role = Constants.ROLE_ASSISTANT,
                    createdAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            val reason = e.message ?: "发送失败"
            messageDao.updateStatus(userMessageId, Constants.STATUS_FAILED, reason)
            Result.failure(e)
        }
    }

    /**
     * 组装多轮上下文。
     *
     * 只取成功的历史消息，外加本次待发送的用户消息，并按
     * [Constants.MAX_CONTEXT_MESSAGES] 保留最近若干条以控制 token 消耗。
     */
    private suspend fun buildContext(
        conversationId: Long,
        currentMessageId: Long
    ): List<RequestMessage> {
        val history = messageDao.getMessagesOnce(conversationId)
        val usable = history.filter {
            it.id == currentMessageId || it.status == Constants.STATUS_SUCCESS
        }
        return usable
            .takeLast(Constants.MAX_CONTEXT_MESSAGES)
            .map { RequestMessage(role = it.role, content = it.content) }
    }

    private suspend fun touchConversation(conversationId: Long) {
        conversationDao.getConversationById(conversationId)?.let { entity ->
            conversationDao.updateConversation(
                entity.copy(updatedAt = System.currentTimeMillis())
            )
        }
    }

    override suspend fun renameConversation(conversationId: Long, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        conversationDao.getConversationById(conversationId)?.let { entity ->
            conversationDao.updateConversation(
                entity.copy(title = trimmed, updatedAt = System.currentTimeMillis())
            )
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
        status = status,
        errorMessage = errorMessage,
        createdAt = createdAt
    )
}
