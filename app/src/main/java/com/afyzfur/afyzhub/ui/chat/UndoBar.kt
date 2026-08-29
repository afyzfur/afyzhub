package com.afyzfur.afyzhub.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens

/**
 * 删除后的撤回提示。
 *
 * 出现在输入栏上方而非用 Snackbar：撤回和刚做的那个操作在同一处
 * 视线范围内，而 Snackbar 会盖住消息列表底部，恰好是刚被删掉的
 * 那段内容所在的位置——用户想确认删对了没有反而被挡住。
 *
 * 自动消失由调用方计时，这里只负责显示与动画。
 */
@Composable
fun UndoBar(
    removal: UndoableRemoval?,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = removal != null,
        // 从下方滑入：它是紧接着输入栏出现的，方向上与输入栏连成一体
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        // removal 在退出动画期间会变成 null，用最后一次的非空值渲染
        val count = removal?.count ?: 0
        Surface(
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shape = AppShapeTokens.SettingsGroup,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Text(
                    text = if (count > 1) "已移除 $count 条消息" else "已移除 1 条消息",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onUndo) {
                    Text(
                        text = "撤回",
                        color = MaterialTheme.colorScheme.inversePrimary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭提示",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
