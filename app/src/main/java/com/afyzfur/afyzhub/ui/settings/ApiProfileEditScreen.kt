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
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.afyzfur.afyzhub.ui.components.ModelIcon
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.LaunchedEffect
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
    val testing by modelsViewModel.testing.collectAsState()
    val testResult by modelsViewModel.testResult.collectAsState()

    // 换到另一组配置时清掉上一组的测试结果，否则会被误读成当前组的
    LaunchedEffect(profileId) { modelsViewModel.clearTestResult() }
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
                        // 改动后清掉上次的测试结果：否则那条「连接正常」会
                        // 留在界面上，让人以为改完的配置也已经验证过
                        onValueChange = {
                            modelsViewModel.clearTestResult()
                            viewModel.updateProfile(profile.copy(apiKey = it))
                        },
                        placeholder = apiKeyHint(profile.provider)
                    )
                    SettingsItemDivider()
                    SettingsTextFieldItem(
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
                        ModelPickerList(
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
            "${result.model} · ${result.elapsedMs} ms"
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

/**
 * 模型列表，每项占满一行。
 *
 * 此前用 FlowRow 排胶囊，宽度随模型名长短变化：短名字的点击区只有
 * 一小块，长名字被截断看不全，找目标时还得在不规则的排布里扫视。
 * 整宽竖排让每一项的点击区一致，名字也能完整显示。
 */
@Composable
private fun ModelPickerList(
    models: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        models.forEachIndexed { index, model ->
            if (index > 0) SettingsItemDivider()
            val isSelected = model == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(model) }
                    .background(
                        // 选中项用淡色底而非实心主色：整宽的实心色块
                        // 在列表里过于抢眼，压过其余内容
                        if (isSelected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            Color.Transparent
                        }
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                ModelIcon(modelName = model, size = 20.dp)
                Spacer(Modifier.size(12.dp))
                Text(
                    text = model,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    // 允许两行：中转的模型名常带前缀，一行放不下
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Spacer(Modifier.size(8.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "当前使用",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
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
