package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.afyzfur.afyzhub.domain.model.Conversation
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens

/**
 * 抽屉内容：会话列表 + 底部入口。
 *
 * 由原 HomeScreen 的职责转化而来。与原首页的差别：
 * - 不再是独立导航目的地，而是聊天页的抽屉内容
 * - 会话项不常驻删除按钮，改为长按触发确认弹窗。原首页每项右侧固定一个垃圾桶图标，
 *   既占宽度又容易误触
 * - 底部入口带文字标签，不用无标签的圆形图标按钮
 *
 * 时间分组与末条消息摘要属于阶段 4，本阶段先立结构。
 */
@Composable
fun ConversationDrawer(
    conversations: List<Conversation>,
    currentConversationId: Long?,
    onConversationClick: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {

        Text(
            text = "AfyzHub",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 24.dp, top = 28.dp, bottom = 16.dp)
        )

        DrawerActionRow(
            text = "新建对话",
            onClick = onNewConversation
        )

        Spacer(Modifier.height(12.dp))

        // 会话列表占据剩余空间，底部入口因此始终贴在下方
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(conversations, key = { it.id }) { conversation ->
                ConversationRow(
                    conversation = conversation,
                    selected = conversation.id == currentConversationId,
                    onClick = { onConversationClick(conversation.id) },
                    onDelete = { onDeleteConversation(conversation.id) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

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

/** 抽屉内的操作行，默认前置加号图标 */
@Composable
private fun DrawerActionRow(
    text: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit = {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 单个会话行。
 *
 * 选中态用 secondaryContainer 铺底并保持胶囊形状，与 M3 抽屉选中语言一致，
 * 但不使用 NavigationDrawerItem —— 后者不提供长按回调，无法承载删除操作。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(AppShapeTokens.Pill)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showDeleteDialog = true }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = conversation.title,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
