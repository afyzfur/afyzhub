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
    val summary: String? = null,
    /**
     * 置顶。置顶的会话排在列表最前，与星标相互独立。
     *
     * 用 Boolean 而非置顶时间戳：多条置顶之间仍按 updatedAt 排序，
     * 这与"置顶只是把它拉到前面，不改变彼此的新旧关系"的直觉一致。
     */
    val pinned: Boolean = false,
    /**
     * 星标。用于跨分组标记重点会话，不影响排序。
     *
     * 与置顶分开：置顶是"我现在正在用它"，星标是"这条以后还要找回来"，
     * 两者的生命周期不同，合成一个字段会逼用户在两种意图里选一个。
     */
    val starred: Boolean = false,
    /** 用户自己写的简介，与模型生成的 summary 分开存 */
    val note: String? = null,
    /** 分组名。空串表示未分组，与 ApiProfile 的分组用同一套约定 */
    val group: String = ""
)