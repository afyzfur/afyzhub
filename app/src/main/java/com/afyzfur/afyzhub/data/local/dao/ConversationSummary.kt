package com.afyzfur.afyzhub.data.local.dao

/**
 * 会话列表项的查询投影：会话本身 + 末条消息摘要。
 *
 * 为什么用关联查询而不给 conversations 表加冗余列：
 * 加列需要数据库迁移，且每次收发消息都要同步维护，一旦漏更新就会出现
 * 摘要与实际内容不一致。关联查询没有这个一致性风险。
 * 会话量在移动端是几十到几百级别，单次 JOIN 的代价可以忽略。
 */
data class ConversationSummary(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** 末条消息正文，会话尚无消息时为 null */
    val lastMessage: String?
)
