package com.afyzfur.afyzhub.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afyzfur.afyzhub.util.markdown.InlineSpan
import com.afyzfur.afyzhub.util.markdown.InlineStyle
import com.afyzfur.afyzhub.util.markdown.MarkdownBlock
import com.afyzfur.afyzhub.util.markdown.MarkdownParser
import kotlinx.coroutines.delay

/**
 * 渲染 Markdown 文本。
 *
 * 解析结果按 [text] 缓存，流式输出时每次增量只重算一次。
 */
@Composable
fun MarkdownText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    /**
     * 文档模式。用于更新日志这类长文：标题整体再放大一档，
     * 二级标题（版本号）之上加大间距并画分隔线。
     *
     * 聊天气泡里不开：那里的标题只是段落强调，放大会挤掉正文。
     */
    documentMode: Boolean = false,
    /** 链接点击回调。不传时链接仅渲染样式，不可点。 */
    onLinkClick: ((String) -> Unit)? = null
) {
    // 流式时 text 高频变化, 逐次全量解析会让长文频繁重建
    // 整个块列表。以 150ms 防抖窗口对齐解析: 连续变化时
    // 只在稳定 150ms 后解析一次(结束即最终态), 期间渲染
    // 旧的块结构, 视觉连续无闪烁。
    var parsedSource by remember { mutableStateOf(text) }
    LaunchedEffect(text) {
        delay(150)
        if (parsedSource != text) parsedSource = text
    }
    val blocks = remember(parsedSource) { MarkdownParser.parse(parsedSource) }

    if (blocks.isEmpty()) {
        Text(text = text, color = color, modifier = modifier)
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEachIndexed { index, block ->
            // 每个版本之间拉开距离并分隔。首个不加，
            // 否则页面顶部会多出一段空白
            if (documentMode &&
                index > 0 &&
                block is MarkdownBlock.Heading &&
                block.level == 2
            ) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(Modifier.height(8.dp))
            }
            MarkdownBlockView(block, color, documentMode, onLinkClick)
        }
    }
}

