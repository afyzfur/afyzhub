package com.afyzfur.afyzhub.util.markdown

/**
 * 轻量 Markdown 解析器，覆盖聊天场景常见语法。
 *
 * 设计取舍：
 * - 只支持模型回复里高频出现的语法，不追求完整 CommonMark；
 * - 容忍未闭合的围栏代码块，因为流式输出中间态必然出现；
 * - 纯 Kotlin 实现，不依赖 Android，便于单元测试。
 */
object MarkdownParser {

    private const val FENCE = "```"

    fun parse(source: String): List<MarkdownBlock> {
        if (source.isBlank()) return emptyList()

        val blocks = mutableListOf<MarkdownBlock>()
        val lines = source.replace("\r\n", "\n").split("\n")
        val paragraph = mutableListOf<String>()

        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()

            if (trimmed.startsWith(FENCE)) {
                flushParagraph(paragraph, blocks)
                index = readCodeBlock(lines, index, blocks)
                continue
            }

            when {
                trimmed.isEmpty() -> flushParagraph(paragraph, blocks)

                isDivider(trimmed) -> {
                    flushParagraph(paragraph, blocks)
                    blocks += MarkdownBlock.Divider
                }

                headingLevel(trimmed) > 0 -> {
                    flushParagraph(paragraph, blocks)
                    val level = headingLevel(trimmed)
                    blocks += MarkdownBlock.Heading(
                        level = level,
                        spans = parseInline(trimmed.drop(level).trim())
                    )
                }

                trimmed.startsWith("> ") || trimmed == ">" -> {
                    flushParagraph(paragraph, blocks)
                    blocks += MarkdownBlock.Quote(parseInline(trimmed.removePrefix(">").trim()))
                }

                else -> {
                    val item = parseListItem(line)
                    if (item != null) {
                        flushParagraph(paragraph, blocks)
                        blocks += item
                    } else {
                        paragraph += trimmed
                    }
                }
            }
            index++
        }

