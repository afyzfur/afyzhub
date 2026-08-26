package com.afyzfur.afyzhub.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens

/**
 * 思考过程块，独立于正文单独成栏。
 *
 * 展开策略：思考进行中自动展开，让用户看到模型在推进而非空等；
 * 思考结束后自动收起，把空间让给正式回答。用户可手动切换，
 * 手动操作后不再被自动策略覆盖——否则用户展开旧回复的思考时
 * 会被下一次重组收走。
 *
 * 折叠而非默认隐藏：思考内容对判断回答质量有价值，
 * 完全藏起来等于丢掉信息。
 */
@Composable
fun ReasoningBlock(
    reasoning: String,
    thinking: Boolean,
    modifier: Modifier = Modifier
) {
    // null 表示未手动干预，跟随 thinking 自动展开或收起
    var manualExpanded by remember { mutableStateOf<Boolean?>(null) }
    val expanded = manualExpanded ?: thinking

    // 思考结束时清掉手动状态，使下一条消息回到自动策略。
    // 不清的话本条的手动选择会影响后续消息的初始状态
    LaunchedEffect(thinking) {
        if (!thinking) manualExpanded = null
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = AppShapeTokens.SettingsGroup,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    // clip 必须在 clickable 之前：否则涟漪按矩形绘制，
                    // 会溢出容器的上方圆角
                    .clip(AppShapeTokens.SettingsGroup)
                    .clickable { manualExpanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (thinking) "正在思考" else "思考过程",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    // 字数比"点击展开"更有信息量：能预估思考的详细程度
                    text = "${reasoning.length} 字",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Text(
                    text = reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    // 思考过程用弱化的颜色，与正式回答区分主次
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = 14.dp,
                        end = 14.dp,
                        bottom = 12.dp
                    )
                )
            }
        }
    }
}
