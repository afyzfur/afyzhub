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

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesByConversationId(conversationId: Long)
}
