package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens

/**
 * 输入区。
 *
 * 与改版前的差别：原实现是 OutlinedTextField + 独立 FAB 并排，外层 Surface 带 8dp 阴影，
 * 是 Material 2 的语言。现在整体收进一个圆角容器，内部上下分层——
 * 上层文本输入，下层功能行。阴影去掉，靠色阶与页面背景区分。
 *
 * 容器铺满宽度、只圆上方两角、贴住屏幕底边。此前四周留 12dp 外边距做悬浮效果，
 * 但容器色（surfaceContainerHigh）与页面色（surfaceContainer）相近，
 * 四周露出的页面背景在视觉上成了一圈多余的浅色描边。铺满则没有这个问题。
 *
 * 功能行当前只放提供商标识与发送按钮。联网搜索、思考模式、附件等按钮
 * 需要对应功能落地后再加，先不放不可用的占位图标。
 */
@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    providerLabel: String,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val canSend = value.isNotBlank() && !isLoading

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = AppShapeTokens.InputContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        // 导航栏内边距加在容器内部，使容器背景延伸到屏幕底边，
        // 而内容不被系统手势区域压住
        Column(
            modifier = Modifier
                .padding(top = 4.dp)
                .navigationBarsPadding()
        ) {

            // 上层：文本输入。用 BasicTextField 而非 TextField，
            // 后者自带的 label / indicator / 内边距无法与容器化布局对齐
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = !isLoading,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = "输入消息",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            )

            // 下层：功能行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, bottom = 8.dp)
            ) {
                // 当前提供商暴露在此处，使"这条消息以何配置发出"一眼可见
                Text(
                    text = providerLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(modifier = Modifier.weight(1f))

                SendButton(
                    canSend = canSend,
                    isLoading = isLoading,
                    onSend = onSend
                )
            }
        }
    }
}

/**
 * 发送按钮。正圆，不可用时降低对比度而非隐藏，
 * 保持布局稳定并暗示"补全输入即可发送"。
 */
@Composable
private fun SendButton(
    canSend: Boolean,
    isLoading: Boolean,
    onSend: () -> Unit
) {
    val container =
        if (canSend) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHighest
    val content =
        if (canSend) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = container,
        shape = AppShapeTokens.CircleButton,
        modifier = Modifier.size(44.dp)
    ) {
        // 用 IconButton 承接点击，以获得涟漪反馈与无障碍语义。
        // 若改用 Modifier.clickable 需自行处理这两点
        IconButton(
            onClick = onSend,
            enabled = canSend
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = content,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = content,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