@Composable
private fun MarkdownBlockView(
    block: MarkdownBlock,
    color: Color,
    documentMode: Boolean = false,
    onLinkClick: ((String) -> Unit)? = null
) {
    // 所有块共用的主题色: 一次取用, 各分支直接传参。
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    when (block) {
        is MarkdownBlock.Paragraph -> {
            // 流式时文本高频变化, 构建标注串开销大; 用 remember 缓存,
            // 同一段文本(以及主题色)不变时直接复用上次结果。
            val annotated = remember(block.spans, linkColor, codeBackground) {
                block.spans.buildAnnotated(linkColor, codeBackground)
            }
            if (onLinkClick != null) {
                LinkAwareText(
                    text = annotated,
                    color = color,
                    style = MaterialTheme.typography.bodyLarge,
                    onLinkClick = onLinkClick
                )
            } else {
                Text(
                    text = annotated,
                    color = color,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        is MarkdownBlock.Heading -> if (onLinkClick != null) {
            LinkAwareText(
                text = block.spans.buildAnnotated(linkColor, codeBackground),
                color = color,
                style = if (documentMode) {
                    when (block.level) {
                        1 -> MaterialTheme.typography.displaySmall
                        2 -> MaterialTheme.typography.headlineLarge
                        3 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }
                } else {
                    when (block.level) {
                        1 -> MaterialTheme.typography.headlineMedium
                        2 -> MaterialTheme.typography.headlineSmall
                        3 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }
                },
                onLinkClick = onLinkClick
            )
        } else Text(
            text = block.spans.buildAnnotated(linkColor, codeBackground),
            color = color,
            // 各级差距拉开：原先 titleMedium 与 titleSmall 只差 2sp，
            // 二级与三级标题几乎看不出层级，更新日志里的版本号
            // 与其下的分类标题混成一片。
            //
            // 文档模式再上调一档，让版本号明显区别于其下的"新增/修复"
            // 与条目正文——扫一眼就能定位到版本边界
            style = if (documentMode) {
                when (block.level) {
                    1 -> MaterialTheme.typography.displaySmall
                    2 -> MaterialTheme.typography.headlineLarge
                    3 -> MaterialTheme.typography.titleLarge
                    else -> MaterialTheme.typography.titleMedium
                }
            } else {
                when (block.level) {
                    1 -> MaterialTheme.typography.headlineMedium
                    2 -> MaterialTheme.typography.headlineSmall
                    3 -> MaterialTheme.typography.titleLarge
                    else -> MaterialTheme.typography.titleMedium
                }
            },
            fontWeight = FontWeight.Bold
        )

        is MarkdownBlock.CodeBlock -> CodeBlockView(block)

        is MarkdownBlock.ListItem -> Row(
            modifier = Modifier.padding(start = (block.indentLevel * 12).dp)
        ) {
            Text(
                text = "${block.marker} ",
                color = color,
                style = MaterialTheme.typography.bodyLarge
            )
            if (onLinkClick != null) {
                LinkAwareText(
                    text = block.spans.buildAnnotated(linkColor, codeBackground),
                    color = color,
                    style = MaterialTheme.typography.bodyLarge,
                    onLinkClick = onLinkClick
                )
            } else {
                Text(
                    text = block.spans.buildAnnotated(linkColor, codeBackground),
                    color = color,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        is MarkdownBlock.Quote -> Row {
            // 用一条竖线表示引用层级。
            Surface(
                color = color.copy(alpha = 0.4f),
                modifier = Modifier
                    .width(3.dp)
                    .heightIn(min = 20.dp)
            ) {}
            Spacer(modifier = Modifier.width(8.dp))
            if (onLinkClick != null) {
                LinkAwareText(
                    text = block.spans.buildAnnotated(linkColor, codeBackground),
                    color = color.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    onLinkClick = onLinkClick
                )
            } else {
                Text(
                    text = block.spans.buildAnnotated(linkColor, codeBackground),
                    color = color.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic
                )
            }
        }

        // 上下留白：分隔线的作用是划分区块，紧贴文字反而像下划线。
        // 更新日志里它分隔各个版本，需要明显的呼吸空间
        MarkdownBlock.Divider -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun CodeBlockView(block: MarkdownBlock.CodeBlock) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = block.language ?: "code",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { copyToClipboard(context, block.code) }) {
                    Text("复制", style = MaterialTheme.typography.labelSmall)
                }
            }

            // 代码不折行，横向滚动查看长行。
            Text(
                text = block.code,
                style = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            )
        }
    }
}


/**
 * 支持链接点击的 Text。
 *
 * 手势用 detectTapGestures 且只声明 onTap：未声明的长按不消费，
 * 外层（如消息长按菜单的 combinedClickable）仍能收到。
 */
@Composable
private fun LinkAwareText(
    text: AnnotatedString,
    color: Color,
    style: androidx.compose.ui.text.TextStyle,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    Text(
        text = text,
        color = color,
        style = style,
        onTextLayout = { layout = it },
        modifier = modifier.pointerInput(text, onLinkClick) {
            detectTapGestures { offset ->
                val result = layout ?: return@detectTapGestures
                val position = result.getOffsetForPosition(offset)
                val hit = text.getStringAnnotations(position, position)
                    .firstOrNull { it.tag == URL_TAG }
                hit?.let { onLinkClick(it.item) }
            }
        }
    )
}

/**
 * 把解析出的行内片段转成 Compose 可渲染的富文本。
 *
 * 链接用 pushStringAnnotation 标记 URL 区间，点击位置由
 * [LinkAwareText] 的手势检测反查注释得到。不用 LinkAnnotation：
 * 该 API 在不同 Compose 小版本间签名变动过，标注法自 1.0 起稳定。
 */
private fun List<InlineSpan>.buildAnnotated(linkColor: Color, codeBackground: Color): AnnotatedString {

    return buildAnnotatedString {
        this@buildAnnotated.forEach { span ->
            val style = SpanStyle(
                fontWeight = if (InlineStyle.BOLD in span.styles) FontWeight.Bold else null,
                fontStyle = if (InlineStyle.ITALIC in span.styles) FontStyle.Italic else null,
                fontFamily = if (InlineStyle.CODE in span.styles) FontFamily.Monospace else null,
                background = if (InlineStyle.CODE in span.styles) codeBackground else Color.Unspecified,
                color = if (InlineStyle.LINK in span.styles) linkColor else Color.Unspecified,
                textDecoration = when {
                    InlineStyle.STRIKETHROUGH in span.styles -> TextDecoration.LineThrough
                    InlineStyle.LINK in span.styles -> TextDecoration.Underline
                    else -> null
                }
            )
            if (span.url != null) {
                pushStringAnnotation(tag = URL_TAG, annotation = span.url)
                withStyle(style) { append(span.text) }
                pop()
            } else {
                withStyle(style) { append(span.text) }
            }
        }
    }
}

/** 链接区间注释的 tag */
private const val URL_TAG = "URL"

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    manager?.setPrimaryClip(ClipData.newPlainText("code", text))
}
