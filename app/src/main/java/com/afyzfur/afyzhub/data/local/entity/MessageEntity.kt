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
    val createdAt: Long = System.currentTimeMillis()
)
