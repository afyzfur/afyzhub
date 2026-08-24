package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens
import java.util.Calendar

/**
 * 空会话首屏。
 *
 * 设计取舍见 docs/ui-redesign.md 4.3：
 * - 不完全空白（原实现只有一行灰字"发送一条消息开始对话"，显得未完成）
 * - 也不做能力介绍卡片或 AI 生成的建议。生成建议需要额外请求，
 *   耗时耗 token，且新会话缺乏上下文，产出质量低
 * - 问候语置于垂直方向约 40% 处而非正中。正中会让下方留白显得空洞
 */
@Composable
fun EmptyChatContent(
    prompts: List<String>,
    onPromptClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 时段只在进入首屏时判定一次，避免每次重组都读系统时间
    val greetingText = remember { greeting() }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 上下 0.4 : 0.6 分配，使问候语落在视觉重心而非正中
        Spacer(Modifier.weight(0.4f))

        Text(
            text = greetingText,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (prompts.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            PromptRow(prompts = prompts, onPromptClick = onPromptClick)
        }

        Spacer(Modifier.weight(0.6f))
    }
}

/**
 * 常用提示词 chip 行，横向可滑动。
 *
 * 内容来自用户配置（阶段 5 在设置页提供编辑入口），非 AI 生成。
 */
@Composable
private fun PromptRow(
    prompts: List<String>,
    onPromptClick: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        prompts.forEach { prompt ->
            Surface(
                shape = AppShapeTokens.Pill,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.clickable { onPromptClick(prompt) }
            ) {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/** 按当前时段返回问候语 */
private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        in 18..22 -> "晚上好"
        else -> "夜深了"
    }
}

// 提示词默认值已移至 data/settings/UiPreferences.kt 的 DefaultQuickPrompts，
// 由设置页管理，此处不再硬编码。
