package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.data.remote.provider.ThinkingStreamWrapper
import com.afyzfur.afyzhub.domain.model.ThinkingEffort
import com.afyzfur.afyzhub.domain.model.parseThinking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 独立思考通道折回内嵌标签的测试。
 *
 * 断言的落点是 parseThinking 的解析结果，而不是拼出的字符串字面量：
 * 折回的唯一目的就是让上层能解析出来，直接断言中间形态会让测试
 * 绑死在标签的具体写法上。
 */
class ThinkingStreamWrapperTest {

    /** 把若干块思考与正文按顺序喂进去，返回拼好的完整文本。 */
    private fun run(vararg pieces: Pair<Boolean, String>): String {
        val w = ThinkingStreamWrapper()
        val sb = StringBuilder()
        pieces.forEach { (isThinking, text) ->
            sb.append(if (isThinking) w.onThinking(text) else w.onText(text))
        }
        sb.append(w.finish())
        return sb.toString()
    }

    @Test
    fun `思考分多块推送时只包一层标签`() {
        val out = run(true to "先", true to "想想", false to "答案")
        val parsed = parseThinking(out)
        assertEquals("先想想", parsed.reasoning)
        assertEquals("答案", parsed.answer)
    }

    @Test
    fun `没有思考时正文原样透传`() {
        val out = run(false to "直接回答")
        assertEquals("直接回答", out)
        assertEquals("直接回答", parseThinking(out).answer)
    }

    @Test
    fun `全程思考没有正文时标签仍然闭合`() {
        // 被 token 上限截断的情形：只有思考，一个字正文都没来
        val out = run(true to "想了很久")
        val parsed = parseThinking(out)
        assertEquals("想了很久", parsed.reasoning)
        assertEquals("", parsed.answer)
    }

    @Test
    fun `正文开始后再来的思考不会破坏标签配对`() {
        // 闭标签只补一次，后续思考并入正文而非再开一段
        val out = run(true to "想", false to "答", true to "又想", false to "续")
        val parsed = parseThinking(out)
        assertEquals("想", parsed.reasoning)
        assertTrue(parsed.answer.startsWith("答"))
    }

    @Test
    fun `空串不产生标签`() {
        val out = run(true to "", false to "只有正文")
        assertEquals("只有正文", out)
    }

    @Test
    fun `finish 重复调用不会补出两个闭标签`() {
        val w = ThinkingStreamWrapper()
        val sb = StringBuilder()
        sb.append(w.onThinking("想"))
        sb.append(w.finish())
        sb.append(w.finish())
        assertEquals("想", parseThinking(sb.toString()).reasoning)
    }

    @Test
    fun `Anthropic 的 max tokens 在开思考时抬高到预算之上`() {
        val default = 4096
        // OFF 不改动默认值
        assertEquals(default, ThinkingEffort.OFF.anthropicMaxTokens(default))
        // 其余档位都必须严格大于预算，否则 Claude 直接返回 400
        listOf(ThinkingEffort.LOW, ThinkingEffort.MEDIUM, ThinkingEffort.HIGH).forEach {
            val budget = it.tokenBudget!!
            assertTrue(
                "$it 的 max_tokens 必须大于预算 $budget",
                it.anthropicMaxTokens(default) > budget
            )
        }
    }

    @Test
    fun `抬高后的额度给正文留有空间`() {
        // 仅比预算多一个 token 是不够的：正文会被立刻截断
        val default = 4096
        val high = ThinkingEffort.HIGH
        val room = high.anthropicMaxTokens(default) - high.tokenBudget!!
        assertEquals(default, room)
    }
}
