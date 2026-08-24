package com.afyzfur.afyzhub.data.repository

import com.afyzfur.afyzhub.domain.model.Conversation
import com.afyzfur.afyzhub.domain.model.ConversationItem
import com.afyzfur.afyzhub.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {

    fun getAllConversations(): Flow<List<Conversation>>

    /** 会话列表附带末条消息摘要，供抽屉列表使用。 */
    fun getConversationItems(): Flow<List<ConversationItem>>

    fun getMessagesByConversationId(conversationId: Long): Flow<List<Message>>

    suspend fun createConversation(title: String): Long

    /**
     * 发送一条用户消息并等待回复。
     *
     * 鉴权与 API 地址由网络层根据设置自动注入，调用方无需传入。
     */
    suspend fun sendMessage(conversationId: Long, content: String): Result<Message>

    /** 重新发送一条此前失败的用户消息。 */
    suspend fun retryMessage(messageId: Long): Result<Message>

    suspend fun renameConversation(conversationId: Long, title: String)

    suspend fun deleteConversation(conversationId: Long)
}
