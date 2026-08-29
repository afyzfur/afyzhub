package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.domain.model.ConversationItem
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens

/**
 * 会话长按后的操作表。
 *
 * 用 ModalBottomSheet 而非 DropdownMenu：操作有六项且带状态
 * （置顶与星标是开关），菜单在小屏上会顶到边缘，而底部表始终
 * 从下方展开、有足够宽度显示每项的当前状态。
 *
 * 重命名、改简介、移动分组这三项需要输入，各自弹自己的对话框而不是
 * 在表里内联编辑——表的高度会随键盘变化，内联输入会让布局跳动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationActionSheet(
    conversation: ConversationItem,
    /** 已存在的分组名，供移动时直接选 */
    existingGroups: List<String>,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleStar: () -> Unit,
    onRename: (String) -> Unit,
    onUpdateNote: (String) -> Unit,
    onMoveToGroup: (String) -> Unit,
    onDelete: () -> Unit
) {
    var dialog by remember { mutableStateOf<ActionDialog?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // 跳过半展开档位：默认起始高度只有屏幕一半，六项操作装不下，
        // 最后的删除会被切掉、必须手动上滑才看得到
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                // 内容仍可能超出可用高度（小屏、大字体），留一条滚动通道
                .verticalScroll(rememberScrollState())
                // 导航栏区域的额外留白，否则最后一项会贴在手势条上
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // 标题行显示会话名，确认操作对象——长按容易按错行
            Text(
                text = conversation.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            if (conversation.group.isNotEmpty()) {
                Text(
                    text = "分组：${conversation.group}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()

            ActionRow(
                icon = Icons.Default.KeyboardArrowUp,
                title = if (conversation.pinned) "取消置顶" else "置顶对话",
                onClick = {
                    onTogglePin()
                    onDismiss()
                }
            )
            ActionRow(
                icon = Icons.Default.Star,
                title = if (conversation.starred) "取消星标" else "标为星标",
                onClick = {
                    onToggleStar()
                    onDismiss()
                }
            )
            ActionRow(
                icon = Icons.Default.Edit,
                title = "重命名",
                onClick = { dialog = ActionDialog.Rename }
            )
            ActionRow(
                icon = Icons.Default.Create,
                title = if (conversation.note.isNullOrBlank()) "添加简介" else "修改简介",
                onClick = { dialog = ActionDialog.Note }
            )
            ActionRow(
                icon = Icons.Default.List,
                title = "移动到分组",
                onClick = { dialog = ActionDialog.Group }
            )

            HorizontalDivider()

            ActionRow(
                icon = Icons.Default.Delete,
                title = "删除对话",
                destructive = true,
                onClick = { dialog = ActionDialog.Delete }
            )
        }
    }

    when (dialog) {
        ActionDialog.Rename -> TextInputDialog(
            title = "重命名",
            initial = conversation.title,
            placeholder = "对话名称",
            onConfirm = {
                onRename(it)
                dialog = null
                onDismiss()
            },
            onDismiss = { dialog = null }
        )

        ActionDialog.Note -> TextInputDialog(
            title = "简介",
            initial = conversation.note ?: "",
            placeholder = "这个对话是关于什么的",
            // 允许清空：简介本来就是可选的
            allowEmpty = true,
            singleLine = false,
            onConfirm = {
                onUpdateNote(it)
                dialog = null
                onDismiss()
            },
            onDismiss = { dialog = null }
        )

        ActionDialog.Group -> GroupPickerDialog(
            current = conversation.group,
            existingGroups = existingGroups,
            onConfirm = {
                onMoveToGroup(it)
                dialog = null
                onDismiss()
            },
            onDismiss = { dialog = null }
        )

        ActionDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("删除对话") },
            text = { Text("确定要删除「${conversation.title}」吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        dialog = null
                        onDismiss()
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text("取消") }
            }
        )

        null -> Unit
    }
}

private enum class ActionDialog { Rename, Note, Group, Delete }

/**
 * 操作表里的一行。
 *
 * 点击区域铺满整行宽度，形状与圆角对齐——涟漪要跟着圆角走，
 * 否则会在圆角处露出方角。
 */
@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        // 用透明而非 surface：底部表的容器色是 surfaceContainerLow，
        // 每行再铺一层 surface 就成了两种深浅，末尾没有行覆盖的那段
        // 会露出底色，看着像多了一条颜色不同的横带
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = tint
            )
        }
    }
}
