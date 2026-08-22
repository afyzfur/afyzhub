package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.util.markdown.InlineStyle
import com.afyzfur.afyzhub.util.markdown.MarkdownBlock
import com.afyzfur.afyzhub.util.markdown.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `解析标题级别`() {
        val blocks = MarkdownParser.parse("# 一级\n### 三级")
        val h1 = blocks[0] as MarkdownBlock.Heading
        val h3 = blocks[1] as MarkdownBlock.Heading
        assertEquals(1, h1.level)
        assertEquals("一级", h1.spans.single().text)
        assertEquals(3, h3.level)
    }

    @Test
    fun `井号后无空格不视为标题`() {
        val blocks = MarkdownParser.parse("#标签内容")
        assertTrue(blocks.single() is MarkdownBlock.Paragraph)
    }

    @Test
    fun `解析围栏代码块与语言`() {
        val blocks = MarkdownParser.parse("说明：\n```kotlin\nval a = 1\nprintln(a)\n```")
        val code = blocks.filterIsInstance<MarkdownBlock.CodeBlock>().single()
        assertEquals("kotlin", code.language)
        assertEquals("val a = 1\nprintln(a)", code.code)
        assertTrue(code.closed)
    }

    @Test
    fun `未闭合代码块仍按代码块处理`() {
        // 流式输出中间态：围栏只开了一半。
        val blocks = MarkdownParser.parse("```python\nprint(1)")
        val code = blocks.single() as MarkdownBlock.CodeBlock
        assertEquals("python", code.language)
        assertEquals("print(1)", code.code)
        assertFalse(code.closed)
    }

    @Test
    fun `代码块内的标记不被解析`() {
        val blocks = MarkdownParser.parse("```\n# 不是标题\n**不是粗体**\n```")
        val code = blocks.single() as MarkdownBlock.CodeBlock
        assertEquals("# 不是标题\n**不是粗体**", code.code)
    }

    @Test
    fun `解析行内加粗与代码`() {
        val spans = MarkdownParser.parseInline("这是**重点**和`code`")
        assertEquals("这是", spans[0].text)
        assertEquals(setOf(InlineStyle.BOLD), spans[1].styles)
        assertEquals("重点", spans[1].text)
        assertEquals(setOf(InlineStyle.CODE), spans[3].styles)
        assertEquals("code", spans[3].text)
    }

    @Test
    fun `未闭合的星号按普通文本处理`() {
        // 流式输出时可能只收到半个标记，不应整段变粗。
        val spans = MarkdownParser.parseInline("计算 2**3 和 **未闭合")
        assertTrue(spans.none { InlineStyle.BOLD in it.styles })
    }

    @Test
    fun `下划线不破坏标识符`() {
        val spans = MarkdownParser.parseInline("变量 user_name_id 保持原样")
        assertEquals("变量 user_name_id 保持原样", spans.joinToString("") { it.text })
        assertTrue(spans.none { InlineStyle.ITALIC in it.styles })
    }

    @Test
    fun `解析链接`() {
        val spans = MarkdownParser.parseInline("见 [文档](https://example.com) 说明")
        val link = spans.single { InlineStyle.LINK in it.styles }
        assertEquals("文档", link.text)
        assertEquals("https://example.com", link.url)
    }

    @Test
    fun `解析无序与有序列表`() {
        val blocks = MarkdownParser.parse("- 第一项\n- 第二项\n1. 步骤一")
        val items = blocks.filterIsInstance<MarkdownBlock.ListItem>()
        assertEquals(3, items.size)
        assertFalse(items[0].ordered)
        assertEquals("•", items[0].marker)
        assertTrue(items[2].ordered)
        assertEquals("1.", items[2].marker)
    }

    @Test
    fun `解析引用与分割线`() {
        val blocks = MarkdownParser.parse("> 引用内容\n\n---")
        assertEquals("引用内容", (blocks[0] as MarkdownBlock.Quote).spans.single().text)
        assertTrue(blocks[1] is MarkdownBlock.Divider)
    }

    @Test
    fun `连续文本行合并为一个段落`() {
        val blocks = MarkdownParser.parse("第一行\n第二行\n\n新段落")
        val paragraphs = blocks.filterIsInstance<MarkdownBlock.Paragraph>()
        assertEquals(2, paragraphs.size)
        assertEquals("第一行 第二行", paragraphs[0].spans.joinToString("") { it.text })
    }

    @Test
    fun `纯文本不产生额外块`() {
        val blocks = MarkdownParser.parse("就是一句普通的话")
        assertEquals(1, blocks.size)
        assertTrue(blocks.single() is MarkdownBlock.Paragraph)
    }

    @Test
    fun `空白输入返回空列表`() {
        assertTrue(MarkdownParser.parse("   ").isEmpty())
    }

    @Test
    fun `加粗内部的行内代码保留双重样式`() {
        val spans = MarkdownParser.parseInline("**注意 `flag` 值**")
        val codeSpan = spans.single { InlineStyle.CODE in it.styles }
        assertTrue(InlineStyle.BOLD in codeSpan.styles)
        assertEquals("flag", codeSpan.text)
    }
}
