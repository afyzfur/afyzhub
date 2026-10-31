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
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.afyzfur.afyzhub.data.image.ImageStore
import com.afyzfur.afyzhub.data.settings.ChatAppearance
import com.afyzfur.afyzhub.ui.theme.ThemePalette
import com.afyzfur.afyzhub.data.settings.ChatAnimation
import com.afyzfur.afyzhub.ui.components.IconContrast
import com.afyzfur.afyzhub.ui.components.IconColorSwatch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.afyzfur.afyzhub.data.settings.ChatBackgroundEffect
import com.afyzfur.afyzhub.ui.components.ChatBackgroundLayer
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
fun AppearanceSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: UiPreferencesViewModel = koinViewModel()
) {
    val prefs by viewModel.preferences.collectAsState()
    val stored = prefs.chatAppearance

    // 拖动中的临时覆盖值。落盘要等抬手（每帧写盘会让滑块跟不上手），
    // 但预览必须立刻变，所以拖动期间由这三个值接管渲染。
    // null 表示没在拖，用落盘值。
    var dimOverride by remember { mutableStateOf<Float?>(null) }
    var blurOverride by remember { mutableStateOf<Float?>(null) }
    var avatarBlurOverride by remember { mutableStateOf<Float?>(null) }
    var inputSeeThroughOverride by remember { mutableStateOf<Float?>(null) }

    // 传给预览的副本。只替换正在拖的那一项，其余仍取落盘值——
    // 一次只可能拖一个滑块，但三个覆盖值各自独立更简单，
    // 不必判断"当前在拖哪个"
    val appearance = stored.copy(
        backgroundDim = dimOverride ?: stored.backgroundDim,
        backgroundBlur = blurOverride ?: stored.backgroundBlur,
        avatarBlur = avatarBlurOverride ?: stored.avatarBlur,
        inputBarSeeThrough = inputSeeThroughOverride ?: stored.inputBarSeeThrough
    )
    val imageError by viewModel.imageError.collectAsState()

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

    // 非空时显示裁剪对话框，值表示裁的是哪张图
    var cropTarget by remember { mutableStateOf<ImageStore.Purpose?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SettingsPageHeader(title = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_cd4994e50397), onNavigateBack = onNavigateBack)

            // 加载失败时提示原因。放在页面顶部而非弹窗：
            // 弹窗会打断操作，而这里的失败通常需要用户换一张图重试
            imageError?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = AppShapeTokens.SettingsGroup,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, end = 4.dp)
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::clearImageError) {
                            Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_6c14bd7f6f9e))
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_5ef29a57bcc9))
                SettingsGroup {
                    SettingsSwitchItem(icon = IconColorSwatch, title = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_9d180a2c7855), subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_2bfbfbbf48f0), checked = prefs.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S, onCheckedChange = viewModel::setDynamicColor)
                    PaletteRow(selected = prefs.palette, enabled = !(prefs.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S), onSelect = viewModel::setPalette)
                }
                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_135f634fede4))
                SettingsGroup {
                    ChatAnimation.entries.forEach { option ->
                        SettingsRadioItem(title = option.label, selected = appearance.animation == option, onClick = { viewModel.setChatAnimation(option) })
                    }
                }
                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_17df7d688f1e))
                SettingsGroup {
                    BubbleStyleItem(
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_146671b0a192),
                        current = appearance.userBubble,
                        onSelect = viewModel::setUserBubble
                    )
                    BubbleStyleItem(
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_e37a7fa52194),
                        current = appearance.assistantBubble,
                        onSelect = viewModel::setAssistantBubble
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_a7da6a59e8a9) +
                        com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_495453826cea),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )

                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_4ceeeb31a55a))
                SettingsGroup {
                    // 排除 NONE：是否显示已由下面两个开关表达，
                    // 这里只选"用什么图"
                    AvatarMode.entries.filter { it != AvatarMode.NONE }.forEach { mode ->
                        SettingsRadioItem(
                            title = mode.label,
                            selected = appearance.avatarMode == mode,
                            onClick = { viewModel.setAvatarMode(mode) }
                        )
                    }
                }

                // 显示开关对所有模式都有效，因此不放在自定义模式的条件里。
                // 与上一组之间需要间距——SettingsGroup 自身不带外边距，
                // 其他页面靠分类标题分隔，这里两组之间没有标题会贴在一起
                if (appearance.showAvatars) {
                    Spacer(Modifier.height(12.dp))
                    SettingsGroup {
                        SettingsSwitchItem(
                            icon = Icons.Default.Person,
                            title = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_97e65da38ee3),
                            subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_3fe82e0db94c),
                            checked = appearance.showUserAvatar,
                            onCheckedChange = viewModel::setShowUserAvatar
                        )
                        SettingsSwitchItem(
                            icon = Icons.Default.Face,
                            title = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_be8297a3745a),
                            subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_0a8d775194af),
                            checked = appearance.showAssistantAvatar,
                            onCheckedChange = viewModel::setShowAssistantAvatar
                        )
                    }
                }

                // 仅在自定义模式下显示图片选择：其他模式下这两行没有作用
                if (appearance.avatarMode == AvatarMode.CUSTOM) {
                    Spacer(Modifier.height(12.dp))
                    SettingsGroup {
                        AvatarPickerRow(
                            title = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_d923295e95ec),
                            path = appearance.userAvatarPath,
                            version = appearance.imageVersion,
                            fallbackIcon = Icons.Default.Person,
                            blur = appearance.avatarBlur,
                            onPick = { userAvatarPicker.launch("image/*") },
                            onEdit = { cropTarget = ImageStore.Purpose.USER_AVATAR },
                            onClear = viewModel::clearUserAvatar
                        )
                        AvatarPickerRow(
                            title = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_5e89305738ef),
                            path = appearance.assistantAvatarPath,
                            version = appearance.imageVersion,
                            fallbackIcon = Icons.Default.Face,
                            blur = appearance.avatarBlur,
                            onPick = { assistantAvatarPicker.launch("image/*") },
                            onEdit = { cropTarget = ImageStore.Purpose.ASSISTANT_AVATAR },
                            onClear = viewModel::clearAssistantAvatar
                        )
                        // 模糊对两侧头像统一生效：分开设两个值，界面上
                        // 要多两个滑块，而想让两侧模糊程度不同的需求罕见
                        SettingsItemDivider()
                        PercentSlider(
                            title = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_afdfbfeab7ad),
                            value = appearance.avatarBlur,
                            hint = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_3ae10b7249db),
                            onChange = viewModel::setAvatarBlur,
                            onPreview = { avatarBlurOverride = it }
                        )
                    }
                }

                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_7e9e1587a765))
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
                    Spacer(Modifier.height(12.dp))
                    SettingsGroup {
                        BackgroundPreview(
                            appearance = appearance,
                            onReplace = { backgroundPicker.launch("image/*") },
                            onEdit = { cropTarget = ImageStore.Purpose.CHAT_BACKGROUND },
                            onClear = viewModel::clearBackground
                        )
                        SettingsItemDivider()
                        // 效果选择放在滑块之前：先决定用哪种处理，
                        // 再调它的强度，顺序与思考过程一致
                        ChatBackgroundEffect.entries.forEach { effect ->
                            SettingsRadioItem(
                                title = effect.label,
                                selected = appearance.backgroundEffect == effect,
                                onClick = { viewModel.setBackgroundEffect(effect) }
                            )
                        }
                        // 只显示当前效果实际用到的滑块。全都显示会让人
                        // 以为调了有用，而那个值此刻并不参与渲染
                        if (appearance.backgroundEffect.usesDim) {
                            SettingsItemDivider()
                            PercentSlider(
                                title = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_4e83125c4a66),
                                value = appearance.backgroundDim,
                                hint = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_ab34a29e9ea6),
                                onChange = viewModel::setBackgroundDim,
                                onPreview = { dimOverride = it }
                            )
                        }
                        if (appearance.backgroundEffect.usesBlur) {
                            SettingsItemDivider()
                            PercentSlider(
                                title = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_47d168d38775),
                                value = appearance.backgroundBlur,
                                hint = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_ea744bc3ff90),
                                onChange = viewModel::setBackgroundBlur,
                                onPreview = { blurOverride = it }
                            )
                        }
                    }
                }

                // 透明度开关独立成组：即使没有背景图也可能想让顶栏
                // 与页面背景融为一体，因此不限定在有图时才显示
                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_e9944630f3e4))
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Default.KeyboardArrowUp,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_b0868e020827),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_f7b1792e175b),
                        checked = appearance.transparentTopBar,
                        onCheckedChange = viewModel::setTransparentTopBar
                    )
                    SettingsSwitchItem(
                        icon = Icons.Default.KeyboardArrowDown,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_8ebe97f3d50c),
                        subtitle = if (appearance.transparentInputBar) {
                            com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_a1c9cf32c22d)
                        } else {
                            com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_5fba648444d4)
                        },
                        checked = appearance.transparentInputBar,
                        onCheckedChange = viewModel::setTransparentInputBar
                    )
                    // 样式与透明无关，始终可选：悬浮式在不透明时也
                    // 是一种不同的观感
                    SettingsItemDivider()
                    SettingsSwitchItem(
                        icon = IconContrast,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_461a52654d0d),
                        subtitle = if (!appearance.inputBarDeepSeeThrough) {
                            com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_3730a9b6e216)
                        } else if (appearance.inputBarFloating) {
                            com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_fe4469fdb011)
                        } else {
                            com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_d2908949f847)
                        },
                        checked = appearance.inputBarFloating,
                        onCheckedChange = viewModel::setInputBarFloating,
                        enabled = appearance.inputBarDeepSeeThrough
                    )
                    // 强度只在透明开启时显示：关着的时候调它看不出
                    // 任何变化，留在界面上只会让人以为没生效
                    if (appearance.transparentInputBar) {
                        SettingsItemDivider()
                        PercentSlider(
                            title = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_c5e26944beba),
                            value = appearance.inputBarSeeThrough,
                            hint = if (appearance.inputBarDeepSeeThrough) {
                                com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_284480e6290c)
                            } else {
                                com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_d571d9c547f4)
                            },
                            onChange = viewModel::setInputBarSeeThrough,
                            onPreview = { inputSeeThroughOverride = it }
                        )
                        SettingsItemDivider()
                        SettingsSwitchItem(
                            icon = IconContrast,
                            title = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_4d2ad3dcb578),
                            subtitle = if (appearance.inputBarDeepSeeThrough) {
                                com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_3cd58a38bcf7)
                            } else {
                                com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_bdba71f57166)
                            },
                            checked = appearance.inputBarDeepSeeThrough,
                            onCheckedChange = viewModel::setInputBarDeepSeeThrough
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
    // 裁剪对话框。三张图共用一个，用 cropTarget 区分裁的是哪张
    cropTarget?.let { purpose ->
        val path = when (purpose) {
            ImageStore.Purpose.USER_AVATAR -> appearance.userAvatarPath
            ImageStore.Purpose.ASSISTANT_AVATAR -> appearance.assistantAvatarPath
            ImageStore.Purpose.CHAT_BACKGROUND -> appearance.backgroundPath
        }
        if (path.isNullOrBlank()) {
            cropTarget = null
        } else {
            ImageCropDialog(
                path = path,
                version = appearance.imageVersion,
                onConfirm = { l, t, r, b ->
                    viewModel.cropImage(purpose, l, t, r, b)
                    cropTarget = null
                },
                onDismiss = { cropTarget = null }
            )
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
                    // 涟漪要跟随胶囊形状，必须走 Surface 的 onClick
                    onClick = { onSelect(style) }
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
    /** 头像模糊强度，与聊天里实际显示的一致 */
    blur: Float,
    onPick: () -> Unit,
    onEdit: () -> Unit,
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
                    // 缩略图也上模糊，所见即聊天里的样子
                    blur = blur,
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
                text = if (path.isNullOrBlank()) com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_4f528c177134) else com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_d65e1a1ba803),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!path.isNullOrBlank()) {
            TextButton(onClick = onEdit) { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_f06f360f9706)) }
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_2f752c005ec5),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 背景预览，附更换与移除操作 */
@Composable
private fun BackgroundPreview(
    appearance: ChatAppearance,
    onReplace: () -> Unit,
    onEdit: () -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        // 预览直接用聊天页的那个背景层组件，因此遮罩浓度、模糊强度
        // 的调整在这里立刻可见，且与进入聊天后看到的完全一致
        ChatBackgroundLayer(
            appearance = appearance,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(AppShapeTokens.SettingsGroup)
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onReplace) { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_b1a3fede2cfc)) }
            TextButton(onClick = onEdit) { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_f06f360f9706)) }
            TextButton(onClick = onClear) { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_2f752c005ec5)) }
        }
    }
}

