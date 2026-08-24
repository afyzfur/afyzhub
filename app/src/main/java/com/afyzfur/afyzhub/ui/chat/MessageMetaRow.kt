package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.data.settings.MessageDisplayOptions
import com.afyzfur.afyzhub.domain.model.Message
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 消息元信息行。
 *
 * 由 [MessageDisplayOptions] 决定显示哪些项，数据缺失的项自动省略——
 * 历史消息（v0.2.0 之前）没有 token 与耗时，不应显示为 0。
 *
 * 所有项排在同一行并允许折行，而不是参考对象那样占三行。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageMetaRow(
    message: Message,
    options: MessageDisplayOptions,
    modifier: Modifier = Modifier
) {
    val parts = remember(message, options) { buildMetaParts(message, options) }
    if (parts.isEmpty()) return

    FlowRow(
        // 用户消息靠右，与其气泡对齐；助手消息全宽靠左
        horizontalArrangement = if (message.isFromUser) {
            Arrangement.spacedBy(12.dp, Alignment.End)
        } else {
            Arrangement.spacedBy(12.dp)
        },
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        parts.forEach { part ->
            Text(
                text = part,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 按开关与数据可用性组装要显示的文本片段。
 *
 * 抽成纯函数以便单测覆盖——这里的分支组合较多，
 * 而"数据缺失时不显示"这条规则容易在改动中被破坏。
 */
internal fun buildMetaParts(
    message: Message,
    options: MessageDisplayOptions
): List<String> {
    val parts = mutableListOf<String>()

    if (options.showTimestamp) {
        parts += formatTimestamp(message.createdAt)
    }

    // 模型名只对助手回复有意义，用户消息不显示
    if (options.showModelName && !message.isFromUser) {
        message.model?.let { parts += it }
    }

    if (options.showTokenUsage) {
        val input = message.promptTokens
        val output = message.completionTokens
        if (input != null || output != null) {
            parts += buildString {
                if (input != null) append("↑$input")
                if (input != null && output != null) append(" ")
                if (output != null) append("↓$output")
            }
        }
    }

    if (options.showSpeed) {
        message.tokensPerSecond?.let {
            parts += "${"%.1f".format(it)} tok/s"
        }
    }

    if (options.showLatency) {
        message.latencyMs?.let { parts += formatLatency(it) }
    }

    return parts
}

/** 同日只显示时分，跨日补上日期 */
private fun formatTimestamp(millis: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = millis }

    val sameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

    val pattern = if (sameDay) "HH:mm" else "MM-dd HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
}

/** 秒以下保留一位小数，超过一分钟改用分秒 */
private fun formatLatency(millis: Long): String = when {
    millis < 1000 -> "${millis}ms"
    millis < 60_000 -> "${"%.1f".format(millis / 1000.0)}s"
    else -> "${millis / 60_000}m${(millis % 60_000) / 1000}s"
}
