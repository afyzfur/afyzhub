package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.domain.model.ConversationItem
import com.afyzfur.afyzhub.ui.chat.ConversationGroup
import com.afyzfur.afyzhub.ui.chat.groupConversations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ConversationGroupingTest {

    /** 固定基准时刻：2026-08-20（周四）15:30，避免测试结果随运行日期变化 */
    private fun baseTime(): Long = Calendar.getInstance().apply {
        set(2026, Calendar.AUGUST, 20, 15, 30, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun item(id: Long, updatedAt: Long) = ConversationItem(
        id = id,
        title = "会话 $id",
        updatedAt = updatedAt,
        lastMessage = null
    )

    private fun shiftDays(from: Long, days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = from
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

    @Test
    fun `空列表返回空分组`() {
        assertTrue(groupConversations(emptyList(), baseTime()).isEmpty())
    }

    @Test
    fun `同一天的会话归入今天`() {
        val now = baseTime()
        // 当天更早的时刻，仍属今天
        val morning = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 1)
        }.timeInMillis

        val result = groupConversations(listOf(item(1, now), item(2, morning)), now)

        assertEquals(setOf(ConversationGroup.TODAY), result.keys)
        assertEquals(2, result[ConversationGroup.TODAY]?.size)
    }

    @Test
    fun `前一天的会话归入昨天`() {
        val now = baseTime()
        val result = groupConversations(listOf(item(1, shiftDays(now, -1))), now)

        assertEquals(setOf(ConversationGroup.YESTERDAY), result.keys)
    }

    @Test
    fun `跨月的旧会话归入更早`() {
        val now = baseTime()
        // 两个月前，必然落在本月之前
        val old = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MONTH, -2)
        }.timeInMillis

        val result = groupConversations(listOf(item(1, old)), now)

        assertEquals(setOf(ConversationGroup.EARLIER), result.keys)
    }

    @Test
    fun `分组顺序固定为今天在前更早在后`() {
        val now = baseTime()
        val old = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MONTH, -2)
        }.timeInMillis

        // 故意把旧会话放在列表首位，验证输出顺序由枚举决定而非输入顺序
        val result = groupConversations(
            listOf(item(1, old), item(2, now)),
            now
        )

        assertEquals(
            listOf(ConversationGroup.TODAY, ConversationGroup.EARLIER),
            result.keys.toList()
        )
    }

    @Test
    fun `组内保持输入的倒序`() {
        val now = baseTime()
        val earlier = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 9)
        }.timeInMillis

        // 输入已按 updatedAt 倒序（新的在前）
        val result = groupConversations(listOf(item(10, now), item(11, earlier)), now)

        assertEquals(listOf(10L, 11L), result[ConversationGroup.TODAY]?.map { it.id })
    }

    @Test
    fun `本周与本月的边界不会互相吞并`() {
        val now = baseTime()

        // 逐天回溯 40 天，确认每个时间点都能落进某个组且不重复
        val items = (1..40).map { item(it.toLong(), shiftDays(now, -it)) }
        val result = groupConversations(items, now)

        val total = result.values.sumOf { it.size }
        assertEquals("每条会话必须且只能属于一个组", 40, total)
    }
}
