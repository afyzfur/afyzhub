package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.data.local.dao.ConversationDao
import com.afyzfur.afyzhub.data.local.dao.ConversationSummary
import com.afyzfur.afyzhub.data.local.dao.MessageDao
import com.afyzfur.afyzhub.data.local.entity.ConversationEntity
import com.afyzfur.afyzhub.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** 内存版 MessageDao，用于在 JVM 上验证仓库逻辑。 */
class FakeMessageDao : MessageDao {

    private val state = MutableStateFlow<List<MessageEntity>>(emptyList())
    private var nextId = 1L

    val current: List<MessageEntity> get() = state.value

    override fun getMessagesByConversationId(conversationId: Long): Flow<List<MessageEntity>> =
        state.map { list -> list.filter { it.conversationId == conversationId } }

    override suspend fun getMessagesOnce(conversationId: Long): List<MessageEntity> =
        state.value.filter { it.conversationId == conversationId }.sortedBy { it.id }

    override suspend fun getMessageById(id: Long): MessageEntity? =
        state.value.firstOrNull { it.id == id }

    override suspend fun insertMessage(message: MessageEntity): Long {
        val id = if (message.id == 0L) nextId++ else message.id
        val stored = message.copy(id = id)
        state.value = state.value.filterNot { it.id == id } + stored
        return id
    }

    override suspend fun updateStatus(id: Long, status: String, errorMessage: String?) {
        state.value = state.value.map {
            if (it.id == id) it.copy(status = status, errorMessage = errorMessage) else it
        }
    }

    override suspend fun updateContent(id: Long, content: String) {
        state.value = state.value.map {
            if (it.id == id) it.copy(content = content) else it
        }
    }

    override suspend fun updateContentAndStatus(id: Long, content: String, status: String) {
        state.value = state.value.map {
            if (it.id == id) it.copy(content = content, status = status) else it
        }
    }

    override suspend fun deleteMessageById(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun deleteMessagesByConversationId(conversationId: Long) {
        state.value = state.value.filterNot { it.conversationId == conversationId }
    }
}

/**
 * 内存版 ConversationDao。
 *
 * @param messageDao 可选。传入后 [getConversationSummaries] 会据其内容计算末条消息，
 *   模拟真实实现里的关联查询；不传则摘要恒为 null，已有测试无需改动。
 */
class FakeConversationDao(
    private val messageDao: FakeMessageDao? = null
) : ConversationDao {

    private val state = MutableStateFlow<List<ConversationEntity>>(emptyList())
    private var nextId = 1L
    override fun getAllConversations(): Flow<List<ConversationEntity>> = state

    override fun getConversationSummaries(): Flow<List<ConversationSummary>> =
        state.map { list ->
            list.sortedByDescending { it.updatedAt }.map { conversation ->
                ConversationSummary(
                    id = conversation.id,
                    title = conversation.title,
                    createdAt = conversation.createdAt,
                    updatedAt = conversation.updatedAt,
                    // 与 SQL 中的子查询一致：按 id 取该会话最后插入的一条
                    lastMessage = messageDao?.current
                        ?.filter { it.conversationId == conversation.id }
                        ?.maxByOrNull { it.id }
                        ?.content
                )
            }
        }

    override suspend fun getConversationById(id: Long): ConversationEntity? =
        state.value.firstOrNull { it.id == id }

    override suspend fun insertConversation(conversation: ConversationEntity): Long {
        val id = if (conversation.id == 0L) nextId++ else conversation.id
        state.value = state.value.filterNot { it.id == id } + conversation.copy(id = id)
        return id
    }

    override suspend fun updateConversation(conversation: ConversationEntity) {
        state.value = state.value.map { if (it.id == conversation.id) conversation else it }
    }

    override suspend fun deleteConversation(conversation: ConversationEntity) {
        state.value = state.value.filterNot { it.id == conversation.id }
    }
}
