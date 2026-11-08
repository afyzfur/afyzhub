package com.afyzfur.afyzhub.ui.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.afyzhub.chat.R
import com.afyzfur.afyzhub.domain.model.ThinkingEffort
import com.afyzfur.afyzhub.ui.components.IconThinking
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThinkingEffortSheet(
    thinkingEnabled: Boolean,
    current: ThinkingEffort,
    thinkingGuidanceEnabled: Boolean,
    onThinkingEnabledChange: (Boolean) -> Unit,
    onEffortChange: (ThinkingEffort) -> Unit,
    onGuidanceEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var showEffortInfo by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(IconThinking, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.thinking_mode), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Switch(checked = thinkingEnabled, onCheckedChange = onThinkingEnabledChange)
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.thinking_effort), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { showEffortInfo = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Info, contentDescription = stringResource(R.string.thinking_effort_info), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Text(current.id, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            ThinkingEffortSelector(current, thinkingEnabled, onEffortChange)
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.thinking_guidance), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.thinking_guidance_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = thinkingGuidanceEnabled, onCheckedChange = onGuidanceEnabledChange)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showEffortInfo) {
        AlertDialog(
            onDismissRequest = { showEffortInfo = false },
            title = { Text(stringResource(R.string.thinking_effort)) },
            text = { Text(stringResource(R.string.thinking_effort_info)) },
            confirmButton = { TextButton(onClick = { showEffortInfo = false }) { Text(stringResource(android.R.string.ok)) } }
        )
    }
}

@Composable
private fun ThinkingEffortSelector(current: ThinkingEffort, enabled: Boolean, onEffortChange: (ThinkingEffort) -> Unit) {
    var position by remember { mutableFloatStateOf(current.ordinal.toFloat()) }
    var pointerActive by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }
    var lastCommitted by remember { mutableStateOf(current) }
    var finishHandled by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    var animationJob by remember { mutableStateOf<Job?>(null) }
    var animationGeneration by remember { mutableIntStateOf(0) }
    val labelInteractions = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val onChange by rememberUpdatedState(onEffortChange)
    val selected = ThinkingEffort.entries[position.roundToInt().coerceIn(0, 4)]

    fun commit(effort: ThinkingEffort) {
        if (lastCommitted != effort) {
            lastCommitted = effort
            onChange(effort)
        }
    }

    fun tap(effort: ThinkingEffort) {
        animationJob?.cancel()
        animationGeneration++
        isDragging = false
        isSettling = false
        finishHandled = true
        position = effort.ordinal.toFloat()
        commit(effort)
    }

    fun settle(effort: ThinkingEffort) {
        if (!isDragging || finishHandled) return
        finishHandled = true
        animationJob?.cancel()
        isDragging = false
        isSettling = true
        val generation = ++animationGeneration
        commit(effort)
        val start = position
        animationJob = scope.launch {
            animate(start, effort.ordinal.toFloat(), animationSpec = tween(160, easing = FastOutSlowInEasing)) { value, _ -> position = value }
            if (animationGeneration == generation) {
                position = effort.ordinal.toFloat()
                isSettling = false
            }
        }
    }

    LaunchedEffect(current) {
        lastCommitted = current
        if (!isDragging && !isSettling) position = current.ordinal.toFloat()
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    animationJob?.cancel()
                    animationGeneration++
                    pointerActive = true
                    isDragging = true
                    finishHandled = false
                    isSettling = false
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    pointerActive = false
                    if (isDragging && !finishHandled) settle(ThinkingEffort.entries[position.roundToInt().coerceIn(0, 4)])
                }
            }
        }
    }

    Layout(
        content = {
            Slider(
                value = position,
                interactionSource = interactionSource,
                onValueChange = { value ->
                    if (!pointerActive && !finishHandled) {
                        animationJob?.cancel()
                        animationGeneration++
                        position = value.coerceIn(0f, 4f)
                    } else {
                        position = value.coerceIn(0f, 4f)
                    }
                },
                onValueChangeFinished = {
                    if (!pointerActive && !finishHandled) {
                        tap(ThinkingEffort.entries[position.roundToInt().coerceIn(0, 4)])
                    } else if (isDragging && !finishHandled) {
                        settle(ThinkingEffort.entries[position.roundToInt().coerceIn(0, 4)])
                    }
                },
                valueRange = 0f..4f,
                steps = 0,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                    activeTickColor = MaterialTheme.colorScheme.onPrimary,
                    inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .layoutId("slider")
            )
            ThinkingEffort.entries.forEach { effort ->
                Text(
                    text = effort.id.uppercase(),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = labelInteractions,
                            indication = ripple(bounded = true, radius = 28.dp),
                            enabled = enabled
                        ) { tap(effort) }
                        .layoutId("label_${effort.ordinal}"),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (effort == selected && enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (effort == selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        },
        modifier = Modifier.fillMaxWidth().height(84.dp)
    ) { measurables, constraints ->
        val density = this.density
        val sliderPadding = with(density) { 12.dp.toPx() }.roundToInt()
        val thumbInset = with(density) { 10.dp.toPx() }.roundToInt()
        val sliderWidth = constraints.maxWidth
        val slider = measurables.first { it.layoutId == "slider" }.measure(Constraints(minWidth = sliderWidth, maxWidth = sliderWidth))
        val labels = measurables.filter { it.layoutId.toString().startsWith("label_") }.map { measurable ->
            val labelWidth = measurable.maxIntrinsicWidth(constraints.maxHeight)
            measurable.measure(Constraints(minWidth = labelWidth, maxWidth = labelWidth))
        }
        val trackWidth = sliderWidth - sliderPadding * 2 - thumbInset * 2
        val labelsTop = slider.height - with(density) { 4.dp.toPx() }.roundToInt()
        layout(constraints.maxWidth, slider.height + with(density) { 36.dp.toPx() }.roundToInt()) {
            slider.placeRelative(sliderPadding, 0)
            labels.forEachIndexed { index, label ->
                val center = sliderPadding + thumbInset + trackWidth * index / 4
                label.placeRelative(center - label.width / 2, labelsTop)
            }
        }
    }
}