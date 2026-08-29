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

    /**
     * 供 FakeConversationDao 模拟外键级联删除。
     *
     * 真实数据库靠 ForeignKey.CASCADE 自动清理，这里没有外键约束，
     * 不手动清会留下孤儿消息，让测试与实际行为不一致。
     */
    fun removeByConversation(conversationId: Long) {
        state.value = state.value.filterNot { it.conversationId == conversationId }
    }

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

    override suspend fun finalizeAssistantMessage(
        id: Long,
        content: String,
        status: String,
        model: String?,
        promptTokens: Int?,
        completionTokens: Int?,
        latencyMs: Long?
    ) {
        state.value = state.value.map {
            if (it.id == id) {
                it.copy(
                    content = content,
                    status = status,
                    model = model,
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                    latencyMs = latencyMs
                )
            } else {
                it
            }
        }
    }

    override suspend fun deleteEmptyMessagesByStatus(
        conversationId: Long,
        status: String
    ): Int {
        val before = state.value
        state.value = before.filterNot {
            it.conversationId == conversationId &&
                it.status == status &&
                it.content.isBlank()
        }
        return before.size - state.value.size
    }

    override suspend fun settlePendingMessages(
        conversationId: Long,
        fromStatus: String,
        toStatus: String
    ): Int {
        var affected = 0
        state.value = state.value.map {
            if (it.conversationId == conversationId && it.status == fromStatus) {
                affected++
                it.copy(status = toStatus, errorMessage = null)
            } else {
                it
            }
        }
        return affected
    }

    override suspend fun deleteFrom(conversationId: Long, createdAt: Long, id: Long) {
        state.value = state.value.filterNot {
            it.conversationId == conversationId &&
                (it.createdAt > createdAt || (it.createdAt == createdAt && it.id >= id))
        }
    }

    /** 条件与 deleteFrom 保持一致，否则撤回的范围会与实际删除不符 */
    override suspend fun getMessagesFrom(
        conversationId: Long,
        createdAt: Long,
        id: Long
    ): List<MessageEntity> = state.value
        .filter {
            it.conversationId == conversationId &&
                (it.createdAt > createdAt || (it.createdAt == createdAt && it.id >= id))
        }
        .sortedWith(compareBy({ it.createdAt }, { it.id }))

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
            // 与 SQL 的 ORDER BY 一致：置顶优先，其次按更新时间
            list.sortedWith(
                compareByDescending<ConversationEntity> { it.pinned }
                    .thenByDescending { it.updatedAt }
            ).map { conversation ->
                ConversationSummary(
                    id = conversation.id,
                    title = conversation.title,
                    createdAt = conversation.createdAt,
                    updatedAt = conversation.updatedAt,
                    summary = conversation.summary,
                    pinned = conversation.pinned,
                    starred = conversation.starred,
                    note = conversation.note,
                    group = conversation.group,
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

    /** 与真实实现一致：只改 summary，不动 updatedAt */
    override suspend fun updateSummary(id: Long, summary: String) {
        state.value = state.value.map {
            if (it.id == id) it.copy(summary = summary) else it
        }
    }

    override suspend fun deleteConversation(conversation: ConversationEntity) {
        state.value = state.value.filterNot { it.id == conversation.id }
    }

    override suspend fun deleteConversationById(id: Long) {
        state.value = state.value.filterNot { it.id == id }
        // 真实实现靠外键级联，这里手动清一遍以保持行为一致
        messageDao?.removeByConversation(id)
    }

    /** 以下几个与真实实现一致：只改单个字段，不动 updatedAt */
    override suspend fun updatePinned(id: Long, pinned: Boolean) {
        state.value = state.value.map {
            if (it.id == id) it.copy(pinned = pinned) else it
        }
    }

    override suspend fun updateStarred(id: Long, starred: Boolean) {
        state.value = state.value.map {
            if (it.id == id) it.copy(starred = starred) else it
        }
    }

    override suspend fun updateTitle(id: Long, title: String) {
        state.value = state.value.map {
            if (it.id == id) it.copy(title = title) else it
        }
    }

    override suspend fun updateNote(id: Long, note: String?) {
        state.value = state.value.map {
            if (it.id == id) it.copy(note = note) else it
        }
    }

    override suspend fun updateGroup(id: Long, group: String) {
        state.value = state.value.map {
            if (it.id == id) it.copy(group = group) else it
        }
    }

    override fun getGroups(): Flow<List<String>> =
        state.map { list ->
            list.map { it.group }.filter { it.isNotEmpty() }.distinct().sorted()
        }
}
