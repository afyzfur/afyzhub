package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
    onNavigateToChatAppearance: () -> Unit,
    onNavigateToMessageDisplay: () -> Unit,
    onNavigateToQuickPrompts: () -> Unit,
    onNavigateToRequestLog: () -> Unit,
    onNavigateToAbout: () -> Unit,
    uiPreferencesViewModel: UiPreferencesViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel(),
    apiProfilesViewModel: ApiProfilesViewModel = koinViewModel()
) {
    val prefs by uiPreferencesViewModel.preferences.collectAsState()
    // 当前生效的配置组。流式开关仍走 SettingsViewModel——它是全局项，
    // 不属于任何一组配置
    val profileStore by apiProfilesViewModel.store.collectAsState()
    val activeProfile = profileStore.active
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
                        icon = Icons.Default.Star,
                        title = "颜色模式",
                        subtitle = "浅色、深色或跟随系统",
                        value = prefs.colorMode.label,
                        onClick = onNavigateToAppearance
                    )
                    SettingsNavItem(
                        icon = Icons.Default.Favorite,
                        title = "外观",
                        subtitle = "动态取色与配色来源",
                        onClick = onNavigateToAppearance
                    )
                }

                SettingsCategoryTitle("模型与服务")

                SettingsGroup {
                    SettingsNavItem(
                        icon = Icons.Default.AccountCircle,
                        title = "API 配置",
                        // 副标题显示当前生效的那一组，省去进入子页面确认。
                        // 必须读配置组而非 SettingsViewModel 的单组状态：
                        // 后者不随配置组切换更新，会显示成另一组的值
                        subtitle = activeProfile?.let { "${it.displayName} · ${it.effectiveModel}" }
                            ?: "未配置",
                        onClick = onNavigateToProvider
                    )
                    SettingsNavItem(
                        icon = Icons.Default.Create,
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
                        icon = Icons.Default.Face,
                        title = "聊天外观",
                        subtitle = "气泡样式、头像与背景",
                        onClick = onNavigateToChatAppearance
                    )
                    SettingsNavItem(
                        icon = Icons.Default.Email,
                        title = "消息显示",
                        subtitle = "时间戳、token 用量等元信息",
                        onClick = onNavigateToMessageDisplay
                    )
                    SettingsSwitchItem(
                        icon = Icons.Default.PlayArrow,
                        title = "流式输出",
                        subtitle = "逐字显示回复，部分中转服务不支持",
                        checked = streamEnabled,
                        onCheckedChange = settingsViewModel::updateStreamEnabled
                    )
                }

                SettingsCategoryTitle("诊断")
                SettingsGroup {
                    SettingsNavItem(
                        icon = Icons.Default.Search,
                        title = "请求日志",
                        subtitle = "查看最近的接口请求与响应，排查失败原因",
                        onClick = onNavigateToRequestLog
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
