package com.afyzfur.afyzhub.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.afyzfur.afyzhub.util.Constants

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val content: String,
    /** "user" 或 "assistant"，取值见 [Constants.ROLE_USER] */
    val role: String,
    /** 发送状态，取值见 [Constants.STATUS_SENDING] 等 */
    @ColumnInfo(defaultValue = Constants.STATUS_SUCCESS)
    val status: String = Constants.STATUS_SUCCESS,
    /** 失败原因，仅在 status 为 failed 时有值 */
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),

    // 以下为 v3 新增的元信息，全部可空。
    // 可空是必要的：v3 之前的历史消息没有这些数据，不能编造默认值。
    // UI 侧遇到 null 时不渲染对应项，而不是显示 0。

    /** 生成该回复的模型名，用户消息为 null */
    val model: String? = null,
    /** 输入 token 数，来自各家的 usage 字段 */
    val promptTokens: Int? = null,
    /** 输出 token 数 */
    val completionTokens: Int? = null,
    /** 从发出请求到回复结束的毫秒数 */
    val latencyMs: Long? = null
)
