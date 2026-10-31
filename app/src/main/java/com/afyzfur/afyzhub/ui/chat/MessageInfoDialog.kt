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
            TextButton(onClick = onDismiss) { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_6c14bd7f6f9e)) }
        },
        title = { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_7cd1a72b10ac)) },
        text = {
            Column {
                InfoRow(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_6b26695e4dce), if (message.isFromUser) com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_9ba763ea3423) else com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_9b0519a157ee))
                InfoRow(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_89b4aa6364ce), formatTime(message.createdAt))
                InfoRow(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_10f48e5a005e), "${message.content.length}")
                message.model?.let { InfoRow(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_98fd0cbd9c31), it) }
                message.promptTokens?.let { InfoRow(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_0f2097438931), "$it") }
                message.completionTokens?.let { InfoRow(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_a182778a934d), "$it") }
                message.latencyMs?.let { InfoRow(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_a9704e1997b9), formatLatency(it)) }
                message.cachedTokens?.takeIf { it > 0 }?.let {
                    val pct = message.promptTokens?.takeIf { p -> p > 0 }
                        ?.let { p -> (it * 100 + p / 2) / p }
                    InfoRow(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_6b6d3e87d835), if (pct != null) "$it ($pct%)" else "$it")
                }
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
