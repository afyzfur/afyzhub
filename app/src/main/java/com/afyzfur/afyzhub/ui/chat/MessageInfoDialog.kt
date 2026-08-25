package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.afyzfur.afyzhub.domain.model.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 消息详情。
 *
 * 只展示数据库里确实有的字段。token 与耗时是 v3 之后才记录的，
 * 早期消息为 null——此时整行不显示，而不是显示 0，
 * 后者会被误读为"用了 0 个 token"。
 */
@Composable
fun MessageInfoDialog(
    message: Message,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        title = { Text("消息信息") },
        text = {
            Column {
                InfoRow("角色", if (message.isFromUser) "用户" else "助手")
                InfoRow("时间", formatTime(message.createdAt))
                InfoRow("字数", "${message.content.length}")
                message.model?.let { InfoRow("模型", it) }
                message.promptTokens?.let { InfoRow("输入 token", "$it") }
                message.completionTokens?.let { InfoRow("输出 token", "$it") }
                message.latencyMs?.let { InfoRow("耗时", formatLatency(it)) }
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))

/** 超过一秒改用秒，毫秒数值在这个量级上不便阅读 */
private fun formatLatency(millis: Long): String =
    if (millis >= 1000) {
        String.format(Locale.getDefault(), "%.1f s", millis / 1000.0)
    } else {
        "$millis ms"
    }
