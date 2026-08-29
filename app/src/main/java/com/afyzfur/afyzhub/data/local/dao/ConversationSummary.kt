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
    /**
     * 模型生成的一句话总结，未生成或生成失败时为 null。
     *
     * 这一列是冗余存储，与上面注释所说的"不加冗余列"看似矛盾：
     * 区别在于它不是消息内容的副本，而是一次不可重现的模型输出，
     * 除了存下来别无办法。
     */
    val summary: String?,
    /** 置顶，列表里排在最前 */
    val pinned: Boolean,
    /** 星标 */
    val starred: Boolean,
    /** 用户自己写的简介，与模型生成的 summary 分开 */
    val note: String?,
    /** 分组名，空串表示未分组 */
    val group: String,
    /** 末条消息正文，会话尚无消息时为 null */
    val lastMessage: String?
)
