package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.BorderStroke
import com.afyzfur.afyzhub.ui.components.IconThinking
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.afyzfur.afyzhub.domain.model.ThinkingEffort
import com.afyzfur.afyzhub.ui.components.ModelIcon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens

/**
 * 输入区。
 *
 * 与改版前的差别：原实现是 OutlinedTextField + 独立 FAB 并排，外层 Surface 带 8dp 阴影，
 * 是 Material 2 的语言。现在整体收进一个圆角容器，内部上下分层——
 * 上层文本输入，下层功能行。阴影去掉，靠色阶与页面背景区分。
 *
 * 容器铺满宽度、只圆上方两角、贴住屏幕底边。此前四周留 12dp 外边距做悬浮效果，
 * 但容器色（surfaceContainerHigh）与页面色（surfaceContainer）相近，
 * 四周露出的页面背景在视觉上成了一圈多余的浅色描边。铺满则没有这个问题。
 *
 * 功能行当前只放提供商标识与发送按钮。联网搜索、思考模式、附件等按钮
 * 需要对应功能落地后再加，先不放不可用的占位图标。
 */
@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    providerLabel: String,
    isLoading: Boolean,
    /** 进行中的具体阶段文字。为空时显示提供商名 */
    statusLabel: String = "",
    /** 点击左下角的模型区域，打开模型切换页 */
    onPickModel: () -> Unit = {},
    /** 当前模型名，用于显示模型图标 */
    modelName: String = "",
    /** 当前思考程度 */
    thinkingEffort: ThinkingEffort = ThinkingEffort.OFF,
    /** 点击思考程度按钮时的回调 */
    onCycleThinkingEffort: () -> Unit = {},
    transparent: Boolean = false,
    /** 透视强度，0 为不透明。仅在 [transparent] 为真时生效 */
    seeThrough: Float = 0.35f,
    /**
     * 是否悬浮样式。
     *
     * 悬浮式四周留边、四角全圆、不贴屏幕底边；通栏式铺满宽度、只圆
     * 上方两角。悬浮式露出的背景更多，透视的效果也更明显。
     */
    floating: Boolean = false,
    /**
     * 是否增强透视。
     *
     * 关闭时只让底色半透，文字与图标保持不透明，透上来的是背景图；
     * 开启时整层一起半透，能看见背后压着的消息。
     */
    deepSeeThrough: Boolean = false,
    /**
     * 报告输入栏本体的高度（不含悬浮式的外留白）。
     *
     * 由本组件报告而非在调用侧反推：调用侧只能量到"错误条 + 撤回条 +
     * 输入栏"的总高，而且悬浮式的外留白与导航栏内边距都混在里面，
     * 减不干净。列表要让位的恰恰只有本体这一段
     */
    onBodyHeightChange: (Dp) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val canSend = value.isNotBlank() && !isLoading

    // 强度是"透多少"，alpha 取其反。不设下界：用户把强度拉满时
    // 就该是全透
    val alpha = if (transparent) (1f - seeThrough).coerceIn(0f, 1f) else 1f

    // 两档透视作用在不同层次上：
    //  - 普通档把 alpha 给底色，文字与图标不受影响，始终清晰
    //  - 增强档把 alpha 给整层，文字随之半透，背后的消息才看得见
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh.let {
        if (deepSeeThrough) it else it.copy(alpha = alpha)
    }
    val layerAlpha = if (deepSeeThrough) alpha else 1f

    val containerShape = if (floating) {
        AppShapeTokens.FloatingInputContainer
    } else {
        AppShapeTokens.InputContainer
    }

    // 不用 Surface，改为 Box + background。
    //
    // 上一版把 tonalElevation 与 shadowElevation 都归零了，那圈线仍在，
    // 说明它不是抬升色调那一层。Surface 除色调外还管 contentColor、
    // 点击拦截、边界裁剪，具体哪一步在边缘多画了一层我看不到。
    //
    // 而输入栏用不上 Surface 的任何附加行为——它只需要一块带圆角的
    // 底色。background(color, shape) 就是单纯填一次色，绘制里没有
    // 任何自动叠加的层，也就没有可能出现描边。
    Box(
        modifier = modifier
            .fillMaxWidth()
            // 增强档下整块（含文字与图标）一起半透。graphicsLayer 把子树
            // 先画到一个离屏层再按 alpha 合成，所以文字也会透。
            //
            // 恒定套一层而非按开关加减 modifier：链的结构一变就要重建
            // 绘制节点，切换时会闪。alpha 为 1 时没有视觉效果。
            //
            // 若改为逐个给文字设颜色 alpha，每处都要改且容易漏，
            // 而且叠加处的透明度会累积，看起来深浅不一
            .graphicsLayer { this.alpha = layerAlpha }
            // 悬浮式的外边距加在容器之外，让背景从四周透出来。
            // 导航栏留白也在这里：通栏式要让容器背景延伸到屏幕底边，
            // 所以留白在容器内部；悬浮式则整块都要抬离底边
            .then(
                if (floating) {
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                } else {
                    Modifier
                }
            )
            // 底色排在 padding 之后、graphicsLayer 之内，两个位置都有讲究：
            //  - 在 padding 之后：modifier 链从外往内套，排在 padding 前面
            //    会把外边距一起填上色，悬浮式那 12dp 留白就没了。原来用
            //    Surface 时不受影响，因为它在内部画底色、不看链的位置
            //  - 在 graphicsLayer 之内：底色要跟着整层一起半透，否则增强档
            //    下只有文字在透、底色仍是实的
            .background(surfaceColor, containerShape)
            // 量在 background 这一层：链上排在 padding 之后，
            // 量到的就是刨去外留白的本体尺寸
            .onSizeChanged { size ->
                onBodyHeightChange(with(density) { size.height.toDp() })
            }
    ) {
        // 导航栏内边距加在容器内部，使容器背景延伸到屏幕底边，
        // 而内容不被系统手势区域压住
        Column(
            modifier = Modifier
                .padding(top = 4.dp)
                // 悬浮式的留白已加在容器外层，这里再加一次会多出一段空白
                .then(if (floating) Modifier else Modifier.navigationBarsPadding())
        ) {

            // 上层：文本输入。用 BasicTextField 而非 TextField，
            // 后者自带的 label / indicator / 内边距无法与容器化布局对齐
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = !isLoading,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = "输入消息",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            )

            // 下层：功能行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // 左右边距不相等是刻意的。文本的 20dp 加在 BasicTextField 上，
                // 那是文字基线的边距；而发送键是 44dp 的圆，圆的视觉边缘比
                // 它的布局盒边缘更靠内。两侧都给 20dp 时，圆看起来会比文字
                // 更往里缩。右侧收到 12dp 做光学对齐，让圆的最右点与文字右
                // 边缘看齐
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, bottom = 8.dp)
            ) {
                // 最左是模型图标，其次思考程度，与 statusLabel 同一行。
                // 把"以什么模型、想多久"放在离发送键最远的一侧：
                // 它们是发送前要确认的信息，不该和发送键挤在一起误触
                // 图标合成一个可点区域，直通模型切换页。此前要换模型
                // 得走设置 → API 配置 → 进某组 → 找到模型区，而换模型是
                // 聊天时最频繁的操作，值得一个就近入口。
                // 生成中禁用：这次请求已经带着旧模型发出去了
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(AppShapeTokens.CircleButton)
                        .clickable(enabled = !isLoading, onClick = onPickModel)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    ModelIcon(modelName = modelName, size = 18.dp)
                }

                Spacer(Modifier.width(8.dp))

                ThinkingEffortButton(
                    effort = thinkingEffort,
                    // 生成中不允许改：这次请求已经发出，改了会让人
                    // 以为对当前回复生效
                    enabled = !isLoading,
                    onClick = onCycleThinkingEffort
                )

                Spacer(Modifier.width(8.dp))

                // 生成中显示具体阶段，替代提供商名——后者在等待期间
                // 不提供任何新信息，而用户此时最想知道的是进行到哪一步
                // 只在生成中显示阶段文字。空闲时这里曾显示配置组名，
                // 但那个信息在顶栏副标题里已经有了，重复占位还容易
                // 被误当成可切换的按钮
                if (statusLabel.isNotEmpty()) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
                // 单个带 weight 的 Spacer 吃掉剩余宽度。此前这里是
                // 文字带 weight(1f, fill = false) 再跟一个 Box(weight(1f))，
                // 两个 weight 争抢剩余空间，Row 的高度与基线对齐都被打乱，
                // 发送键因此偏离左侧那一行
                Spacer(modifier = Modifier.weight(1f))
                SendButton(
                    canSend = canSend,
                    isLoading = isLoading,
                    onSend = onSend,
                    onStop = onStop
                )
            }
        }
    }
}

