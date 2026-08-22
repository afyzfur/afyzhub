package com.afyzfur.afyzhub.util.markdown

/** 行内样式。可叠加，例如同时加粗与代码。 */
enum class InlineStyle {
    BOLD,
    ITALIC,
    CODE,
    STRIKETHROUGH,
    LINK
}

/**
 * 一段带样式的行内文本。
 *
 * 保持为纯数据，便于在 JVM 单元测试中断言，Compose 层再转为 AnnotatedString。
 */
data class InlineSpan(
    val text: String,
    val styles: Set<InlineStyle> = emptySet(),
    /** 链接目标，仅当样式包含 [InlineStyle.LINK] 时有值。 */
    val url: String? = null
)

/** 块级元素。 */
sealed interface MarkdownBlock {

    /** 普通段落，内部可含行内样式。 */
    data class Paragraph(val spans: List<InlineSpan>) : MarkdownBlock

    /** 标题，[level] 取 1..6。 */
    data class Heading(val level: Int, val spans: List<InlineSpan>) : MarkdownBlock

    /**
     * 围栏代码块。
     *
     * [closed] 为 false 表示源文本中的围栏尚未闭合，流式输出过程中很常见，
     * 渲染时仍按代码块显示，避免内容突然改变样式。
     */
    data class CodeBlock(
        val language: String?,
        val code: String,
        val closed: Boolean = true
    ) : MarkdownBlock

    /** 列表项。[ordered] 为 true 时 [marker] 是序号文本。 */
    data class ListItem(
        val spans: List<InlineSpan>,
        val ordered: Boolean,
        val marker: String,
        val indentLevel: Int = 0
    ) : MarkdownBlock

    /** 引用块。 */
    data class Quote(val spans: List<InlineSpan>) : MarkdownBlock

    /** 分割线。 */
    data object Divider : MarkdownBlock
}
