package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.domain.model.AiProvider
import com.afyzfur.afyzhub.domain.model.ApiProfile
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens
import org.koin.androidx.compose.koinViewModel

/**
 * 单组 API 配置的编辑页。
 *
 * 改动即时写回配置组。这里没做防抖：DataStore 的写入本身是异步且
 * 串行的，输入过程中的中间态被后来的值覆盖即可，不需要额外协调。
 * 状态唯一来源是仓库的 flow，因此不存在界面与存储不一致的窗口。
 *
 * 找不到 [profileId] 时直接返回：可能是这一组已在别处被删掉。
 */
@Composable
fun ApiProfileEditScreen(
    profileId: String,
    onNavigateBack: () -> Unit,
    viewModel: ApiProfilesViewModel = koinViewModel(),
    modelsViewModel: ProfileModelsViewModel = koinViewModel()
) {
    val store by viewModel.store.collectAsState()
    val profile = store.profiles.firstOrNull { it.id == profileId }

    if (profile == null) {
        // 这一组已不存在，没什么可编辑的
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                SettingsPageHeader(title = "配置", onNavigateBack = onNavigateBack)
                Text(
                    text = "这组配置已被删除。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(28.dp)
                )
            }
        }
        return
    }

    val loading by modelsViewModel.loading.collectAsState()
    val error by modelsViewModel.error.collectAsState()
    var pageIndex by remember(profileId) { mutableIntStateOf(0) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SettingsPageHeader(
                title = profile.displayName,
                onNavigateBack = onNavigateBack
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsCategoryTitle("名称与分组")
                SettingsGroup {
                    SettingsTextFieldItem(
                        title = "名称",
                        value = profile.name,
                        onValueChange = { viewModel.updateProfile(profile.copy(name = it)) },
                        placeholder = "例如：主号、中转-便宜"
                    )
                    SettingsItemDivider()
                    SettingsTextFieldItem(
                        title = "分组",
                        value = profile.group,
                        onValueChange = { viewModel.updateProfile(profile.copy(group = it)) },
                        placeholder = "留空则归入未分组",
                        subtitle = "同名分组会归到一起"
                    )
                }

                SettingsCategoryTitle("服务提供商")
                SettingsGroup {
                    AiProvider.entries.forEachIndexed { index, entry ->
                        if (index > 0) SettingsItemDivider()
                        SettingsRadioItem(
                            title = entry.displayName,
                            selected = entry.id == profile.providerId,
                            onClick = {
                                viewModel.updateProfile(
                                    profile.copy(providerId = entry.id)
                                )
                            }
                        )
                    }
                }

                SettingsCategoryTitle("接口配置")
                SettingsGroup {
                    // 明文显示：便于核对与修改，Key 只存在本机
                    SettingsTextFieldItem(
                        title = "API Key",
                        value = profile.apiKey,
                        onValueChange = { viewModel.updateProfile(profile.copy(apiKey = it)) },
                        placeholder = apiKeyHint(profile.provider)
                    )
                    SettingsItemDivider()
                    SettingsTextFieldItem(
                        title = "API 地址",
                        value = profile.baseUrl,
                        onValueChange = { viewModel.updateProfile(profile.copy(baseUrl = it)) },
                        placeholder = profile.provider.defaultBaseUrl,
                        subtitle = "使用中转服务时填入对应地址",
                        trailing = {
                            TextButton(onClick = {
                                viewModel.updateProfile(profile.copy(baseUrl = ""))
                            }) {
                                Text("恢复默认")
                            }
                        }
                    )
                }

                SettingsCategoryTitle("模型")
                SettingsGroup {
                    SettingsTextFieldItem(
                        title = "模型名称",
                        value = profile.model,
                        onValueChange = { viewModel.updateProfile(profile.copy(model = it)) },
                        placeholder = "留空则使用 ${profile.provider.fallbackModel}"
                    )

                    SettingsItemDivider()
                    ModelFetchRow(
                        loading = loading,
                        hasModels = profile.cachedModels.isNotEmpty(),
                        onRefresh = {
                            modelsViewModel.fetchModels(profile) { models ->
                                viewModel.updateProfile(
                                    profile.copy(cachedModels = models)
                                )
                                pageIndex = 0
                            }
                        }
                    )

                    error?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }

                if (profile.cachedModels.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    SettingsGroup {
                        val page = pageOfModels(profile.cachedModels, pageIndex)
                        Text(
                            text = if (page.showPager) {
                                "可用模型（${profile.cachedModels.size}）" +
                                    " 第 ${page.pageIndex + 1}/${page.pageCount} 页"
                            } else {
                                "可用模型（${profile.cachedModels.size}）"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                start = 20.dp,
                                end = 20.dp,
                                top = 12.dp
                            )
                        )
                        ModelPickerChips(
                            models = page.models,
                            selected = profile.model,
                            onSelect = {
                                viewModel.updateProfile(profile.copy(model = it))
                            }
                        )
                        if (page.showPager) {
                            SettingsItemDivider()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                TextButton(
                                    onClick = { pageIndex -= 1 },
                                    enabled = page.hasPrevious
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text("上一页")
                                }
                                Text(
                                    text = "${page.pageIndex + 1} / ${page.pageCount}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = { pageIndex += 1 },
                                    enabled = page.hasNext
                                ) {
                                    Text("下一页")
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "改动会自动保存。API Key 仅存在本机，不会上传到第三方。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 拉取模型列表的操作行 */
@Composable
private fun ModelFetchRow(
    loading: Boolean,
    hasModels: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !loading, onClick = onRefresh)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = if (hasModels) "刷新模型列表" else "从服务端获取模型列表",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        if (loading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** 模型选择胶囊，与提供商页一致的实心样式 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelPickerChips(
    models: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        models.forEach { model ->
            val isSelected = model == selected
            Surface(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                shape = AppShapeTokens.Pill,
                modifier = Modifier.clickable { onSelect(model) }
            ) {
                Text(
                    text = model,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .heightIn(min = 32.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/** 各家 Key 格式差别较大，占位符给出对应示例 */
private fun apiKeyHint(provider: AiProvider): String = when (provider) {
    AiProvider.OPENAI -> "sk-..."
    AiProvider.ANTHROPIC -> "sk-ant-..."
    AiProvider.GEMINI -> "AIza..."
}
