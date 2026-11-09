package com.afyzfur.afyzhub.ui.chat

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.domain.model.ThinkingEffort

/** One coordinate system: five cell centres own labels, ticks, pointer mapping and thumb. */
@Composable
internal fun AnimatedThinkingEffortSelector(
    current: ThinkingEffort,
    enabled: Boolean,
    onEffortChange: (ThinkingEffort) -> Unit
) {
    val state = remember { ThinkingEffortControlState(current) }
    val onChange by rememberUpdatedState(onEffortChange)
    val displayed by animateFloatAsState(
        targetValue = state.target,
        animationSpec = tween(420, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)),
        label = "thinking-effort-position"
    )
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val primary = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.outlineVariant
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val alpha = if (enabled) 1f else 0.38f
    LaunchedEffect(current) { state.acceptExternal(current) }
    LaunchedEffect(enabled) { if (!enabled) state.cancel() }

    fun choose(value: Float) {
        state.choose(value)?.let(onChange)
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellWidth = maxWidth / 5
        val diameter = minOf(56.dp, (cellWidth - 6.dp).coerceAtLeast(0.dp))
        Column {
            Canvas(
                Modifier.fillMaxWidth().height(52.dp).testTag("effort-track")
                    .semantics {
                        contentDescription = "Thinking effort"
                        progressBarRangeInfo = ProgressBarRangeInfo(displayed, 0f..4f, 3)
                        if (!enabled) disabled()
                        setProgress { value ->
                            if (enabled) choose(value)
                            enabled
                        }
                    }
                    .pointerInput(enabled, rtl) {
                        if (enabled) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val start = size.width / 10f
                                val length = size.width * 0.8f
                                fun valueAt(x: Float): Float {
                                    val logicalX = if (rtl) size.width - x else x
                                    return ((logicalX - start) / length * 4f).coerceIn(0f, 4f)
                                }
                                down.consume()
                                var latest = valueAt(down.position.x)
                                state.preview(latest)
                                var released = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || change.isConsumed) break
                                    latest = valueAt(change.position.x)
                                    change.consume()
                                    if (!change.pressed) {
                                        released = true
                                        break
                                    }
                                    state.preview(latest)
                                }
                                if (released) choose(latest) else state.cancel()
                            }
                        }
                    }
            ) {
                val start = size.width / 10f
                val length = size.width * 0.8f
                val y = size.height / 2
                fun x(value: Float): Float {
                    val logical = start + length * value / 4f
                    return if (rtl) size.width - logical else logical
                }
                drawLine(inactive.copy(alpha = alpha), Offset(x(0f), y), Offset(x(4f), y), 6.dp.toPx(), StrokeCap.Round)
                drawLine(primary.copy(alpha = alpha), Offset(x(0f), y), Offset(x(displayed), y), 6.dp.toPx(), StrokeCap.Round)
                repeat(5) { index ->
                    drawCircle(primary.copy(alpha = alpha), 2.dp.toPx(), Offset(x(index.toFloat()), y))
                }
                drawCircle(primary.copy(alpha = alpha), 10.dp.toPx(), Offset(x(displayed), y))
                drawCircle(onPrimary.copy(alpha = alpha), 3.dp.toPx(), Offset(x(displayed), y))
            }
            Row(Modifier.fillMaxWidth()) {
                ThinkingEffort.entries.forEach { effort ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val interaction = remember { MutableInteractionSource() }
                        Box(
                            Modifier.size(diameter).testTag("effort-${effort.id}")
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = interaction,
                                    indication = ripple(bounded = true),
                                    enabled = enabled,
                                    role = Role.RadioButton
                                ) { choose(effort.ordinal.toFloat()) }
                                .semantics { selected = state.selected == effort },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                effort.id.uppercase(),
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.selected == effort && enabled) primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
