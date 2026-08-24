package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

/**
 * 设置一级页面：只做分类导航，具体项在子页面内。
 *
 * 版式见 docs/ui-redesign.md 4.4——改版前是 256 行单页平铺，
 * 所有内容堆在一起；现在一级页只承载入口，每项带副标题说明管什么。
 *
 * 流式输出是唯一留在一级页的开关：它取值简单、切换频率相对高
 *（中转服务不支持时需要关掉重试），为它单开子页面不值得。
 */
@Composable
fun SettingsHomeScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProvider: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToMessageDisplay: () -> Unit,
    onNavigateToQuickPrompts: () -> Unit,
    onNavigateToAbout: () -> Unit,
    uiPreferencesViewModel: UiPreferencesViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel()
) {
    val prefs by uiPreferencesViewModel.preferences.collectAsState()
    val provider by settingsViewModel.provider.collectAsState()
    val model by settingsViewModel.selectedModel.collectAsState()
    val streamEnabled by settingsViewModel.streamEnabled.collectAsState()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SettingsPageHeader(title = "设置", onNavigateBack = onNavigateBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsCategoryTitle("通用")

                SettingsGroup {
                    SettingsValueItem(
                        icon = Icons.Default.Brightness6,
                        title = "颜色模式",
                        subtitle = "浅色、深色或跟随系统",
                        value = prefs.colorMode.label,
                        onClick = onNavigateToAppearance
                    )
                    SettingsNavItem(
                        icon = Icons.Default.Palette,
                        title = "外观",
                        subtitle = "动态取色与配色来源",
                        onClick = onNavigateToAppearance
                    )
                }

                SettingsCategoryTitle("模型与服务")

                SettingsGroup {
                    SettingsNavItem(
                        icon = Icons.Default.Cloud,
                        title = "提供商",
                        // 副标题直接显示当前配置，省去进入子页面确认
                        subtitle = "${provider.displayName} · $model",
                        onClick = onNavigateToProvider
                    )
                    SettingsNavItem(
                        icon = Icons.Default.TipsAndUpdates,
                        title = "首屏提示词",
                        subtitle = if (prefs.quickPrompts.isEmpty()) {
                            "未设置"
                        } else {
                            "${prefs.quickPrompts.size} 条"
                        },
                        onClick = onNavigateToQuickPrompts
                    )
                }

                SettingsCategoryTitle("对话")

                SettingsGroup {
                    SettingsNavItem(
                        icon = Icons.Default.Chat,
                        title = "消息显示",
                        subtitle = "时间戳、token 用量等元信息",
                        onClick = onNavigateToMessageDisplay
                    )
                    SettingsSwitchItem(
                        icon = Icons.Default.Stream,
                        title = "流式输出",
                        subtitle = "逐字显示回复，部分中转服务不支持",
                        checked = streamEnabled,
                        onCheckedChange = settingsViewModel::updateStreamEnabled
                    )
                }

                SettingsCategoryTitle("关于")

                SettingsGroup {
                    SettingsNavItem(
                        icon = Icons.Default.Info,
                        title = "关于 AfyzHub",
                        subtitle = "版本与项目链接",
                        onClick = onNavigateToAbout
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
