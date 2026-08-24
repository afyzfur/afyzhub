package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.domain.model.ConversationItem
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens

/**
 * 抽屉内容：分组会话列表 + 底部入口。
 *
 * 阶段 4 相比阶段 2 的差别：
 * - 会话按时间分组，组标题用强调色（见 ConversationGrouping.kt）
 * - 列表项加末条消息摘要。参考对象仅显示标题，相邻的相似会话无法区分
 * - 底部增加当前助手行。目前助手体系尚未落地，暂显示当前模型
 */
@Composable
fun ConversationDrawer(
    conversations: List<ConversationItem>,
    currentConversationId: Long?,
    modelLabel: String,
    onConversationClick: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 分组结果随列表变化重算，不必每次重组都做
    val grouped = remember(conversations) { groupConversations(conversations) }

    Column(modifier = modifier.fillMaxSize()) {

        Text(
            text = "AfyzHub",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 24.dp, top = 28.dp, bottom = 16.dp)
        )

        DrawerActionRow(
            text = "新建对话",
            onClick = onNewConversation,
            leading = {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            grouped.forEach { (group, items) ->
                item(key = "header-${group.name}") {
                    Text(
                        text = group.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            start = 28.dp,
                            top = 16.dp,
                            bottom = 6.dp
                        )
                    )
                }

                items(
                    items = items,
                    key = { it.id }
                ) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        selected = conversation.id == currentConversationId,
                        onClick = { onConversationClick(conversation.id) },
                        onDelete = { onDeleteConversation(conversation.id) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 底部两行，均带文字标签。参考对象用五个无标签圆形图标，可发现性差
        DrawerActionRow(
            text = modelLabel,
            onClick = onSettingsClick,
            leading = {
                // 助手体系落地前先用首字母占位，避免引入无意义的通用图标
                AssistantAvatar(label = modelLabel)
            }
        )

        DrawerActionRow(
            text = "设置",
            onClick = onSettingsClick,
            leading = {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

        Spacer(Modifier.height(16.dp))
    }
}

/** 助手标识占位：取模型名首字母，圆形底色 */
@Composable
private fun AssistantAvatar(label: String) {
    Row(
        modifier = Modifier
            .size(24.dp)
            .clip(AppShapeTokens.CircleButton)
            .background(MaterialTheme.colorScheme.primaryContainer),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.take(1).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/** 抽屉内的操作行 */
@Composable
private fun DrawerActionRow(
    text: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(AppShapeTokens.Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 单个会话行：标题 + 摘要。
 *
 * 不用 NavigationDrawerItem —— 它不提供长按回调，也放不下两行内容。
 * 删除走长按，列表项本身不放按钮。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: ConversationItem,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(AppShapeTokens.SettingsGroup)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showDeleteDialog = true }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = conversation.title,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // 无消息的会话不显示空摘要行，避免行高不齐
        conversation.lastMessage?.let { summary ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除对话") },
            text = { Text("确定要删除「${conversation.title}」吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
