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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
            SettingsPageHeader(title = "消息显示", onNavigateBack = onNavigateBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsCategoryTitle("基础")

                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Default.AccessTime,
                        title = "时间戳",
                        subtitle = "同日只显示时分，跨日带日期",
                        checked = display.showTimestamp,
                        onCheckedChange = {
                            viewModel.setMessageDisplay(display.copy(showTimestamp = it))
                        }
                    )
                    SettingsSwitchItem(
                        icon = Icons.Default.TouchApp,
                        title = "操作按钮",
                        subtitle = "复制、重新生成等",
                        checked = display.showActions,
                        onCheckedChange = {
                            viewModel.setMessageDisplay(display.copy(showActions = it))
                        }
                    )
                    SettingsSwitchItem(
                        icon = Icons.Default.Memory,
                        title = "模型名称",
                        subtitle = "标注每条回复由哪个模型生成",
                        checked = display.showModelName,
                        onCheckedChange = {
                            viewModel.setMessageDisplay(display.copy(showModelName = it))
                        }
                    )
                }

                SettingsCategoryTitle("性能数据")

                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Default.DataUsage,
                        title = "Token 用量",
                        subtitle = "输入与输出的 token 数",
                        checked = display.showTokenUsage,
                        onCheckedChange = {
                            viewModel.setMessageDisplay(display.copy(showTokenUsage = it))
                        }
                    )
                    SettingsSwitchItem(
                        icon = Icons.Default.Speed,
                        title = "生成速度",
                        subtitle = "每秒输出的 token 数",
                        checked = display.showSpeed,
                        onCheckedChange = {
                            viewModel.setMessageDisplay(display.copy(showSpeed = it))
                        }
                    )
                    SettingsSwitchItem(
                        icon = Icons.Default.Timer,
                        title = "耗时",
                        subtitle = "从发出请求到回复结束",
                        checked = display.showLatency,
                        onCheckedChange = {
                            viewModel.setMessageDisplay(display.copy(showLatency = it))
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "性能数据仅对开启后产生的新消息可用，" +
                        "此前的历史消息不会补齐。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
