package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.domain.model.ThinkingEffort
import com.afyzfur.afyzhub.ui.components.IconThinking
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens

/**
 * 思考程度的选择表。
 *
 * 此前是点一下循环切一档。四档的循环要点三次才能从"高"回到"关"，
 * 而且切之前看不到有哪些档位——只能靠点下去才知道下一档是什么。
 * 改成横向排开一次看全，点哪档就是哪档。
 *
 * 用 LazyRow 横排而非竖排列表：四个档位是同一个维度上的强弱刻度，
 * 横向排列自然读作从左到右递增；竖排会让人以为是四个并列的选项。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThinkingEffortSheet(
    current: ThinkingEffort,
    onSelect: (ThinkingEffort) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "思考程度",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "思考会增加耗时与费用，且并非所有模型都支持",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(16.dp))

            // 两行两列铺满宽度，四档一屏全在，不需要横向滑动。
            // 此前用 LazyRow 固定卡片宽度，第四档会被推到屏幕外，
            // 而"有哪些档位"恰恰是这个面板要回答的问题
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                ThinkingEffort.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { effort ->
                            EffortCard(
                                effort = effort,
                                selected = effort == current,
                                onClick = {
                                    onSelect(effort)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单个档位的卡片。
 *
 * 卡片而非文字按钮：要同时放档名与说明，两行内容用卡片承载
 * 才有明确的点击边界。
 */
@Composable
private fun EffortCard(
    effort: ThinkingEffort,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        shape = AppShapeTokens.SettingsGroup,
        border = if (selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp)
        ) {
            // 用图标数量表示强弱：一个脑是低，三个是高，关闭时给单个
            // 灰掉的脑。比纯文字更快看出这是一条递增的刻度
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(effort.iconCount()) {
                    Icon(
                        imageVector = IconThinking,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = effort.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = effort.hint(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 该档位画几个脑图标。关闭也画一个，否则卡片高度不齐。 */
private fun ThinkingEffort.iconCount(): Int = when (this) {
    ThinkingEffort.OFF -> 1
    ThinkingEffort.LOW -> 1
    ThinkingEffort.MEDIUM -> 2
    ThinkingEffort.HIGH -> 3
}

/** 一句话说明这档意味着什么，避免只有"低/中/高"让人猜。 */
private fun ThinkingEffort.hint(): String = when (this) {
    ThinkingEffort.OFF -> "最快"
    ThinkingEffort.LOW -> "略作思考"
    ThinkingEffort.MEDIUM -> "适中"
    ThinkingEffort.HIGH -> "最慢最细"
}
