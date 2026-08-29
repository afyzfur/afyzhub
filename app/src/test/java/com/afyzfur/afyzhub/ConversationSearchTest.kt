package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.domain.model.ConversationItem
import com.afyzfur.afyzhub.ui.chat.searchConversations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSearchTest {

    private fun item(
        id: Long,
        title: String,
        summary: String? = null,
        note: String? = null,
        group: String = ""
    ) = ConversationItem(
        id = id,
        title = title,
        updatedAt = id,
        summary = summary,
        lastMessage = null,
        note = note,
        group = group
    )

    private val items = listOf(
        item(1, "Kotlin 协程入门", summary = "讲了 launch 与 async 的区别"),
        item(2, "报错排查", note = "Python 的编码问题", group = "工作"),
        item(3, "晚饭吃什么", group = "生活"),
        item(4, "Compose 布局", summary = "约束传递", group = "工作")
    )

    @Test
    fun `按标题命中`() {
        val r = searchConversations(items, "协程")
        assertEquals(1, r.size)
        assertEquals(1L, r.first().id)
    }

    @Test
    fun `按总结命中`() {
        val r = searchConversations(items, "async")
        assertEquals(1L, r.single().id)
    }

    @Test
    fun `按简介命中`() {
        // 简介是用户自己写的，最能反映他想记住什么，必须可搜
        val r = searchConversations(items, "编码")
        assertEquals(2L, r.single().id)
    }

    @Test
    fun `按分组命中`() {
        val r = searchConversations(items, "工作")
        assertEquals(2, r.size)
    }

    @Test
    fun `大小写不敏感`() {
        // 模型名与英文标题常有大小写差异，要求精确匹配会搜不到
        assertEquals(1L, searchConversations(items, "KOTLIN").single().id)
        assertEquals(4L, searchConversations(items, "compose").single().id)
    }

    @Test
    fun `多个词要全部命中但可落在不同字段`() {
        // 标题写 Python 之外的词、简介写 Python：跨字段组合必须能搜到
        val r = searchConversations(items, "报错 Python")
        assertEquals(2L, r.single().id)
    }

    @Test
    fun `多个词有一个不命中就排除`() {
        assertTrue(searchConversations(items, "报错 Kotlin").isEmpty())
    }

    @Test
    fun `空查询返回全部`() {
        assertEquals(items.size, searchConversations(items, "").size)
        assertEquals(items.size, searchConversations(items, "   ").size)
    }

    @Test
    fun `不搜消息正文`() {
        // lastMessage 刻意不在匹配范围内：命中一句话中间的词
        // 对"找到那个会话"帮助不大，却会拉进大量弱相关结果
        val withMessage = listOf(
            item(9, "无关标题").copy(lastMessage = "这里提到了协程")
        )
        assertTrue(searchConversations(withMessage, "协程").isEmpty())
    }

    @Test
    fun `保持原有顺序`() {
        // 结果不重排：调用方已按置顶与时间排好，搜索只做筛选
        val r = searchConversations(items, "工作")
        assertEquals(listOf(2L, 4L), r.map { it.id })
    }
}
