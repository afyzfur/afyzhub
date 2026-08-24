package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.data.settings.AvatarMode
import com.afyzfur.afyzhub.data.settings.BubbleStyle
import com.afyzfur.afyzhub.data.settings.ChatAppearance
import com.afyzfur.afyzhub.data.settings.MessageDisplayOptions
import com.afyzfur.afyzhub.domain.model.Message
import com.afyzfur.afyzhub.ui.components.LocalImage
import com.afyzfur.afyzhub.ui.components.ModelIcon
import com.afyzfur.afyzhub.ui.components.MarkdownText
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens

/**
 * 单条消息。
 *
 * 气泡与否由 [ChatAppearance] 决定，用户与助手各自可选。
 * 无气泡时占满宽度，代码块与表格能完整展开；有气泡时宽度随内容
 * 并限制上限，避免长行贴满屏幕。
 *
 * 头像位在开启时占据固定宽度，两侧消息各自靠内对齐，
 * 使同一侧的消息主体保持竖直对齐而不因有无头像错开。
 */
@Composable
fun MessageBlock(
    message: Message,
    displayOptions: MessageDisplayOptions,
    appearance: ChatAppearance,
    providerLabel: String,
    onRetry: () -> Unit = {}
) {
    val fromUser = message.isFromUser
    val style = if (fromUser) appearance.userBubble else appearance.assistantBubble

    Row(
        modifier = Modifier.fillMaxWidth(),
        // 用户消息整体靠右，助手靠左
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
        // 助手头像在消息左侧
        if (appearance.showAvatars && !fromUser) {
            MessageAvatar(
                appearance = appearance,
                fromUser = false,
                providerLabel = providerLabel,
                modelName = message.model
            )
            Spacer(Modifier.size(8.dp))
        }

        Column(
            horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
            // 无气泡的助手消息要占满剩余宽度，其余情况随内容
            modifier = if (style == BubbleStyle.PLAIN && !fromUser) {
                Modifier.weight(1f)
            } else {
                Modifier.widthIn(max = 320.dp)
            }
        ) {
            when {
                message.isFailed -> FailedMessage(message = message, onRetry = onRetry)
                else -> MessageBody(message = message, style = style, fromUser = fromUser)
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

        // 用户头像在消息右侧
        if (appearance.showAvatars && fromUser) {
            Spacer(Modifier.size(8.dp))
            MessageAvatar(
                appearance = appearance,
                fromUser = true,
                providerLabel = providerLabel,
                modelName = message.model
            )
        }
    }
}

/**
 * 消息正文。
 *
 * 用户消息按原样显示不解析 Markdown——用户输入的 `*` 之类符号
 * 通常是字面意思，解析反而会吞掉字符。
 */
@Composable
private fun MessageBody(
    message: Message,
    style: BubbleStyle,
    fromUser: Boolean
) {
    val content: @Composable () -> Unit = {
        if (fromUser) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = if (style == BubbleStyle.BUBBLE) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        } else {
            MarkdownText(
                text = message.content,
                color = if (style == BubbleStyle.BUBBLE) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }

    when (style) {
        BubbleStyle.BUBBLE -> Surface(
            color = if (fromUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            // 气泡在靠内的下角收窄，指示消息来源方向
            shape = if (fromUser) {
                AppShapeTokens.UserMessage
            } else {
                AppShapeTokens.AssistantMessage
            }
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                content()
            }
        }

        BubbleStyle.PLAIN -> Box(modifier = Modifier.fillMaxWidth()) {
            content()
        }
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
 * 头像。
 *
 * 自定义图片缺失时回落到内置图标而非留空——留空会让头像列出现空洞，
 * 破坏消息的竖直对齐。
 */
@Composable
private fun MessageAvatar(
    appearance: ChatAppearance,
    fromUser: Boolean,
    providerLabel: String,
    /** 该条消息使用的模型名，用于匹配厂商图标；v3 之前的消息为 null */
    modelName: String?
) {
    val path = if (fromUser) appearance.userAvatarPath else appearance.assistantAvatarPath
    val useCustom = appearance.avatarMode == AvatarMode.CUSTOM && !path.isNullOrBlank()

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
    ) {
        if (useCustom) {
            LocalImage(
                path = path!!,
                version = appearance.imageVersion,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        } else {
            Surface(
                color = if (fromUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
                shape = CircleShape,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (fromUser) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        // 优先用消息自身记录的模型名——历史消息可能来自
                        // 与当前配置不同的模型。缺失时退回提供商名，
                        // 两者都匹配不到时 ModelIcon 内部会显示首字母
                        ModelIcon(
                            modelName = modelName ?: providerLabel,
                            size = 24.dp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 失败消息。
 *
 * 始终用容器承载：错误内容需要视觉上被隔离出来，
 * 无容器的错误文本容易被当成正常回复。
 */
@Composable
private fun FailedMessage(
    message: Message,
    onRetry: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = AppShapeTokens.AssistantMessage
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
