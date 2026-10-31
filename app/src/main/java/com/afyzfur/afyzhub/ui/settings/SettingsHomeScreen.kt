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
import com.afyzfur.afyzhub.data.settings.ColorMode
import com.afyzfur.afyzhub.ui.components.IconContrast
import com.afyzfur.afyzhub.ui.components.IconBolt
import com.afyzfur.afyzhub.ui.components.IconChatBubble
import com.afyzfur.afyzhub.ui.components.IconDocument
import com.afyzfur.afyzhub.ui.components.IconKey
import com.afyzfur.afyzhub.ui.components.IconPalette
import com.afyzfur.afyzhub.ui.components.IconTextLines
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
    onNavigateToRequestLog: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToBrowser: () -> Unit,
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
    val webSearchEnabled by settingsViewModel.webSearchEnabled.collectAsState()


    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SettingsPageHeader(title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_7debf9cb0372), onNavigateBack = onNavigateBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_1a0fdce8f88f))

                SettingsGroup {
                    // 下拉而非循环点击：循环时点之前看不到有哪些选项，
                    // 而且从"深色"回到"跟随系统"要连点两次
                    SettingsDropdownItem(
                        icon = IconContrast,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_b9f0a68d2400),
                        subtitle = prefs.colorMode.label,
                        options = ColorMode.entries,
                        selected = prefs.colorMode,
                        label = { it.label },
                        onSelect = uiPreferencesViewModel::setColorMode
                    )
                    SettingsNavItem(
                        icon = IconPalette,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_cd4994e50397),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_7f2600022639),
                        onClick = onNavigateToAppearance
                    )
                }

                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_143c45e7f0f7))

                SettingsGroup {
                    SettingsNavItem(
                        icon = IconKey,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_64f968b0cf1d),
                        // 副标题显示当前生效的那一组，省去进入子页面确认。
                        // 必须读配置组而非 SettingsViewModel 的单组状态：
                        // 后者不随配置组切换更新，会显示成另一组的值
                        subtitle = activeProfile?.let { "${it.displayName} · ${it.effectiveModel}" }
                            ?: com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_63595e95b708),
                        onClick = onNavigateToProvider
                    )
                    SettingsNavItem(
                        icon = IconBolt,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_5d4357a73cd4),
                        subtitle = if (prefs.quickPrompts.isEmpty()) {
                            com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_55a04b58cd2c)
                        } else {
                            com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.items_count, prefs.quickPrompts.size)
                        },
                        onClick = onNavigateToQuickPrompts
                    )
                }

                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_00d4cbafeabc))
                SettingsGroup {
                    SettingsNavItem(
                        icon = IconTextLines,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_76598035e91b),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_a50e255aa5c0),
                        onClick = onNavigateToMessageDisplay
                    )
                    SettingsSwitchItem(
                        icon = Icons.Default.PlayArrow,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_5b6aae8c30dd),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_8f7adf702cd8),
                        checked = streamEnabled,
                        onCheckedChange = settingsViewModel::updateStreamEnabled
                    )
                    SettingsNavItem(
                        icon = Icons.Default.Search,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_2e345cfee36f),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_f824f671818d),
                        onClick = onNavigateToBrowser
                    )
                }

                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_e0b478941707))
                SettingsGroup {
                    SettingsNavItem(
                        icon = IconDocument,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_d9d4d4e7ddce),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_d9eb3c32dc2d),
                        onClick = onNavigateToRequestLog
                    )
                }

                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_bed172efc953))
                SettingsGroup {
                    SettingsNavItem(
                        icon = Icons.Default.Info,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_a53e89da8563),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_ff3d35f0cdcf),
                        onClick = onNavigateToAbout
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
