package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.data.settings.MessageDisplayOptions
import com.afyzfur.afyzhub.domain.model.Message
import com.afyzfur.afyzhub.ui.components.MarkdownText
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens

/**
 * 消息块。
 *
 * 与改版前的差别（见 docs/ui-redesign.md 4.3）：
 * - 助手消息改为全宽无容器。原实现给它 340dp 宽度上限并铺 surfaceVariant，
 *   代码块和表格因此被挤在窄条里，长内容还要横向滚动
 * - 用户消息保留气泡，但收窄右下角以指示方向，不画三角尾巴
 * - 阴影与描边一律不用，层次靠 tonal 色阶
 *
 * 时间戳、token 用量等元信息属于阶段 6（依赖 Room 加列），此处暂不渲染。
 */
@Composable
fun MessageBlock(
    message: Message,
    displayOptions: MessageDisplayOptions,
    onRetry: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isFromUser) Alignment.End else Alignment.Start
    ) {
        when {
            message.isFailed -> FailedMessage(message = message, onRetry = onRetry)
            message.isFromUser -> UserMessage(message = message)
            else -> AssistantMessage(message = message)
        }

        // 失败消息已有错误提示与重试按钮，再加元信息只会更乱
        if (!message.isFailed && !message.isSending) {
            MessageMetaRow(
                message = message,
                options = displayOptions,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * 用户消息。右对齐气泡，宽度上限留出左侧空白以形成方向感。
 */
@Composable
private fun UserMessage(message: Message) {
    // 外层 MessageBlock 已负责对齐，此处只管气泡本身
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = AppShapeTokens.UserMessage,
        modifier = Modifier.widthIn(max = 300.dp)
    ) {
        Text(
            // 用户输入按原样显示，不解析 Markdown
            text = message.content,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }

    if (message.isSending) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "发送中",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 助手消息。全宽，无容器背景。
 *
 * 不铺底色是有意的：助手回复通常是本页最长的内容，铺色会让整屏变成大色块。
 * 用户消息有容器、助手消息没有，这个不对称本身就区分了双方，无需再加标识。
 */
@Composable
private fun AssistantMessage(message: Message) {
    MarkdownText(
        // 流式生成中的空回复先显示光标，避免出现空行
        text = message.content.ifEmpty { if (message.isSending) "▍" else "" },
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 失败消息。保留原文并提供重试，不静默丢弃。
 */
@Composable
private fun FailedMessage(
    message: Message,
    onRetry: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = AppShapeTokens.AssistantMessage,
        modifier = Modifier.widthIn(max = 320.dp)
    ) {
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = message.errorMessage ?: "发送失败",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
        TextButton(onClick = onRetry) {
            Text("重试")
        }
    }
}
