package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.domain.model.ConversationItem
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
        summary = null,
        lastMessage = null
    )

    private fun shiftDays(from: Long, days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = from
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

    /** 取分组标签，便于断言 */
    private fun labels(
        items: List<ConversationItem>,
        now: Long
    ): List<String> = groupConversations(items, now).keys.map { it.label }

    @Test
    fun `空列表返回空分组`() {
        assertTrue(groupConversations(emptyList(), baseTime()).isEmpty())
    }

    @Test
    fun `同一天的会话归入今天`() {
        val now = baseTime()
        val morning = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 1)
        }.timeInMillis

        val result = groupConversations(listOf(item(1, now), item(2, morning)), now)

        assertEquals(listOf("今天"), result.keys.map { it.label })
        assertEquals(2, result.values.first().size)
    }

    @Test
    fun `前一天与前两天分别归入昨天与前天`() {
        val now = baseTime()
        val result = labels(
            listOf(item(1, shiftDays(now, -1)), item(2, shiftDays(now, -2))),
            now
        )
        assertEquals(listOf("昨天", "前天"), result)
    }

    @Test
    fun `本周内更早的日子显示星期几`() {
        // 基准是周四，回退 3 天为周一，仍在本周内（周日为一周之首时）
        val now = baseTime()
        val result = labels(listOf(item(1, shiftDays(now, -3))), now)

        assertEquals(1, result.size)
        assertTrue("应显示星期几，实际为 ${result[0]}", result[0].startsWith("星期"))
    }

    @Test
    fun `跨周的会话显示日期`() {
        val now = baseTime()
        // 回退 10 天必然跨周
        val result = labels(listOf(item(1, shiftDays(now, -10))), now)

        assertEquals(1, result.size)
        assertTrue("应显示日期，实际为 ${result[0]}", result[0].contains("月"))
    }

    @Test
    fun `同年不带年份跨年才带`() {
        val now = baseTime()
        val sameYear = labels(listOf(item(1, shiftDays(now, -30))), now)
        assertTrue("同年不该带年份：${sameYear[0]}", !sameYear[0].contains("年"))

        val lastYear = labels(listOf(item(1, shiftDays(now, -400))), now)
        assertTrue("跨年应带年份：${lastYear[0]}", lastYear[0].contains("年"))
    }

    @Test
    fun `分组按时间从近到远排列`() {
        val now = baseTime()
        // 故意打乱输入顺序，验证输出由档位序号决定
        val result = labels(
            listOf(
                item(1, shiftDays(now, -10)),
                item(2, now),
                item(3, shiftDays(now, -2)),
                item(4, shiftDays(now, -1))
            ),
            now
        )

        assertEquals("今天", result.first())
        assertEquals(listOf("今天", "昨天", "前天"), result.take(3))
    }

    @Test
    fun `组内保持输入的倒序`() {
        val now = baseTime()
        val earlier = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 9)
        }.timeInMillis

        val result = groupConversations(listOf(item(10, now), item(11, earlier)), now)

        assertEquals(listOf(10L, 11L), result.values.first().map { it.id })
    }

    @Test
    fun `每条会话必须且只能属于一个组`() {
        val now = baseTime()
        val items = (1..40).map { item(it.toLong(), shiftDays(now, -it)) }
        val result = groupConversations(items, now)

        assertEquals(40, result.values.sumOf { it.size })
    }
}