/**
 * 思考程度按钮。
 *
 * 点击循环切换档位而非弹菜单：只有四档，循环比"点开、选、关掉"更快，
 * 而输入栏这个位置本来就该是轻量操作。
 *
 * 关闭时用低对比度的边框样式，开启后填充主色——不看文字也能判断
 * 现在到底开没开。
 */
@Composable
private fun ThinkingEffortButton(
    effort: ThinkingEffort,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val active = effort.enabled

    // 底色随档位加深，而不是"开就一个色"。三档用同一颜色时，看一眼
    // 只知道开着、不知道开到哪一档，得点开面板才能确认。
    //
    // 插值上限只到 0.36 而非接近 1：secondary 本身相当深，靠近它时
    // 底色暗得压过周围的浅色控件，看着像被禁用。三档之间拉开约两成
    // 的差距已经分得出来，同时都还留在"浅色徽章"的范围内。
    //
    // 用 secondaryContainer 与 secondary 之间的插值而非三个写死的颜色：
    // 主题配色可切换（六套色板加动态取色），写死的值在别的色板下会
    // 与整体脱节
    val depth = when (effort) {
        ThinkingEffort.LOW -> 0f
        ThinkingEffort.MEDIUM -> 0.18f
        ThinkingEffort.HIGH -> 0.36f
        else -> 0f
    }
    val activeColor = lerp(
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.secondary,
        depth
    )
    val activeContentColor = lerp(
        MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.onSecondary,
        depth
    )

    Surface(
        color = if (active) activeColor else Color.Transparent,
        contentColor = if (active) {
            activeContentColor
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = AppShapeTokens.Pill,
        border = if (active) {
            null
        } else {
            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.alpha(if (enabled) 1f else 0.5f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = IconThinking,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            // 档名始终显示，包括关闭时。此前关闭态只有一个图标，
            // 既看不出当前是什么状态，点击区域也小得容易点偏
            Spacer(Modifier.width(5.dp))
            Text(
                text = effort.label,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/**
 * 发送按钮，生成中切换为暂停。
 *
 * 复用同一位置而非另设一个按钮：生成中不可能同时需要发送，
 * 两个功能互斥；就地切换也让用户不必寻找暂停在哪。
 *
 * 不可用时降低对比度而非隐藏，保持布局稳定并暗示"补全输入即可发送"。
 */
@Composable
private fun SendButton(
    canSend: Boolean,
    isLoading: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    // 暂停用 error 色：它是一个中断性操作，与发送的正向语义相反，
    // 用主色会让两种状态难以区分
    val container = when {
        isLoading -> MaterialTheme.colorScheme.error
        canSend -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when {
        isLoading -> MaterialTheme.colorScheme.onError
        canSend -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = container,
        shape = AppShapeTokens.CircleButton,
        modifier = Modifier.size(44.dp)
    ) {
        // 用 IconButton 承接点击，以获得涟漪反馈与无障碍语义。
        // 若改用 Modifier.clickable 需自行处理这两点
        IconButton(
            onClick = if (isLoading) onStop else onSend,
            enabled = isLoading || canSend
        ) {
            if (isLoading) {
                // 方块而非图标：material-icons-core 里没有 stop 图标，
                // 而实心方块是停止的通用表意，也不需要额外依赖
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(content, AppShapeTokens.StopSquare)
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = content,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
