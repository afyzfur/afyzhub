package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.afyzfur.afyzhub.ui.components.IconClock
import com.afyzfur.afyzhub.ui.components.IconTag
import com.afyzfur.afyzhub.ui.components.IconBarChart
import com.afyzfur.afyzhub.ui.components.IconSpeed
import com.afyzfur.afyzhub.ui.components.IconStopwatch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

/**
 * 消息显示子页面：元信息开关组。
 *
 * 分成"基础"与"性能数据"两组，因为后者依赖阶段 6 才落地的 Room 字段，
 * 现阶段打开也只对新消息生效。分组能让这个差别在界面上可见。
 */
@Composable
fun MessageDisplaySettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: UiPreferencesViewModel = koinViewModel()
) {
    val prefs by viewModel.preferences.collectAsState()
    val display = prefs.messageDisplay

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SettingsPageHeader(title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_76598035e91b), onNavigateBack = onNavigateBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_5f83e7f6a1ee))

                SettingsGroup {
                    SettingsSwitchItem(
                        icon = IconClock,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_944257fc0daf),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_bacfe515acb9),
                        checked = display.showTimestamp,
                        onCheckedChange = {
                            viewModel.setMessageDisplay(display.copy(showTimestamp = it))
                        }
                    )
                    SettingsSwitchItem(
                        icon = IconTag,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_38719c9968ae),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_41555ceece96),
                        checked = display.showModelName,
                        onCheckedChange = {
                            viewModel.setMessageDisplay(display.copy(showModelName = it))
                        }
                    )
                }

                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_c1671196957f))

                SettingsGroup {
                    SettingsSwitchItem(
                        icon = IconBarChart,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_a075f5fc3d77),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_69a34ae61a54),
                        checked = display.showTokenUsage,
                        onCheckedChange = {
                            viewModel.setMessageDisplay(display.copy(showTokenUsage = it))
                        }
                    )
                    SettingsSwitchItem(
                        icon = IconSpeed,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_bd4d4ac87d76),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_8c62cbe70498),
                        checked = display.showSpeed,
                        onCheckedChange = {
                            viewModel.setMessageDisplay(display.copy(showSpeed = it))
                        }
                    )
                    SettingsSwitchItem(
                        icon = IconStopwatch,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_a9704e1997b9),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_bd8c2ba49b52),
                        checked = display.showLatency,
                        onCheckedChange = {
                            viewModel.setMessageDisplay(display.copy(showLatency = it))
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_f124c69c1402) +
                        com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_f6439d8146e5),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
