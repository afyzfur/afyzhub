package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.domain.model.parseThinking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 思考标签解析的测试。
 *
 * 用例以实际遇到的输出为主——截图里出现过标签原样显示，
 * 说明这段逻辑此前完全缺失。
 */
class ThinkingContentTest {

    @Test
    fun `无标签时全部是回答`() {
        val result = parseThinking("我是 Grok。")
        assertNull(result.reasoning)
        assertEquals("我是 Grok。", result.answer)
        assertFalse(result.thinking)
    }

    @Test
    fun `闭合标签分离思考与回答`() {
        val raw = "<think>用户在问我是谁</think>我是 Grok，由 xAI 构建。"
        val result = parseThinking(raw)

        assertEquals("用户在问我是谁", result.reasoning)
        assertEquals("我是 Grok，由 xAI 构建。", result.answer)
        assertFalse(result.thinking)
    }

    @Test
    fun `未闭合标签视为思考进行中`() {
        val result = parseThinking("<think>让我想想这个问题")

        assertEquals("让我想想这个问题", result.reasoning)
        assertEquals("", result.answer)
        assertTrue(result.thinking)
    }

    @Test
    fun `多行思考内容完整保留`() {
        val raw = "<think>第一步：理解问题\n第二步：组织答案</think>答案在此"
        val result = parseThinking(raw)

        assertEquals("第一步：理解问题\n第二步：组织答案", result.reasoning)
        assertEquals("答案在此", result.answer)
    }

    @Test
    fun `空思考内容视为无思考`() {
        val result = parseThinking("<think></think>直接回答")

        assertNull(result.reasoning)
        assertEquals("直接回答", result.answer)
        assertFalse(result.hasReasoning)
    }

    @Test
    fun `回答中含有相同文本时不被误删`() {
        // 移除标签用的是 removeRange 而非 replace，
        // 回答里出现与思考内容相同的文字时不该被一起删掉
        val raw = "<think>重复</think>重复"
        val result = parseThinking(raw)

        assertEquals("重复", result.reasoning)
        assertEquals("重复", result.answer)
    }

    @Test
    fun `开标签之前的内容归入回答`() {
        val result = parseThinking("前言<think>思考中")

        assertEquals("前言", result.answer)
        assertEquals("思考中", result.reasoning)
        assertTrue(result.thinking)
    }
}
