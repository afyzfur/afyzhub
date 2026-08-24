package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.domain.model.AiProvider
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/**
 * 提供商配置子页面。
 *
 * 改版说明：此前这页是从旧版单页设置整体搬来的，仍在用 OutlinedTextField
 * 与 HorizontalDivider——描边输入框加分割线是 Material 2 的语言，
 * 与其他设置页的「分类标题 + 圆角分组容器」不一致。现已统一。
 *
 * 交互逻辑未改：改动仍由 SettingsViewModel 防抖后自动落盘，
 * 返回时先刷盘。
 */
@Composable
fun ProviderSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val provider by viewModel.provider.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()
    val streamEnabled by viewModel.streamEnabled.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val loadingModels by viewModel.loadingModels.collectAsState()
    val modelsError by viewModel.modelsError.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()

    if (saveSuccess) {
        LaunchedEffect(saveSuccess) {
            delay(2000)
            viewModel.clearSaveSuccess()
        }
    }

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
                title = "提供商",
                onNavigateBack = {
                    // 防抖窗口内可能还有未落盘的改动，先刷盘再返回
                    viewModel.flushPendingChanges()
                    onNavigateBack()
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsCategoryTitle("服务提供商")
                SettingsGroup {
                    AiProvider.entries.forEach { entry ->
                        SettingsRadioItem(
                            title = entry.displayName,
                            selected = entry == provider,
                            onClick = { viewModel.selectProvider(entry) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "各提供商的 Key、地址与模型分别保存，切换时不会互相覆盖。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )

                SettingsCategoryTitle("接口配置")
                SettingsGroup {
                    // 明文显示：便于核对与修改，Key 只存在本机
                    SettingsTextFieldItem(
                        title = "API Key",
                        value = apiKey,
                        onValueChange = viewModel::updateApiKey,
                        placeholder = apiKeyPlaceholder(provider)
                    )
                    SettingsTextFieldItem(
                        title = "API 地址",
                        value = baseUrl,
                        onValueChange = viewModel::updateBaseUrl,
                        placeholder = provider.defaultBaseUrl,
                        subtitle = "使用中转服务时填入对应地址",
                        trailing = {
                            TextButton(onClick = viewModel::resetBaseUrl) {
                                Text("恢复默认")
                            }
                        }
                    )
                }

                SettingsCategoryTitle("模型")
                SettingsGroup {
                    // 允许直接输入模型名，以支持中转服务提供的非官方模型
                    SettingsTextFieldItem(
                        title = "模型名称",
                        value = selectedModel,
                        onValueChange = viewModel::updateModel,
                        placeholder = "留空则使用提供商默认模型"
                    )

                    ModelRefreshRow(
                        loading = loadingModels,
                        hasModels = availableModels.isNotEmpty(),
                        onRefresh = viewModel::refreshModels
                    )

                    modelsError?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }

                // 列表平铺在此处并持久缓存，避免离开页面后需要重新获取
                if (availableModels.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    SettingsGroup {
                        Text(
                            text = "可用模型（${availableModels.size}）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                start = 20.dp,
                                end = 20.dp,
                                top = 12.dp
                            )
                        )
                        ModelChips(
                            models = availableModels,
                            selected = selectedModel,
                            onSelect = viewModel::updateModel
                        )
                    }
                }

                SettingsCategoryTitle("请求")
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Default.Refresh,
                        title = "流式输出",
                        subtitle = "逐字显示回复。部分中转服务不支持，可关闭后重试",
                        checked = streamEnabled,
                        onCheckedChange = viewModel::updateStreamEnabled
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (saveSuccess) {
                        "设置已保存"
                    } else {
                        "改动会自动保存。API Key 仅存在本机，不会上传到第三方。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (saveSuccess) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 28.dp)
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 刷新模型列表的操作行，加载中时显示进度指示 */
@Composable
private fun ModelRefreshRow(
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

/**
 * 可用模型的选择 chip。
 *
 * 不用 FilterChip：它自带描边与选中态图标，在数百个模型的列表里
 * 视觉噪音过大。改用与首屏提示词一致的实心胶囊。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelChips(
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

/** 各家 Key 的格式差别较大，占位符给出对应示例。 */
private fun apiKeyPlaceholder(provider: AiProvider): String = when (provider) {
    AiProvider.OPENAI -> "sk-..."
    AiProvider.ANTHROPIC -> "sk-ant-..."
    AiProvider.GEMINI -> "AIza..."
}
