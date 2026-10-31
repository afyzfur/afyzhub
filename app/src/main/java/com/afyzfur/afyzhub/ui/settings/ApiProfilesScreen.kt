package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.domain.model.ApiProfile
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens
import org.koin.androidx.compose.koinViewModel

/**
 * API 配置组列表。
 *
 * 一组配置 = 一份 Key + 地址 + 模型，可自定义名称并归入分组。
 * 同一家有多个 Key（不同额度、不同中转）时不必再来回覆盖。
 *
 * 点一行即把这组设为生效，点行尾铅笔进入编辑。切换是高频操作
 * 给整行，编辑是低频操作收进按钮。
 */
@Composable
fun ApiProfilesScreen(
    onNavigateBack: () -> Unit,
    onEditProfile: (String) -> Unit,
    viewModel: ApiProfilesViewModel = koinViewModel()
) {
    val store by viewModel.store.collectAsState()
    var pendingDelete by remember { mutableStateOf<ApiProfile?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SettingsPageHeader(title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_64f968b0cf1d), onNavigateBack = onNavigateBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                if (store.profiles.isEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_b05b205e7d9d),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // 按分组分块展示，未分组的排在最后
                store.grouped().forEach { (group, profiles) ->
                    SettingsCategoryTitle(group.ifBlank { com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_8de957a7b7a2) })
                    SettingsGroup {
                        profiles.forEach { profile ->
                            ProfileRow(
                                profile = profile,
                                selected = profile.id == store.active?.id,
                                onSelect = { viewModel.selectProfile(profile.id) },
                                onEdit = { onEditProfile(profile.id) }
                            )
                        }
                    }
                }

                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_4989b5cf9483))
                SettingsGroup {
                    SettingsActionItem(
                        icon = Icons.Default.Add,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_31cc2c4c86f0),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_bb777b5751df),
                        // 建完就跳进去填 Key，省一次点击
                        onClick = { viewModel.addProfile(onCreated = onEditProfile) }
                    )
                    store.active?.let { active ->
                        SettingsItemDivider()
                        SettingsActionItem(
                            icon = Icons.Default.Add,
                            title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_6c3e065e11a9),
                            subtitle = "基于「${active.displayName}」建一组副本",
                            onClick = { viewModel.duplicateProfile(active.id) }
                        )
                        SettingsItemDivider()
                        SettingsActionItem(
                            icon = Icons.Default.Delete,
                            title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_a42184082e99),
                            subtitle = active.displayName,
                            destructive = true,
                            onClick = { pendingDelete = active }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_3fd2c2c8f4a8),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // 删除不可撤销，Key 丢了要重新去服务商那边取，值得确认一次
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_9764f48bf69c)) },
            text = { Text("将删除「${target.displayName}」，其中的 API Key 也会一并清除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProfile(target.id)
                    pendingDelete = null
                }) {
                    Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_3755f56f2f83), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_4d0b4688c787)) }
            }
        )
    }
}

/**
 * 配置组的一行。
 *
 * 整行点击把这组设为生效——切换是这里最高频的操作；编辑收进
 * 行尾的铅笔按钮。生效状态只用一个单选圆点表达，不再叠加
 * 背景高亮、多重对勾这些视觉噪音。
 */
@Composable
private fun ProfileRow(
    profile: ApiProfile,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit
) {
    // 卡片式行: 卡片主体点击进编辑, 右侧"使用/使用中"chip 切换生效。
    // 之前整行点击都是切换生效, 想改配置反而难进去; 圆形单选钮
    // 也与整体风格不搭——选中态用主题色卡片+文字标识表达
    Surface(
        onClick = onEdit,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = AppShapeTokens.SettingsGroup,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    // Key 是否填过比 Key 本身更有用，列表里不该露出明文
                    text = buildString {
                        append(profile.effectiveModel)
                        if (profile.apiKey.isBlank()) append(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_c89821b90a53))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (profile.apiKey.isBlank()) {
                        MaterialTheme.colorScheme.error
                    } else if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 生效切换: 独立可点的 chip, 语义一目了然
            Surface(
                onClick = onSelect,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = if (selected) com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_e6aa2cbd7b42) else com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_0e2d3a3c09c2),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}