/**
 * 通用的百分比滑块。
 *
 * 原先是专用的 DimSlider，加了模糊之后需要第二个同样形态的滑块，
 * 与其复制一遍不如把标题与说明抽成参数。
 */
@Composable
private fun PercentSlider(
    title: String,
    value: Float,
    hint: String,
    onChange: (Float) -> Unit,
    /**
     * 拖动中的实时值，抬手后传 null 表示交回落盘值。
     *
     * 与 onChange 分开是因为两者的代价不同：这个每帧都调、只改内存，
     * onChange 一次拖动只调一次、要写磁盘。之前两者合一，每帧写盘让
     * 滑块跟不上手；只在抬手时调则预览不实时。
     */
    onPreview: (Float?) -> Unit = {}
) {
    // 拖动期间 thumb 的位置由本地状态驱动。
    //
    // 此前 onValueChange 直接连到 ViewModel 的 setter，而那条链是
    // 协程 -> dataStore.edit 写磁盘 -> Flow 发新值 -> 整页重组，滑块的
    // value 又取自这个回来的值。一次拖动上百帧就是上百次磁盘写入，
    // thumb 得等写盘完成才跟上，于是不跟手、要划好几次；0% 时更明显，
    // 因为相邻几帧的值相同、DataStore 去重不发新值，thumb 干脆不动。
    var local by remember { mutableFloatStateOf(value) }

    // pending 表示「本地有改动还没落盘回来」，而非「手指还在滑块上」。
    // 用后者会让抬手瞬间就把显示权交回外部值，而那时落盘尚未完成，
    // thumb 会先跳回原位再跳到新位置——就是松手时那下卡顿。
    // 取值规则见 SliderValueSource.kt，那里有测试钉住
    val pending = !isSettled(external = value, local = local)

    if (shouldAdoptExternal(external = value, local = local, pending = pending)) {
        local = value
    }
    val shown = sliderDisplayValue(external = value, local = local, pending = pending)

    // 落盘完成后才撤掉预览覆盖，把渲染交回落盘值。
    // 放在这里而非 onValueChangeFinished：那一刻落盘还没回来，
    // 撤掉会让预览先闪回旧值
    LaunchedEffect(pending) {
        if (!pending) onPreview(null)
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(shown * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = shown,
            onValueChange = {
                // 拖动期间只更新本地状态并向上报，不写磁盘。
                // 预览据此实时渲染，落盘留到抬手时做一次
                local = it
                onPreview(it)
            },
            onValueChangeFinished = {
                onChange(local)
                // 不在这里撤覆盖：落盘还没回来。交给上面的
                // LaunchedEffect 在落盘完成后处理
            }
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
