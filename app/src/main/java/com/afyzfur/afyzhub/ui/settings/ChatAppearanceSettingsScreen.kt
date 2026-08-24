package com.afyzfur.afyzhub.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.data.settings.AvatarMode
import com.afyzfur.afyzhub.data.settings.BubbleStyle
import com.afyzfur.afyzhub.data.settings.ChatBackgroundMode
import com.afyzfur.afyzhub.ui.components.LocalImage
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

/**
 * 聊天外观子页面：气泡样式、头像与背景。
 *
 * 与「外观」页分开：那页管的是全局配色，这页只影响消息列表的呈现。
 * 合并会让一页过长，且两类设置的调整频率不同。
 */
@Composable
fun ChatAppearanceSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: UiPreferencesViewModel = koinViewModel()
) {
    val prefs by viewModel.preferences.collectAsState()
    val appearance = prefs.chatAppearance

    // 用 GetContent 而非 OpenDocument：只需一次性读取内容用于复制，
    // 不需要长期持有 URI 权限（图片已复制到私有目录）
    val userAvatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(viewModel::pickUserAvatar) }

    val assistantAvatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(viewModel::pickAssistantAvatar) }

    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(viewModel::pickBackground) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SettingsPageHeader(title = "聊天外观", onNavigateBack = onNavigateBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsCategoryTitle("消息样式")
                SettingsGroup {
                    BubbleStyleItem(
                        title = "用户消息",
                        current = appearance.userBubble,
                        onSelect = viewModel::setUserBubble
                    )
                    BubbleStyleItem(
                        title = "助手消息",
                        current = appearance.assistantBubble,
                        onSelect = viewModel::setAssistantBubble
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "助手消息无气泡时可占满宽度，代码块与表格更易读；" +
                        "有气泡时两边样式统一。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )

                SettingsCategoryTitle("头像")
                SettingsGroup {
                    AvatarMode.entries.forEach { mode ->
                        SettingsRadioItem(
                            title = mode.label,
                            selected = appearance.avatarMode == mode,
                            onClick = { viewModel.setAvatarMode(mode) }
                        )
                    }
                }

                // 仅在自定义模式下显示图片选择：其他模式下这两行没有作用
                if (appearance.avatarMode == AvatarMode.CUSTOM) {
                    SettingsGroup {
                        AvatarPickerRow(
                            title = "我的头像",
                            path = appearance.userAvatarPath,
                            version = appearance.imageVersion,
                            fallbackIcon = Icons.Default.Person,
                            onPick = { userAvatarPicker.launch("image/*") },
                            onClear = viewModel::clearUserAvatar
                        )
                        AvatarPickerRow(
                            title = "助手头像",
                            path = appearance.assistantAvatarPath,
                            version = appearance.imageVersion,
                            fallbackIcon = Icons.Default.Face,
                            onPick = { assistantAvatarPicker.launch("image/*") },
                            onClear = viewModel::clearAssistantAvatar
                        )
                    }
                }

                SettingsCategoryTitle("聊天背景")
                SettingsGroup {
                    ChatBackgroundMode.entries.forEach { mode ->
                        SettingsRadioItem(
                            title = mode.label,
                            selected = appearance.backgroundMode == mode,
                            onClick = {
                                // 选择图片模式但还没有图时直接拉起选择器，
                                // 否则界面看不出任何变化
                                if (
                                    mode == ChatBackgroundMode.IMAGE &&
                                    appearance.backgroundPath.isNullOrBlank()
                                ) {
                                    backgroundPicker.launch("image/*")
                                } else {
                                    viewModel.setBackgroundMode(mode)
                                }
                            }
                        )
                    }
                }

                if (appearance.hasBackgroundImage) {
                    SettingsGroup {
                        BackgroundPreview(
                            path = appearance.backgroundPath!!,
                            version = appearance.imageVersion,
                            onReplace = { backgroundPicker.launch("image/*") },
                            onClear = viewModel::clearBackground
                        )
                        DimSlider(
                            value = appearance.backgroundDim,
                            onChange = viewModel::setBackgroundDim
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * 气泡样式的两选一行。
 *
 * 用并排的两个文字按钮而非开关：开关需要用户推断"开"对应哪种样式，
 * 而两个具名选项直接可读。
 */
@Composable
private fun BubbleStyleItem(
    title: String,
    current: BubbleStyle,
    onSelect: (BubbleStyle) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BubbleStyle.entries.forEach { style ->
                val isSelected = style == current
                Surface(
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    shape = AppShapeTokens.Pill,
                    modifier = Modifier.clickable { onSelect(style) }
                ) {
                    Text(
                        text = style.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/** 头像行：显示当前头像（或回退图标）、点击更换、右侧清除 */
@Composable
private fun AvatarPickerRow(
    title: String,
    path: String?,
    version: Long,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        ) {
            if (!path.isNullOrBlank()) {
                LocalImage(
                    path = path,
                    version = version,
                    contentDescription = title,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.size(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (path.isNullOrBlank()) "点击选择图片" else "点击更换",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!path.isNullOrBlank()) {
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "移除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 背景预览，附更换与移除操作 */
@Composable
private fun BackgroundPreview(
    path: String,
    version: Long,
    onReplace: () -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        LocalImage(
            path = path,
            version = version,
            contentDescription = "当前背景",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(AppShapeTokens.SettingsGroup)
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onReplace) { Text("更换") }
            TextButton(onClick = onClear) { Text("移除") }
        }
    }
}

/** 背景遮罩浓度调节 */
@Composable
private fun DimSlider(value: Float, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "遮罩浓度",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(value * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(value = value, onValueChange = onChange)
        Text(
            text = "背景图会降低文字对比度，提高遮罩浓度可改善可读性。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
