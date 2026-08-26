package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.domain.usecase.cleanSummary
import com.afyzfur.afyzhub.domain.usecase.cleanTitle
import com.afyzfur.afyzhub.domain.usecase.fallbackTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 标题与总结的本地处理逻辑。
 *
 * 兜底规则在模型生成失败、未填 Key 或消息为空时生效，是最后保障。
 * 清洗逻辑则用于修正模型的实际输出——提示词里的"不要引号""不超过
 * 12 个字"都只是请求，不是保证。
 */
class GenerateTitleUseCaseTest {

    @Test
    fun `短消息原样作为标题`() {
        assertEquals(
            "今天天气怎么样",
            fallbackTitle("今天天气怎么样")
        )
    }

    @Test
    fun `超长消息截断并加省略号`() {
        val long = "这是一句很长的话".repeat(10)
        val title = fallbackTitle(long)

        assertEquals(21, title.length)
        assertEquals('…', title.last())
    }

    @Test
    fun `换行与多余空白折叠为单空格`() {
        assertEquals(
            "第一行 第二行",
            fallbackTitle("  第一行\n\n   第二行  ")
        )
    }

    @Test
    fun `空白消息给默认标题`() {
        assertEquals("新对话", fallbackTitle("   "))
    }

    @Test
    fun `清洗去掉各类引号`() {
        assertEquals("天气查询", cleanTitle("\"天气查询\""))
        assertEquals("天气查询", cleanTitle("「天气查询」"))
        assertEquals("天气查询", cleanTitle("“天气查询”"))
    }

    @Test
    fun `清洗去掉自作主张的前缀`() {
        assertEquals("天气查询", cleanTitle("标题：天气查询"))
        assertEquals("天气查询", cleanTitle("摘要: 天气查询"))
    }

    @Test
    fun `清洗去掉末尾标点`() {
        assertEquals("天气查询", cleanTitle("天气查询。"))
        assertEquals("如何配置", cleanTitle("如何配置？"))
    }

    @Test
    fun `清洗剥掉混进来的思考过程`() {
        // 有些模型即使在这种小任务上也会输出思考。
        // 标签用拼接构造，避免源码里的字面标签在工具链中被处理掉
        val open = "<" + "think" + ">"
        val close = "</" + "think" + ">"
        val raw = "${open}用户问的是天气，应该叫天气查询${close}天气查询"

        assertEquals("天气查询", cleanTitle(raw))
    }

    @Test
    fun `清洗硬性截断超长返回`() {
        // 提示词说了不超过 12 字，但模型不一定听
        val raw = "这是一个非常非常长的标题完全不符合要求需要被截断处理掉"
        val cleaned = cleanTitle(raw)

        assertEquals(21, cleaned.length)
        assertEquals('…', cleaned.last())
    }

    @Test
    fun `清洗把多行返回折成一行`() {
        val cleaned = cleanTitle("第一行\n第二行")

        assertFalse("标题只有一行，不该含换行", cleaned.contains('\n'))
    }

    @Test
    fun `总结允许比标题更长`() {
        val raw = "解释了如何在设置里配置中转地址并验证密钥是否有效"
        val cleaned = cleanSummary(raw)

        // 24 字，在总结的 40 字上限内，不该被截断
        assertEquals(raw, cleaned)
    }
}
