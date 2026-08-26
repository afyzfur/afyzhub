package com.afyzfur.afyzhub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * 由模型生成的一句话总结，用于抽屉列表的第二行。
     *
     * 为 null 时界面退回显示末条消息。存字段而不是每次现算：
     * 生成要发一次请求，不能在滚动列表时触发。
     */
    val summary: String? = null
)