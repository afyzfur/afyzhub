package com.afyzfur.afyzhub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.afyzfur.afyzhub.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun getMessagesByConversationId(conversationId: Long): Flow<List<MessageEntity>>

    /** 一次性读取历史消息，用于组装请求上下文。 */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessagesOnce(conversationId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: Long): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("UPDATE messages SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, errorMessage: String?)

    /** 流式回复过程中逐步写入已接收的内容。 */
    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateContent(id: Long, content: String)

    @Query("UPDATE messages SET content = :content, status = :status WHERE id = :id")
    suspend fun updateContentAndStatus(id: Long, content: String, status: String)

    /**
     * 流式回复结束时一次性写入正文、状态与元信息。
     *
     * 合并为单条 UPDATE 而不是分多次调用：流式过程中 updateContent 已经
     * 触发了大量写入，收尾阶段不必再增加事务数。
     */
    @Query(
        """
        UPDATE messages
        SET content = :content,
            status = :status,
            model = :model,
            promptTokens = :promptTokens,
            completionTokens = :completionTokens,
            latencyMs = :latencyMs
        WHERE id = :id
        """
    )
    suspend fun finalizeAssistantMessage(
        id: Long,
        content: String,
        status: String,
        model: String?,
        promptTokens: Int?,
        completionTokens: Int?,
        latencyMs: Long?
    )

    /**
     * 删除该会话中内容为空且仍处于指定状态的消息。
     *
     * 用于暂停后清理没收到任何内容的助手占位——留一条空消息没有意义。
     * 按内容为空而非按 id 筛选：暂停的收尾不该依赖调用方还持有那个 id，
     * 之前靠传 id 收尾的做法在取消时序下并不可靠。
     */
    @Query(
        "DELETE FROM messages WHERE conversationId = :conversationId " +
            "AND status = :status AND TRIM(content) = ''"
    )
    suspend fun deleteEmptyMessagesByStatus(conversationId: Long, status: String): Int

    /**
     * 把该会话中仍处于 [fromStatus] 的消息全部改为 [toStatus]。
     *
     * 暂停时用它归位，避免消息永久停在「发送中」。做成批量更新是因为
     * 一次发送会牵动用户消息与助手占位两条，且暂停发生的时机不定，
     * 调用方未必知道哪些落了库。
     */
    @Query(
        "UPDATE messages SET status = :toStatus, errorMessage = NULL " +
            "WHERE conversationId = :conversationId AND status = :fromStatus"
    )
    suspend fun settlePendingMessages(
        conversationId: Long,
        fromStatus: String,
        toStatus: String
    ): Int

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    /**
     * 删除该会话中这条消息及其之后的全部消息。
     *
     * 用于「回滚到此处」：把对话退回到某条消息之前的状态。
     * 按 createdAt 与 id 双重比较，与列表排序保持一致——
     * 同一毫秒内插入的多条消息仅靠时间无法定序。
     */
    @Query(
        "DELETE FROM messages WHERE conversationId = :conversationId AND (" +
            "createdAt > :createdAt OR (createdAt = :createdAt AND id >= :id))"
    )
    suspend fun deleteFrom(conversationId: Long, createdAt: Long, id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesByConversationId(conversationId: Long)
}
