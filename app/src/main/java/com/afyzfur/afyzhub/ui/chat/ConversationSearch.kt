package com.afyzfur.afyzhub.ui.chat

import com.afyzfur.afyzhub.domain.model.ConversationItem

/**
 * 按关键词筛选会话。
 *
 * 匹配范围是标题、总结、简介与分组名——这四项都是用来"认出这个
 * 会话"的信息。不搜消息正文：那需要全表扫描每条消息，而且命中
 * 一句话中间的词对于"找到那个会话"帮助不大，反而会把大量弱相关
 * 的会话拉进结果。
 *
 * 大小写不敏感。中文没有大小写，但模型名与英文标题常有大小写差异，
 * 要求精确匹配会让人搜不到自己刚建的会话。
 *
 * 多个空格分隔的词按"全都要命中"处理，各词可落在不同字段：搜
 * "python 报错"应当找到标题写 Python、简介写报错排查的那一条。
 * 若按整串匹配，这种跨字段的组合永远搜不出来。
 */
fun searchConversations(
    items: List<ConversationItem>,
    query: String
): List<ConversationItem> {
    val terms = query.trim().split(WHITESPACE).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return items
    return items.filter { item ->
        val haystack = buildString {
            append(item.title)
            append('\u0000')
            item.summary?.let { append(it); append('\u0000') }
            item.note?.let { append(it); append('\u0000') }
            append(item.group)
        }.lowercase()
        // 每个词都要命中，但允许落在不同字段
        terms.all { haystack.contains(it.lowercase()) }
    }
}

private val WHITESPACE = Regex("\\s+")
