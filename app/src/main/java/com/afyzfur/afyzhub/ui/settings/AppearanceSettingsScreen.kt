package com.afyzfur.afyzhub.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.data.settings.ColorMode
import org.koin.androidx.compose.koinViewModel

/**
 * 外观子页面：颜色模式与动态取色。
 */
@Composable
fun AppearanceSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: UiPreferencesViewModel = koinViewModel()
) {
    val prefs by viewModel.preferences.collectAsState()
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SettingsPageHeader(title = "外观", onNavigateBack = onNavigateBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsCategoryTitle("颜色模式")

                SettingsGroup {
                    ColorMode.entries.forEach { mode ->
                        SettingsRadioItem(
                            title = mode.label,
                            selected = prefs.colorMode == mode,
                            onClick = { viewModel.setColorMode(mode) }
                        )
                    }
                }

                SettingsCategoryTitle("配色")

                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Default.Favorite,
                        title = "动态取色",
                        subtitle = if (dynamicColorSupported) {
                            "跟随系统壁纸生成配色"
                        } else {
                            // Android 12 以下没有 Material You，说明原因而非静默禁用
                            "需要 Android 12 及以上，当前设备不支持"
                        },
                        checked = prefs.dynamicColor && dynamicColorSupported,
                        onCheckedChange = { viewModel.setDynamicColor(it) }
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = if (dynamicColorSupported) {
                        "关闭动态取色后使用以品牌色为基准的配色。"
                    } else {
                        "当前使用以品牌色为基准的配色。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
