package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.domain.model.AiProvider
import com.afyzfur.afyzhub.domain.model.ApiProfile
import com.afyzfur.afyzhub.ui.components.IconKey
import com.afyzfur.afyzhub.ui.components.IconBolt
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
    onNavigateToModels: () -> Unit,
    viewModel: ApiProfilesViewModel = koinViewModel(),
    modelsViewModel: ProfileModelsViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel()
) {
    val store by viewModel.store.collectAsState()
    val profile = store.profiles.firstOrNull { it.id == profileId }
    // Gemini 原生搜索开关: 全局项但只在此页(provider=GEMINI 时)出现
    val geminiSearchEnabled by settingsViewModel.geminiSearchEnabled.collectAsState()

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

    val testing by modelsViewModel.testing.collectAsState()
    val testResult by modelsViewModel.testResult.collectAsState()

    // 换到另一组配置时清掉上一组的测试结果，否则会被误读成当前组的
    LaunchedEffect(profileId) { modelsViewModel.clearTestResult() }
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
                        // 切换配置组时重建输入框，否则会留着上一组的值
                        identityKey = profileId,
                        title = "名称",
                        value = profile.name,
                        onValueChange = { viewModel.updateProfile(profile.copy(name = it)) },
                        placeholder = "例如：主号、中转-便宜"
                    )
                    SettingsItemDivider()
                    SettingsTextFieldItem(
                        // 切换配置组时重建输入框，否则会留着上一组的值
                        identityKey = profileId,
                        title = "分组",
                        value = profile.group,
                        onValueChange = { viewModel.updateProfile(profile.copy(group = it)) },
                        placeholder = "留空则归入未分组",
                        subtitle = "同名分组会归到一起"
                    )
                }

                SettingsCategoryTitle("服务提供商")
                SettingsGroup {
                    SettingsDropdownItem(
                        icon = IconKey,
                        title = "提供商",
                        subtitle = "决定请求的协议格式与默认地址",
                        options = AiProvider.entries,
                        selected = profile.provider,
                        label = { it.displayName },
                        onSelect = { entry ->
                            viewModel.updateProfile(
                                profile.copy(providerId = entry.id)
                            )
                        }
                    )
                    // 仅 Gemini 协议显示: 原生搜索是 Gemini 服务端
                    // grounding 能力, 其他提供商没有对应实现
                    if (profile.provider == AiProvider.GEMINI) {
                        SettingsItemDivider()
                        SettingsSwitchItem(
                            icon = IconBolt,
                            title = "原生联网搜索",
                            subtitle = "Google 服务端搜索接地，仅 Gemini 协议可用",
                            checked = geminiSearchEnabled,
                            onCheckedChange = settingsViewModel::updateGeminiSearchEnabled
                        )
                    }
                }

                SettingsCategoryTitle("系统提示词")
                SettingsGroup {
                    SettingsTextFieldItem(
                        identityKey = profileId,
                        title = "该组的系统提示词",
                        value = profile.systemPrompt,
                        onValueChange = {
                            viewModel.updateProfile(
                                profile.copy(systemPrompt = it)
                            )
                        },
                        placeholder = "留空则不注入。切换配置组即切换人设",
                        singleLine = false
                    )
                }
                SettingsCategoryTitle("接口配置")
                SettingsGroup {
                    // API Key 默认隐藏，点击右侧图标可临时显示。
                    SettingsTextFieldItem(
                        // 切换配置组时重建输入框，否则会留着上一组的值
                        identityKey = profileId,
                        title = "API Key",
                        value = profile.apiKey,
                        // 改动后清掉上次的测试结果：否则那条「连接正常」会
                        // 留在界面上，让人以为改完的配置也已经验证过
                        onValueChange = {
                            modelsViewModel.clearTestResult()
                            viewModel.updateProfile(profile.copy(apiKey = it))
                        },
                        placeholder = apiKeyHint(profile.provider),
                        passwordVisibleToggle = true
                    )
                    SettingsItemDivider()
                    SettingsTextFieldItem(
                        // 切换配置组时重建输入框，否则会留着上一组的值
                        identityKey = profileId,
                        title = "API 地址",
                        value = profile.baseUrl,
                        onValueChange = {
                            modelsViewModel.clearTestResult()
                            viewModel.updateProfile(profile.copy(baseUrl = it))
                        },
                        placeholder = profile.provider.defaultBaseUrl,
                        // 不放「恢复默认」按钮：实际多数是填中转地址，
                        // 那个按钮几乎不会用到，还占掉一行的右半边。
                        // 想回官方地址清空即可，占位符已提示默认值
                        subtitle = "留空则用官方地址，中转服务填对应地址"
                    )
                }

                SettingsCategoryTitle("连接测试")
                SettingsGroup {
                    SettingsActionItem(
                        icon = Icons.Default.PlayArrow,
                        title = if (testing) "测试中…" else "测试这组配置",
                        subtitle = "发一次最小请求，确认能否正常对话",
                        onClick = { modelsViewModel.testConnection(profile) }
                    )
                    testResult?.let { result ->
                        SettingsItemDivider()
                        TestResultRow(result)
                    }
                }

                // 模型相关的编辑(改名、拉列表、挑选)整体收进二级页：
                // 编辑页曾经六块纵向堆叠, 滚下来信息量过载。这里只留
                // 一个入口行, 副标题直接显示当前模型, 不进二级页也能
                // 看到生效值
                SettingsCategoryTitle("模型")
                SettingsGroup {
                    SettingsNavItem(
                        icon = Icons.Default.PlayArrow,
                        title = "模型管理",
                        subtitle = profile.effectiveModel,
                        onClick = onNavigateToModels
                    )
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

/**
 * 连接测试的结果行。
 *
 * 失败原因完整显示、不截断：服务端返回的原文往往直接指出问题
 * （密钥无效、模型不存在、额度耗尽），截断反而要用户去别处翻日志。
 */
@Composable
private fun TestResultRow(result: TestResult) {
    val (color, title, detail) = when (result) {
        is TestResult.Success -> Triple(
            MaterialTheme.colorScheme.primary,
            "连接正常",
            "${result.model} · 响应 ${result.elapsedMs} ms · 回复「${result.preview.take(20)}」"
        )
        is TestResult.Failure -> Triple(
            MaterialTheme.colorScheme.error,
            "连接失败",
            result.reason
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = if (result is TestResult.Success) {
                Icons.Default.Check
            } else {
                Icons.Default.Close
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = color
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 各家 Key 格式差别较大，占位符给出对应示例 */
private fun apiKeyHint(provider: AiProvider): String = when (provider) {
    AiProvider.OPENAI -> "sk-..."
    AiProvider.ANTHROPIC -> "sk-ant-..."
    AiProvider.GEMINI -> "AIza..."
}
