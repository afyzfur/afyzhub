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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.afyzfur.afyzhub.ui.components.ModelIcon
import org.koin.androidx.compose.koinViewModel

/**
 * API 配置组列表。
 *
 * 一组配置 = 一份 Key + 地址 + 模型，可自定义名称并归入分组。
 * 同一家有多个 Key（不同额度、不同中转）时不必再来回覆盖。
 *
 * 点一行即选中该组生效，点右侧箭头进入编辑。选中与编辑分开：
 * 日常用得最多的是切换，不该每次都先进详情页。
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
            SettingsPageHeader(title = "API 配置", onNavigateBack = onNavigateBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                if (store.profiles.isEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "还没有配置。新建一组后填入 API Key 即可开始对话。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // 按分组分块展示，未分组的排在最后
                store.grouped().forEach { (group, profiles) ->
                    SettingsCategoryTitle(group.ifBlank { "未分组" })
                    SettingsGroup {
                        profiles.forEachIndexed { index, profile ->
                            if (index > 0) SettingsItemDivider()
                            ProfileRow(
                                profile = profile,
                                selected = profile.id == store.active?.id,
                                onSelect = { viewModel.selectProfile(profile.id) },
                                onEdit = { onEditProfile(profile.id) }
                            )
                        }
                    }
                }

                SettingsCategoryTitle("管理")
                SettingsGroup {
                    SettingsActionItem(
                        icon = Icons.Default.Add,
                        title = "新建配置",
                        subtitle = "建好后直接进入编辑",
                        // 建完就跳进去填 Key，省一次点击
                        onClick = { viewModel.addProfile(onCreated = onEditProfile) }
                    )
                    store.active?.let { active ->
                        SettingsItemDivider()
                        SettingsActionItem(
                            icon = Icons.Default.Add,
                            title = "复制当前配置",
                            subtitle = "基于「${active.displayName}」建一组副本",
                            onClick = { viewModel.duplicateProfile(active.id) }
                        )
                        SettingsItemDivider()
                        SettingsActionItem(
                            icon = Icons.Default.Delete,
                            title = "删除当前配置",
                            subtitle = active.displayName,
                            destructive = true,
                            onClick = { pendingDelete = active }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "选中的配置对全部会话生效。API Key 仅存在本机，不会上传到第三方。",
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
            title = { Text("删除配置") },
            text = { Text("将删除「${target.displayName}」，其中的 API Key 也会一并清除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProfile(target.id)
                    pendingDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

/**
 * 配置组的一行。
 *
 * 点整行进入编辑，点左侧的勾选区把这组设为生效。
 *
 * 此前是反过来的——整行选中、右侧按钮进编辑。但改 Key、换模型
 * 这些事都在编辑页里，而"仅仅切换生效组"更多是在模型选择页顺手
 * 完成的，所以把进入编辑放在整行这个更大的点击区上。
 */
@Composable
private fun ProfileRow(
    profile: ApiProfile,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit
) {
    // clickable 加在最外层：此前它在内层的 weight(1f) 那个 Row 上，
    // 右侧箭头落在可点区域之外——涟漪到箭头前就断了，点箭头也没反应。
    // 勾选区的 IconButton 在这一层之内，它自己的点击会先被消费掉，
    // 因此不会连带触发进入编辑
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(top = 12.dp, bottom = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            // 勾选区单独可点，用来切换生效组；整行则是进入编辑
            IconButton(onClick = onSelect, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = if (selected) "已生效" else "设为生效",
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.size(8.dp))
            ModelIcon(modelName = profile.effectiveModel, size = 22.dp)
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    // Key 是否填过比 Key 本身更有用，列表里不该露出明文
                    text = buildString {
                        append(profile.effectiveModel)
                        if (profile.apiKey.isBlank()) append("　未填 Key")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (profile.apiKey.isBlank()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // 箭头只作提示，点击由整行承接
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.size(16.dp))
    }
}
