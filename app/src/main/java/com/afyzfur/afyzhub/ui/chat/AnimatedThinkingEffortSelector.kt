package com.afyzfur.afyzhub.ui.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

/** One coordinate system: five cell centres own labels, ticks, pointer mapping and thumb. */
@Composable
internal fun AnimatedThinkingEffortSelector(
    current: ThinkingEffort,
    enabled: Boolean,
    onEffortChange: (ThinkingEffort) -> Unit
) {
    val state = remember { ThinkingEffortControlState(current) }
    val onChange by rememberUpdatedState(onEffortChange)
    val displayed = remember { mutableFloatStateOf(current.ordinal.toFloat()) }
    // One frame loop owns motion. Retargeting a drag never cancels/restarts a coroutine.
    LaunchedEffect(state) {
        var velocity = 0f
        while (currentCoroutineContext().isActive) {
            snapshotFlow { state.target }.first { abs(it - displayed.floatValue) > 0.001f }
            var previous = 0L
            var clickStart = displayed.floatValue
            var clickTarget = state.target
            var elapsed = 0f
            var wasDragging = state.followingDrag
            while (currentCoroutineContext().isActive) {
                val now = withFrameNanos { it }
                val dt = if (previous == 0L) 1f / 60f else ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.05f)
                previous = now
                val target = state.target
                val dragging = state.followingDrag
                if (dragging) {
                    // Analytic critically damped follow; stable across refresh rates and pointer events.
                    val omega = 100f
                    val offset = displayed.floatValue - target
                    val coefficient = velocity + omega * offset
                    val decay = exp(-omega * dt)
                    displayed.floatValue = (target + (offset + coefficient * dt) * decay).coerceIn(0f, 4f)
                    velocity = (velocity - omega * coefficient * dt) * decay
                } else {
                    if (wasDragging || target != clickTarget) {
                        clickStart = displayed.floatValue
                        clickTarget = target
                        elapsed = 0f
                    }
                    elapsed += dt
                    val fraction = (elapsed / 0.20f).coerceIn(0f, 1f)
                    displayed.floatValue = clickStart + (target - clickStart) * FastOutSlowInEasing.transform(fraction)
                    velocity = 0f
                }
                wasDragging = dragging
                if ((dragging && abs(displayed.floatValue - target) < 0.002f && abs(velocity) < 0.03f) ||
                    (!dragging && elapsed >= 0.20f)) {
                    displayed.floatValue = target
                    velocity = 0f
                    break
                }
            }
        }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val primary = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.surfaceContainerHighest
    val outline = MaterialTheme.colorScheme.outlineVariant
    val thumb = MaterialTheme.colorScheme.surface
    val dot = MaterialTheme.colorScheme.onSurfaceVariant
    val onFill = MaterialTheme.colorScheme.onPrimary
    val alpha = if (enabled) 1f else 0.38f
    LaunchedEffect(current) { state.acceptExternal(current) }
    LaunchedEffect(enabled) { if (!enabled) state.cancel() }

    fun choose(value: Float, fromDrag: Boolean = false) {
        state.choose(value, fromDrag)?.let(onChange)
    }

    Column(Modifier.fillMaxWidth()) {
            Canvas(
                Modifier.fillMaxWidth().height(60.dp).testTag("effort-track")
                    .semantics {
                        contentDescription = "Thinking effort"
                        progressBarRangeInfo = ProgressBarRangeInfo(displayed.floatValue, 0f..4f, 3)
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
                val outerHeight = 44.dp.toPx()
                val top = (size.height - outerHeight) / 2f
                val inset = 5.dp.toPx()
                val innerHeight = outerHeight - 2f * inset
                val radius = minOf(innerHeight / 2f, start - inset)
                val centre = x(displayed.floatValue)
                drawRoundRect(inactive.copy(alpha = alpha), Offset(0f, top), Size(size.width, outerHeight), CornerRadius(outerHeight / 2f))
                drawRoundRect(outline.copy(alpha = 0.5f * alpha), Offset(0.5.dp.toPx(), top + 0.5.dp.toPx()),
                    Size(size.width - 1.dp.toPx(), outerHeight - 1.dp.toPx()), CornerRadius(outerHeight / 2f), style = Stroke(1.dp.toPx()))
                val fillLeft = if (rtl) centre - radius else inset
                val fillRight = if (rtl) size.width - inset else centre + radius
                drawRoundRect(primary.copy(alpha = alpha), Offset(fillLeft, top + inset),
                    Size((fillRight - fillLeft).coerceAtLeast(innerHeight), innerHeight), CornerRadius(radius))
                repeat(5) { index ->
                    val covered = index <= displayed.floatValue
                    val tickColor = if (covered) onFill else dot
                    drawCircle(tickColor.copy(alpha = (if (covered) 0.5f else 0.38f) * alpha), 3.dp.toPx(), Offset(x(index.toFloat()), y))
                }
                drawCircle(Color.Black.copy(alpha = 0.08f * alpha), 13.dp.toPx(), Offset(centre, y + 1.dp.toPx()))
                drawCircle(thumb.copy(alpha = alpha), 12.5.dp.toPx(), Offset(centre, y))
                drawCircle(outline.copy(alpha = 0.4f * alpha), 12.5.dp.toPx(), Offset(centre, y), style = Stroke(0.5.dp.toPx()))
            }
            Row(Modifier.fillMaxWidth()) {
                ThinkingEffort.entries.forEach { effort ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val interaction = remember { MutableInteractionSource() }
                        Box(
                            Modifier.fillMaxWidth().padding(horizontal = 3.dp).height(40.dp).testTag("effort-${effort.id}")
                                .clip(AppShapeTokens.Pill)
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
