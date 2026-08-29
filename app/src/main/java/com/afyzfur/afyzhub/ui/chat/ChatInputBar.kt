package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.BorderStroke
import com.afyzfur.afyzhub.ui.components.ThinkingLightbulb
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
import androidx.compose.foundation.background
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
    modifier: Modifier = Modifier
) {
    val canSend = value.isNotBlank() && !isLoading

    Surface(
        // 透明时仍留一层极淡的底色而非全透：完全透明会让光标与
        // 占位文字直接压在背景图上，几乎无法辨认边界
        color = if (transparent) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = AppShapeTokens.InputContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        // 导航栏内边距加在容器内部，使容器背景延伸到屏幕底边，
        // 而内容不被系统手势区域压住
        Column(
            modifier = Modifier
                .padding(top = 4.dp)
                .navigationBarsPadding()
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
                Text(
                    text = statusLabel.ifEmpty { providerLabel },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (statusLabel.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // 空闲时这里显示配置组名，点它同样进模型页；生成中
                    // 显示的是阶段文字，点了没有意义所以不可点
                    modifier = Modifier
                        .clip(AppShapeTokens.CircleButton)
                        .clickable(enabled = !isLoading, onClick = onPickModel)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
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
    Surface(
        color = if (active) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        contentColor = if (active) {
            MaterialTheme.colorScheme.onSecondaryContainer
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = ThinkingLightbulb,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            // 关闭时只显示图标，省下的横向空间留给状态文字
            if (active) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = effort.label,
                    style = MaterialTheme.typography.labelSmall
                )
            }
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
