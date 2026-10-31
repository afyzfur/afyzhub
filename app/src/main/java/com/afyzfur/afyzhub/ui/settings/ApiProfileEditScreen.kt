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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
    onNavigateToAdvancedPrompt: () -> Unit,
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
                SettingsPageHeader(title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_d7d7ce790b9a), onNavigateBack = onNavigateBack)
                Text(
                    text = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_9f61d7d38215),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(28.dp)
                )
            }
        }
        return
    }

    val testing by modelsViewModel.testing.collectAsState()
    var testSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(testing) { while (testing) { delay(1000); testSeconds++ } }
    val testResult by modelsViewModel.testResult.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

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
                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_ab79fd0a625e))
                SettingsGroup {
                    SettingsTextFieldItem(
                        // 切换配置组时重建输入框，否则会留着上一组的值
                        identityKey = profileId,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_1be7ae4fc257),
                        value = profile.name,
                        onValueChange = { viewModel.updateProfile(profile.copy(name = it)) },
                        placeholder = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_afd01c7bd437)
                    )
                    SettingsItemDivider()
                    SettingsTextFieldItem(
                        // 切换配置组时重建输入框，否则会留着上一组的值
                        identityKey = profileId,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_97d8a6c05b4c),
                        value = profile.group,
                        onValueChange = { viewModel.updateProfile(profile.copy(group = it)) },
                        placeholder = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_abeb9eae8cd2),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_16472019313f)
                    )
                }

                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_f73eee9e85c6))
                SettingsGroup {
                    SettingsDropdownItem(
                        icon = IconKey,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_219c099b6e14),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_4c8afa7576a9),
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
                            title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_91b8b3886a06),
                            subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_839be7010244),
                            checked = geminiSearchEnabled,
                            onCheckedChange = settingsViewModel::updateGeminiSearchEnabled
                        )
                    }
                }

                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_4c16e86779d2))
                SettingsGroup {
                    // 地址放在 Key 前面，配置时先确认请求发往哪里。
                    SettingsTextFieldItem(
                        identityKey = profileId,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_acd8a5043158),
                        value = profile.baseUrl,
                        onValueChange = {
                            modelsViewModel.clearTestResult()
                            viewModel.updateProfile(profile.copy(baseUrl = it))
                        },
                        placeholder = profile.provider.defaultBaseUrl,
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_2264317dc171)
                    )
                    SettingsItemDivider()
                    // 明文显示：便于核对与修改，Key 只存在本机
                    SettingsTextFieldItem(
                        identityKey = profileId,
                        title = "API Key",
                        value = profile.apiKey,
                        onValueChange = {
                            modelsViewModel.clearTestResult()
                            viewModel.updateProfile(profile.copy(apiKey = it))
                        },
                        placeholder = apiKeyHint(profile.provider),
                        passwordVisibleToggle = true
                    )
                }

                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_19398128e0f8))
                SettingsGroup {
                    SettingsDropdownItem(
                        icon = IconBolt, title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_7f362bad0c29), subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_e928ced77864),
                        options = profile.availablePrompts, selected = profile.activePrompt, label = { it.name },
                        onSelect = { group -> viewModel.changePrompts(profileId) { it.copy(activePromptId = group.id) } }
                    )
                    SettingsItemDivider()
                    SettingsTextFieldItem(
                        identityKey = profileId + profile.activePrompt.id,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_338affe37fbd),
                        value = profile.activePrompt.role,
                        onValueChange = { text -> viewModel.changePrompts(profileId) { it.editActivePrompt { group -> group.copy(role = text) } } },
                        placeholder = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_dba6c64184ee),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_98859a6b7d9a),
                        singleLine = false
                    )
                    SettingsItemDivider()
                    SettingsNavItem(
                        icon = Icons.Default.PlayArrow,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_dd07e641ca66),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_f7228a388abd),
                        onClick = onNavigateToAdvancedPrompt
                    )
                    SettingsItemDivider()
                    SettingsSwitchItem(
                        icon = IconBolt,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_f44e0a04c577),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_985302bd5aa3),
                        checked = profile.promptsEnabled,
                        onCheckedChange = { enabled -> viewModel.changePrompts(profileId) { it.copy(promptsEnabled = enabled) } }
                    )
                }

                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_3f09fccffabd))
                SettingsGroup {
                    SettingsActionItem(
                        icon = Icons.Default.PlayArrow,
                        title = if (testing) com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.profile_test_running, testSeconds) else com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_27fc3c44360f),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_6e995a7133c9),
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
                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_98fd0cbd9c31))
                SettingsGroup {
                    SettingsNavItem(
                        icon = Icons.Default.PlayArrow,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_183414408d44),
                        subtitle = profile.effectiveModel,
                        onClick = onNavigateToModels
                    )
                }

                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_fcfcc873ed3c))
                SettingsGroup {
                    SettingsActionItem(
                        icon = Icons.Default.Delete,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_2054f14a2346),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_2abdfd12448c),
                        destructive = true,
                        onClick = { confirmDelete = true }
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_5a034fa39789),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_9764f48bf69c)) },
            text = { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.profile_delete_confirm, profile.displayName)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteProfile(profile.id); onNavigateBack() }) {
                    Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_3755f56f2f83), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_4d0b4688c787)) } }
        )
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
            com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_51991a5d111a),
            com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.profile_test_success, result.elapsedMs, result.elapsedMs)
        )
        is TestResult.Failure -> Triple(
            MaterialTheme.colorScheme.error,
            com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_2c056f182f42),
            "测试耗时 ${result.elapsedMs} ms\n${result.reason}"
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
