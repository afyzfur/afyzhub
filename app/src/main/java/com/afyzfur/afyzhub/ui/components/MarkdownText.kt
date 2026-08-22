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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/**
 * 渲染 Markdown 文本。
 *
 * 解析结果按 [text] 缓存，流式输出时每次增量只重算一次。
 */
@Composable
fun MarkdownText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val blocks = remember(text) { MarkdownParser.parse(text) }

    if (blocks.isEmpty()) {
        Text(text = text, color = color, modifier = modifier)
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEach { block -> MarkdownBlockView(block, color) }
    }
}

@Composable
private fun MarkdownBlockView(block: MarkdownBlock, color: Color) {
    when (block) {
        is MarkdownBlock.Paragraph -> Text(
            text = block.spans.toAnnotatedString(),
            color = color,
            style = MaterialTheme.typography.bodyLarge
        )

        is MarkdownBlock.Heading -> Text(
            text = block.spans.toAnnotatedString(),
            color = color,
            style = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
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
            Text(
                text = block.spans.toAnnotatedString(),
                color = color,
                style = MaterialTheme.typography.bodyLarge
            )
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
            Text(
                text = block.spans.toAnnotatedString(),
                color = color.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic
            )
        }

        MarkdownBlock.Divider -> HorizontalDivider()
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

/** 把解析出的行内片段转成 Compose 可渲染的富文本。 */
@Composable
private fun List<InlineSpan>.toAnnotatedString(): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest

    return buildAnnotatedString {
        this@toAnnotatedString.forEach { span ->
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
            withStyle(style) { append(span.text) }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    manager?.setPrimaryClip(ClipData.newPlainText("code", text))
}
