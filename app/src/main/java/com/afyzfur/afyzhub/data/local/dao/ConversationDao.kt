package com.afyzfur.afyzhub.data.local.dao

import androidx.room.*
import com.afyzfur.afyzhub.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    /**
     * 会话列表 + 每个会话的末条消息，供抽屉显示摘要。
     *
     * 用相关子查询而非 GROUP BY：后者在 SQLite 中取"每组最新一行的某列"
     * 需要依赖非标准的裸列行为，可读性差且不保证跨版本一致。
     * 子查询按 id DESC 取一条，id 自增所以等价于最新插入的一条。
     */
    @Query(
        """
        SELECT c.id, c.title, c.createdAt, c.updatedAt, c.summary,
               (SELECT m.content FROM messages m
                WHERE m.conversationId = c.id
                ORDER BY m.id DESC LIMIT 1) AS lastMessage
        FROM conversations c
        ORDER BY c.updatedAt DESC
        """
    )
    fun getConversationSummaries(): Flow<List<ConversationSummary>>

    /**
     * 只更新总结，不动 updatedAt。
     *
     * 保持 updatedAt 不变很重要：抽屉按它排序并分组，总结是回复完成后
     * 异步写入的，若一并刷新时间会把会话挪到"今天"，破坏时间分组。
     */
    @Query("UPDATE conversations SET summary = :summary WHERE id = :id")
    suspend fun updateSummary(id: Long, summary: String)

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Delete
    suspend fun deleteConversation(conversation: ConversationEntity)
}