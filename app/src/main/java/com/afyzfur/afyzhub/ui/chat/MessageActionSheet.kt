package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 消息长按后的操作表。
 *
 * 用底部弹出而非贴在消息旁的下拉菜单：消息可能很长或靠近屏幕边缘，
 * 锚定弹出会出现位置计算与遮挡问题，底部表的位置恒定。
 *
 * 用户与助手的可用操作不同，由 [fromUser] 区分。朗读、修改记忆、
 * 插入总结、创建分支、多选这几项依赖尚未落地的能力，暂不提供——
 * 放不可用的入口比不放更让人困惑。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionSheet(
    fromUser: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    /** 仅助手消息：换一个回答 */
    onRegenerate: () -> Unit = {},
    /** 仅用户消息：把内容填回输入框重新发送 */
    onEditResend: () -> Unit = {},
    /** 仅用户消息：删除这条及其之后的全部消息 */
    onRollback: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
        ) {
            ActionRow(
                // 可用图标清单里没有 copy，List 的多层视觉与"副本"意象接近；
                // Create 是铅笔，留给「编辑并重发」
                icon = Icons.AutoMirrored.Filled.List,
                text = "复制消息",
                onClick = onCopy
            )

            if (fromUser) {
                ActionRow(
                    icon = Icons.Default.Create,
                    text = "编辑并重发",
                    onClick = onEditResend
                )
                ActionRow(
                    icon = Icons.Default.Delete,
                    text = "回滚到此处",
                    onClick = onRollback
                )
            } else {
                ActionRow(
                    icon = Icons.Default.Refresh,
                    text = "重新生成",
                    onClick = onRegenerate
                )
            }

            ActionRow(
                icon = Icons.Default.Delete,
                text = "删除",
                onClick = onDelete,
                // 删除不可撤销，用 error 色与其他项区分
                tint = MaterialTheme.colorScheme.error
            )

            ActionRow(
                icon = Icons.Default.Info,
                text = "信息",
                onClick = onInfo
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.size(20.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = tint
        )
    }
}
