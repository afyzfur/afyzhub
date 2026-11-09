package com.afyzfur.afyzhub.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens
import kotlinx.coroutines.flow.collectLatest

/** One coordinate system: five cell centres own labels, ticks, pointer mapping and thumb. */
@Composable
internal fun AnimatedThinkingEffortSelector(
    current: ThinkingEffort,
    enabled: Boolean,
    onEffortChange: (ThinkingEffort) -> Unit
) {
    val state = remember { ThinkingEffortControlState(current) }
    val onChange by rememberUpdatedState(onEffortChange)
    val displayed = remember { Animatable(current.ordinal.toFloat()) }
    // Observe input without subscribing the selector composition to every pointer update.
    LaunchedEffect(state) {
        var retainedVelocity = 0f
        snapshotFlow { state.target to state.followingDrag }.collectLatest { (target, dragging) ->
            displayed.animateTo(
                target,
                initialVelocity = if (dragging) retainedVelocity else 0f,
                animationSpec = if (dragging) {
                    spring(dampingRatio = 1f, stiffness = 5000f, visibilityThreshold = 0.005f)
                } else {
                    tween(180, easing = FastOutSlowInEasing)
                }
            ) { retainedVelocity = velocity }
            retainedVelocity = 0f
        }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val primary = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.outlineVariant
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val alpha = if (enabled) 1f else 0.38f
    LaunchedEffect(current) { state.acceptExternal(current) }
    LaunchedEffect(enabled) { if (!enabled) state.cancel() }

    fun choose(value: Float, fromDrag: Boolean = false) {
        state.choose(value, fromDrag)?.let(onChange)
    }

    Column(Modifier.fillMaxWidth()) {
            Canvas(
                Modifier.fillMaxWidth().height(52.dp).testTag("effort-track")
                    .semantics {
                        contentDescription = "Thinking effort"
                        progressBarRangeInfo = ProgressBarRangeInfo(displayed.value, 0f..4f, 3)
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
                                var dragged = false
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
                                    if (!dragged && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                        dragged = true
                                    }
                                    if (dragged) state.preview(latest)
                                }
                                if (released) choose(latest, fromDrag = dragged) else state.cancel()
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
                drawLine(primary.copy(alpha = alpha), Offset(x(0f), y), Offset(x(displayed.value), y), 6.dp.toPx(), StrokeCap.Round)
                repeat(5) { index ->
                    drawCircle(primary.copy(alpha = alpha), 2.dp.toPx(), Offset(x(index.toFloat()), y))
                }
                drawCircle(primary.copy(alpha = alpha), 10.dp.toPx(), Offset(x(displayed.value), y))
                drawCircle(onPrimary.copy(alpha = alpha), 4.dp.toPx(), Offset(x(displayed.value), y))
            }
            Row(Modifier.fillMaxWidth()) {
                ThinkingEffort.entries.forEach { effort ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val interaction = remember { MutableInteractionSource() }
                        Box(
                            Modifier.fillMaxWidth().padding(horizontal = 3.dp).height(40.dp).testTag("effort-${effort.id}")
                                .clip(AppShapeTokens.Pill)
                                .background(if (state.selected == effort && enabled) primary.copy(alpha = 0.08f) else Color.Transparent)
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
                            if (state.selected == effort && enabled) {
                                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp)
                                    .size(width = 12.dp, height = 2.dp)
                                    .background(primary, AppShapeTokens.Pill))
                            }
                        }
                    }
                }
            }
    }
}
