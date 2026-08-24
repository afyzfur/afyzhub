package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.domain.model.AiProvider
import org.koin.androidx.compose.koinViewModel

/**
 * 提供商配置子页面。
 *
 * 由改版前的单页设置整体转化而来——那时它就是整个设置页，现在成为
 * 「模型与服务 → 提供商」下的一个子页面。内容与交互逻辑未改动，
 * 只替换了页面头部并套用新的分组容器。
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
                    // 防抖窗口内可能还有未落盘的改动，先刷盘再返回。
                    viewModel.flushPendingChanges()
                    onNavigateBack()
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            Text(
                text = "服务提供商",
                style = MaterialTheme.typography.titleMedium
            )

            ProviderSelector(
                current = provider,
                onSelect = viewModel::selectProvider
            )

            Text(
                text = "各提供商的 Key、地址和模型分别保存，切换时不会互相覆盖。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Text(
                text = "接口配置",
                style = MaterialTheme.typography.titleLarge
            )

            // 明文显示：便于核对与修改，Key 只存在本机。
            OutlinedTextField(
                value = apiKey,
                onValueChange = viewModel::updateApiKey,
                label = { Text("API Key") },
                placeholder = { Text(apiKeyPlaceholder(provider)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                label = { Text("API 地址") },
                placeholder = { Text(provider.defaultBaseUrl) },
                supportingText = { Text("使用中转服务时填入对应地址") },
                trailingIcon = {
                    TextButton(onClick = viewModel::resetBaseUrl) {
                        Text("默认")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(
                text = "模型",
                style = MaterialTheme.typography.titleMedium
            )

            // 允许直接输入模型名，以支持中转服务提供的非官方模型。
            OutlinedTextField(
                value = selectedModel,
                onValueChange = viewModel::updateModel,
                label = { Text("模型名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = viewModel::refreshModels,
                    enabled = !loadingModels
                ) {
                    Text(if (availableModels.isEmpty()) "获取模型列表" else "刷新列表")
                }
                if (loadingModels) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            }

            modelsError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // 列表直接平铺在此处并持久缓存，避免离开页面后需要重新获取。
            if (availableModels.isNotEmpty()) {
                Text(
                    text = "可用模型（${availableModels.size}）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRowModels(
                    models = availableModels,
                    selected = selectedModel,
                    onSelect = viewModel::updateModel
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "流式输出",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "逐字显示回复。部分中转服务不支持，可关闭后重试。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = streamEnabled,
                    onCheckedChange = viewModel::updateStreamEnabled
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 设置改动自动保存，不再需要手动确认。
            Text(
                text = if (saveSuccess) "设置已自动保存" else "改动会自动保存",
                style = MaterialTheme.typography.bodySmall,
                color = if (saveSuccess) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            if (saveSuccess) {
                LaunchedEffect(saveSuccess) {
                    kotlinx.coroutines.delay(2000)
                    viewModel.clearSaveSuccess()
                }
            }

            Text(
                text = "API Key 仅保存在本机，不会上传到任何第三方服务器。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderSelector(
    current: AiProvider,
    onSelect: (AiProvider) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AiProvider.entries.forEach { provider ->
            FilterChip(
                selected = provider == current,
                onClick = { onSelect(provider) },
                label = { Text(provider.displayName) }
            )
        }
    }
}

/** 各家 Key 的格式差别较大，占位符给出对应示例。 */
private fun apiKeyPlaceholder(provider: AiProvider): String = when (provider) {
    AiProvider.OPENAI -> "sk-..."
    AiProvider.ANTHROPIC -> "sk-ant-..."
    AiProvider.GEMINI -> "AIza..."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowModels(
    models: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        models.forEach { model ->
            FilterChip(
                selected = model == selected,
                onClick = { onSelect(model) },
                label = { Text(model) }
            )
        }
    }
}