        flushParagraph(paragraph, blocks)
        return blocks
    }

    /** 读取围栏代码块，返回下一行的下标。 */
    private fun readCodeBlock(
        lines: List<String>,
        startIndex: Int,
        blocks: MutableList<MarkdownBlock>
    ): Int {
        val language = lines[startIndex].trim().removePrefix(FENCE).trim().ifBlank { null }
        val code = mutableListOf<String>()
        var index = startIndex + 1
        var closed = false

        while (index < lines.size) {
            if (lines[index].trim().startsWith(FENCE)) {
                closed = true
                index++
                break
            }
            code += lines[index]
            index++
        }

        blocks += MarkdownBlock.CodeBlock(
            language = language,
            code = code.joinToString("\n"),
            closed = closed
        )
        return index
    }

    private fun flushParagraph(buffer: MutableList<String>, blocks: MutableList<MarkdownBlock>) {
        if (buffer.isEmpty()) return
        // 段落内的换行按 Markdown 语义折叠为空格。
        val text = buffer.joinToString(" ").trim()
        buffer.clear()
        if (text.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(parseInline(text))
        }
    }

    private fun isDivider(trimmed: String): Boolean =
        trimmed.length >= 3 && (
            trimmed.all { it == '-' } || trimmed.all { it == '*' } || trimmed.all { it == '_' }
            )

    private fun headingLevel(trimmed: String): Int {
        val hashes = trimmed.takeWhile { it == '#' }.length
        if (hashes !in 1..6) return 0
        // 必须有空格分隔，避免把 "#tag" 当成标题。
        return if (trimmed.length > hashes && trimmed[hashes] == ' ') hashes else 0
    }

    private fun parseListItem(rawLine: String): MarkdownBlock.ListItem? {
        val indent = rawLine.takeWhile { it == ' ' }.length
        val trimmed = rawLine.trim()

        // 无序列表：- * +
        if (trimmed.length >= 2 && trimmed[0] in "-*+" && trimmed[1] == ' ') {
            return MarkdownBlock.ListItem(
                spans = parseInline(trimmed.drop(2).trim()),
                ordered = false,
                marker = "•",
                indentLevel = indent / 2
            )
        }

        // 有序列表：1. 或 1)
        val digits = trimmed.takeWhile { it.isDigit() }
        if (digits.isNotEmpty() && trimmed.length > digits.length + 1) {
            val separator = trimmed[digits.length]
            if ((separator == '.' || separator == ')') && trimmed[digits.length + 1] == ' ') {
                return MarkdownBlock.ListItem(
                    spans = parseInline(trimmed.drop(digits.length + 2).trim()),
                    ordered = true,
                    marker = "$digits.",
                    indentLevel = indent / 2
                )
            }
        }

        return null
    }

    /**
     * 解析行内样式。
     *
     * 单趟扫描，遇到未闭合的标记就当作普通字符，避免把
     * 数学表达式或未完成的流式文本错误地整段变样式。
     */
    fun parseInline(text: String): List<InlineSpan> {
        if (text.isEmpty()) return emptyList()

        val spans = mutableListOf<InlineSpan>()
        val plain = StringBuilder()
        var i = 0

        fun flushPlain() {
            if (plain.isNotEmpty()) {
                spans += InlineSpan(plain.toString())
                plain.clear()
            }
        }

        while (i < text.length) {
            // 行内代码优先，其内部不再解析其他样式。
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    flushPlain()
                    spans += InlineSpan(text.substring(i + 1, end), setOf(InlineStyle.CODE))
                    i = end + 1
                    continue
                }
            }

            // 链接 [文本](地址)
            if (text[i] == '[') {
                val link = matchLink(text, i)
                if (link != null) {
                    flushPlain()
                    spans += InlineSpan(link.label, setOf(InlineStyle.LINK), link.url)
                    i = link.endExclusive
                    continue
                }
            }

            val marker = matchEmphasisMarker(text, i)
            if (marker != null) {
                val end = text.indexOf(marker.token, i + marker.token.length)
                if (end > i) {
                    val inner = text.substring(i + marker.token.length, end)
                    if (inner.isNotEmpty()) {
                        flushPlain()
                        // 强调内部可能还有行内代码或链接，递归处理。
                        parseInline(inner).forEach { child ->
                            spans += child.copy(styles = child.styles + marker.style)
                        }
                        i = end + marker.token.length
                        continue
                    }
                }
            }

            plain.append(text[i])
            i++
        }

        flushPlain()
        return spans
    }

    private data class EmphasisMarker(val token: String, val style: InlineStyle)

    private fun matchEmphasisMarker(text: String, index: Int): EmphasisMarker? = when {
        text.startsWith("***", index) -> null // 三重标记不常见，按普通文本处理
        text.startsWith("**", index) ->
            EmphasisMarker("**", InlineStyle.BOLD).takeUnless { isArithmetic(text, index, 2) }
        text.startsWith("~~", index) -> EmphasisMarker("~~", InlineStyle.STRIKETHROUGH)
        text.startsWith("*", index) ->
            EmphasisMarker("*", InlineStyle.ITALIC).takeUnless { isArithmetic(text, index, 1) }
        // 下划线斜体要求左侧非单词字符，避免误伤 snake_case 标识符。
        text.startsWith("_", index) && isStandaloneUnderscore(text, index) ->
            EmphasisMarker("_", InlineStyle.ITALIC)
        else -> null
    }

    /**
     * 判断星号是否属于算式而非强调标记。
     *
     * 形如 `2**3`、`a * b` 中的星号两侧是数字或空白，
     * 若按强调解析会把中间整段错误加粗。
     */
    private fun isArithmetic(text: String, index: Int, tokenLength: Int): Boolean {
        val before = text.getOrNull(index - 1)
        val after = text.getOrNull(index + tokenLength)
        // 前一个字符是数字时几乎必然是乘方或乘法。
        if (before != null && before.isDigit()) return true
        // 标记紧跟空白时也不构成强调开头（Markdown 要求紧贴内容）。
        return after == null || after.isWhitespace()
    }

    private fun isStandaloneUnderscore(text: String, index: Int): Boolean {
        val before = text.getOrNull(index - 1)
        return before == null || !before.isLetterOrDigit()
    }

    private data class LinkMatch(val label: String, val url: String, val endExclusive: Int)

    private fun matchLink(text: String, start: Int): LinkMatch? {
        val labelEnd = text.indexOf(']', start + 1)
        if (labelEnd < 0) return null
        if (text.getOrNull(labelEnd + 1) != '(') return null
        val urlEnd = text.indexOf(')', labelEnd + 2)
        if (urlEnd < 0) return null

        val label = text.substring(start + 1, labelEnd)
        val url = text.substring(labelEnd + 2, urlEnd).trim()
        if (label.isEmpty() || url.isEmpty()) return null

        return LinkMatch(label, url, urlEnd + 1)
    }
}
