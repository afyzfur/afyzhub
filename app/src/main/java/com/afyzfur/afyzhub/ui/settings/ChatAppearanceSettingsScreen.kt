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
import com.afyzfur.afyzhub.ui.components.IconContrast
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
fun ChatAppearanceSettingsScreen(
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
            SettingsPageHeader(title = "聊天外观", onNavigateBack = onNavigateBack)

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
                            Text("关闭")
                        }
                    }
                }
            }

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
                            title = "显示我的头像",
                            subtitle = "在抽屉顶部与自己的消息旁显示",
                            checked = appearance.showUserAvatar,
                            onCheckedChange = viewModel::setShowUserAvatar
                        )
                        SettingsSwitchItem(
                            icon = Icons.Default.Face,
                            title = "显示助手头像",
                            subtitle = "在助手消息旁显示模型厂商图标",
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
                            title = "我的头像",
                            path = appearance.userAvatarPath,
                            version = appearance.imageVersion,
                            fallbackIcon = Icons.Default.Person,
                            blur = appearance.avatarBlur,
                            onPick = { userAvatarPicker.launch("image/*") },
                            onEdit = { cropTarget = ImageStore.Purpose.USER_AVATAR },
                            onClear = viewModel::clearUserAvatar
                        )
                        AvatarPickerRow(
                            title = "助手头像",
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
                            title = "头像模糊",
                            value = appearance.avatarBlur,
                            hint = "让画面复杂的头像不那么抢眼，0% 为不处理",
                            onChange = viewModel::setAvatarBlur,
                            onPreview = { avatarBlurOverride = it }
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
                                title = "遮罩浓度",
                                value = appearance.backgroundDim,
                                hint = "压暗背景，保留画面细节",
                                onChange = viewModel::setBackgroundDim,
                                onPreview = { dimOverride = it }
                            )
                        }
                        if (appearance.backgroundEffect.usesBlur) {
                            SettingsItemDivider()
                            PercentSlider(
                                title = "模糊强度",
                                value = appearance.backgroundBlur,
                                hint = "抹掉画面细节，保留色调与明暗",
                                onChange = viewModel::setBackgroundBlur,
                                onPreview = { blurOverride = it }
                            )
                        }
                    }
                }

                // 透明度开关独立成组：即使没有背景图也可能想让顶栏
                // 与页面背景融为一体，因此不限定在有图时才显示
                SettingsCategoryTitle("栏目透明度")
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Default.KeyboardArrowUp,
                        title = "顶栏透明",
                        subtitle = "关闭后顶栏铺主题色，会遮住背景图上部",
                        checked = appearance.transparentTopBar,
                        onCheckedChange = viewModel::setTransparentTopBar
                    )
                    SettingsSwitchItem(
                        icon = Icons.Default.KeyboardArrowDown,
                        title = "输入栏透明",
                        subtitle = if (appearance.transparentInputBar) {
                            "背景会透上来，可用下面的强度调节"
                        } else {
                            "开启后背景图会透上来"
                        },
                        checked = appearance.transparentInputBar,
                        onCheckedChange = viewModel::setTransparentInputBar
                    )
                    // 样式与透明无关，始终可选：悬浮式在不透明时也
                    // 是一种不同的观感
                    SettingsItemDivider()
                    SettingsSwitchItem(
                        icon = IconContrast,
                        title = "输入栏悬浮",
                        subtitle = if (appearance.inputBarFloating) {
                            "四周留出间距，四角全圆，与页面分离"
                        } else {
                            "当前铺满底部，开启后四周留出间距"
                        },
                        checked = appearance.inputBarFloating,
                        onCheckedChange = viewModel::setInputBarFloating
                    )
                    // 强度只在透明开启时显示：关着的时候调它看不出
                    // 任何变化，留在界面上只会让人以为没生效
                    if (appearance.transparentInputBar) {
                        SettingsItemDivider()
                        PercentSlider(
                            title = "透视强度",
                            value = appearance.inputBarSeeThrough,
                            hint = if (appearance.inputBarDeepSeeThrough) {
                                "越高越透。增强透视下输入的文字也会跟着变淡"
                            } else {
                                "越高越透。悬浮样式下露出的背景更多，调低一些就够"
                            },
                            onChange = viewModel::setInputBarSeeThrough,
                            onPreview = { inputSeeThroughOverride = it }
                        )
                        SettingsItemDivider()
                        SettingsSwitchItem(
                            icon = IconContrast,
                            title = "增强透视",
                            subtitle = if (appearance.inputBarDeepSeeThrough) {
                                "连文字一起透，能看见输入栏后面的消息"
                            } else {
                                "当前只透出背景，开启后可看见后面的消息"
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
                text = if (path.isNullOrBlank()) "点击选择图片" else "点击更换",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!path.isNullOrBlank()) {
            TextButton(onClick = onEdit) { Text("裁剪") }
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
            TextButton(onClick = onReplace) { Text("更换") }
            TextButton(onClick = onEdit) { Text("裁剪") }
            TextButton(onClick = onClear) { Text("移除") }
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
