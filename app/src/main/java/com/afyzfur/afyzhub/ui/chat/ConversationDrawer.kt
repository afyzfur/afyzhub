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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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
import com.afyzfur.afyzhub.data.settings.ChatAppearance
import com.afyzfur.afyzhub.ui.components.ModelIcon
import com.afyzfur.afyzhub.ui.components.UserAvatar
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
    /** 用于取用户头像与显示开关 */
    appearance: ChatAppearance,
    /** 已存在的分组名，供长按后移动会话时选择 */
    existingGroups: List<String> = emptyList(),
    onConversationClick: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onTogglePin: (Long, Boolean) -> Unit = { _, _ -> },
    onToggleStar: (Long, Boolean) -> Unit = { _, _ -> },
    onRenameConversation: (Long, String) -> Unit = { _, _ -> },
    onUpdateNote: (Long, String) -> Unit = { _, _ -> },
    onMoveToGroup: (Long, String) -> Unit = { _, _ -> },
    onSettingsClick: () -> Unit,
    onModelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 分组结果随列表变化重算，不必每次重组都做
    val grouped = remember(conversations) { groupConversations(conversations) }

    Column(modifier = modifier.fillMaxSize()) {

        // 头像放在标题左侧：这是抽屉里唯一属于"用户自身"的位置，
        // 而消息列表里的头像会随滚动移出视野
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 24.dp, top = 28.dp, bottom = 16.dp)
        ) {
            if (appearance.showAvatars && appearance.showUserAvatar) {
                UserAvatar(appearance = appearance, size = 36.dp)
                Spacer(Modifier.size(12.dp))
            }
            Text(
                text = "AfyzHub",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

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
                item(key = "header-${group.label}") {
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
                        existingGroups = existingGroups,
                        onClick = { onConversationClick(conversation.id) },
                        // 传当前值的反面：操作表显示的是"置顶"还是
                        // "取消置顶"，点下去就是切到另一个状态
                        onTogglePin = {
                            onTogglePin(conversation.id, !conversation.pinned)
                        },
                        onToggleStar = {
                            onToggleStar(conversation.id, !conversation.starred)
                        },
                        onRename = { onRenameConversation(conversation.id, it) },
                        onUpdateNote = { onUpdateNote(conversation.id, it) },
                        onMoveToGroup = { onMoveToGroup(conversation.id, it) },
                        onDelete = { onDeleteConversation(conversation.id) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 底部两行，均带文字标签。参考对象用五个无标签圆形图标，可发现性差
        // 点模型直接进提供商配置：模型与 Key 是最常改动的项，
        // 经设置首页多绕一层没有必要
        DrawerActionRow(
            text = modelLabel,
            onClick = onModelClick,
            leading = {
                // 按模型名匹配厂商图标，匹配不到时 ModelIcon 内部退回首字母
                ModelIcon(modelName = modelLabel)
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
    existingGroups: List<String>,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleStar: () -> Unit,
    onRename: (String) -> Unit,
    onUpdateNote: (String) -> Unit,
    onMoveToGroup: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }

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
                onLongClick = { showSheet = true }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 置顶与星标各用一个小图标标在标题前，不占额外行高
            if (conversation.pinned) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "已置顶",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            if (conversation.starred) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "已加星标",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = conversation.title,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 优先显示模型生成的总结，没有则退回末条消息。
        // 总结是对整轮问答的概括，比末条消息更能说明这个会话在谈什么
        // 用户自己写的简介优先于模型生成的总结：他明确写下的东西
        // 比模型的概括更贴近他想记住的内容
        val previewSource = conversation.note?.takeIf { it.isNotBlank() }
            ?: conversation.summary?.takeIf { it.isNotBlank() }
            ?: conversation.lastMessage
        // 无消息的会话不显示空摘要行，避免行高不齐
        previewSource?.let { raw ->
            // 摘要取的是原始正文，带思考标签的模型会让预览全是
            // 标签内容，看不到实际回答。
            //
            // 上一版这里在 answer 为空时退回原文，但那正是带标签的
            // 全文——整条都还在思考中时（回答尚未开始）预览依旧是
            // 一堆标签。现在改为给一个状态文案：预览的用途是让人
            // 认出这个会话，标签内容对此毫无帮助
            val summary = remember(raw) { previewOf(raw) }
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

    if (showSheet) {
        ConversationActionSheet(
            conversation = conversation,
            existingGroups = existingGroups,
            onDismiss = { showSheet = false },
            onTogglePin = onTogglePin,
            onToggleStar = onToggleStar,
            onRename = onRename,
            onUpdateNote = onUpdateNote,
            onMoveToGroup = onMoveToGroup,
            onDelete = onDelete
        )
    }
}
