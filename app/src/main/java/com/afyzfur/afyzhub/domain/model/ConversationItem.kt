package com.afyzfur.afyzhub.domain.model

/**
 * 抽屉会话列表项。
 *
 * 与 [Conversation] 分开而不是给它加可空字段：Conversation 表示会话本体，
 * 摘要是列表展示的附加信息，来自跨表查询。混在一起会让"这个字段什么时候有值"
 * 变成调用方需要记住的隐含约定。
 */
data class ConversationItem(
    val id: Long,
    val title: String,
    val updatedAt: Long,
    /** 模型生成的一句话总结，未生成时为 null */
    val summary: String?,
    /** 末条消息正文，无消息时为 null。已在数据层截断 */
    val lastMessage: String?,
    /** 置顶，排在列表最前 */
    val pinned: Boolean = false,
    /** 星标 */
    val starred: Boolean = false,
    /** 用户自己写的简介。与模型生成的 summary 分开，优先显示 */
    val note: String? = null,
    /** 分组名，空串表示未分组 */
    val group: String = ""
)
