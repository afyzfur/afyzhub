package com.afyzfur.afyzhub.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.afyzfur.afyzhub.ui.components.IconColorSwatch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.data.settings.ColorMode
import com.afyzfur.afyzhub.ui.theme.ThemePalette
import org.koin.androidx.compose.koinViewModel

/**
 * 预设配色选择器。
 *
 * 用色块而非文字列表：配色是视觉属性，直接看颜色比读"静蓝""松绿"这类
 * 名称更快判断。名称仍显示在色块下方，供无法分辨颜色的用户识别。
 *
 * [enabled] 为 false 时降低不透明度并屏蔽点击，表示动态取色已接管配色。
 */
@Composable
private fun PaletteRow(
    selected: ThemePalette,
    enabled: Boolean,
    onSelect: (ThemePalette) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ThemePalette.entries.forEach { palette ->
            val isSelected = palette == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(if (enabled) 1f else 0.4f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        // 点击放在 clip 之后：加在外层 Column 上时
                        // 涟漪会覆盖色块加名称的整个矩形，形状不对
                        .clickable(enabled = enabled) { onSelect(palette) }
                        .background(palette.swatch)
                        .then(
                            // 选中态用主题色描边而非勾选图标：
                            // 勾选图标在深色块上可能看不清
                            if (isSelected) {
                                Modifier.border(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                            } else {
                                Modifier
                            }
                        )
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = palette.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

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
                SettingsCategoryTitle("配色")

                SettingsGroup {
                    SettingsSwitchItem(
                        icon = IconColorSwatch,
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

                // 动态取色开启时预设配色不生效，此时禁用整组而非隐藏——
                // 隐藏会让用户以为功能不存在
                val paletteEnabled = !(prefs.dynamicColor && dynamicColorSupported)

                SettingsCategoryTitle("预设配色")
                SettingsGroup {
                    PaletteRow(
                        selected = prefs.palette,
                        enabled = paletteEnabled,
                        onSelect = viewModel::setPalette
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (paletteEnabled) {
                        "各套配色由同一色板旋转色相派生，明暗层次一致。"
                    } else {
                        "动态取色已开启，配色来自系统壁纸。关闭后可选择预设配色。"
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
