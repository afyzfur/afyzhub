package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.ui.chat.THINKING_PREVIEW
import com.afyzfur.afyzhub.ui.chat.previewOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 抽屉预览的测试。
 *
 * 上一版这里改错过一次：answer 为空时退回原文，而原文正是带标签的
 * 全文，于是"剥离思考"在最需要生效的场景下完全没生效。
 */
class ConversationPreviewTest {

    @Test
    fun `思考已闭合时只取回答`() {
        val preview = previewOf("<think>先分析一下</think>这是答案")

        assertEquals("这是答案", preview)
    }

    @Test
    fun `思考进行中且无回答时给状态文案`() {
        // 这是上一版失效的场景：整条都在思考里
        val preview = previewOf("<think>还在想，这段很长很长")

        assertEquals(THINKING_PREVIEW, preview)
        assertFalse("预览不该出现标签", preview.contains("think"))
    }

    @Test
    fun `思考已闭合但回答尚未到达时给状态文案`() {
        val preview = previewOf("<think>想完了</think>")

        assertEquals(THINKING_PREVIEW, preview)
    }

    @Test
    fun `无思考标签时原样显示`() {
        assertEquals("普通回复", previewOf("普通回复"))
    }

    @Test
    fun `换行折叠为空格`() {
        // 预览只有一行，换行会显示成一个看不出来的间断
        assertEquals("第一行 第二行", previewOf("第一行\n第二行"))
    }

    @Test
    fun `开标签之前的内容算作回答`() {
        val preview = previewOf("先说一句<think>然后想</think>")

        assertEquals("先说一句", preview)
    }

    @Test
    fun `空内容不崩且返回空串`() {
        assertEquals("", previewOf("   \n  "))
    }
}
