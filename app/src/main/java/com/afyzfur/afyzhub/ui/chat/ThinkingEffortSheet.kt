package com.afyzfur.afyzhub.ui.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.afyzhub.chat.R
import com.afyzfur.afyzhub.domain.model.ThinkingEffort
import com.afyzfur.afyzhub.ui.components.IconThinking

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
    var sliderPosition by remember { mutableFloatStateOf(current.ordinal.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }
    var showEffortInfo by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()

    fun settleOn(effort: ThinkingEffort) {
        settleJob?.cancel()
        isDragging = false
        isSettling = true
        val target = effort.ordinal.toFloat()
        val start = sliderPosition
        onEffortChange(effort)
        settleJob = scope.launch {
            animate(
                initialValue = start,
                targetValue = target,
                animationSpec = tween(
                    durationMillis = 220,
                    easing = FastOutSlowInEasing
                )
            ) { value, _ -> sliderPosition = value }
            sliderPosition = target
            isSettling = false
        }
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    settleJob?.cancel()
                    isSettling = false
                    isDragging = true
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    if (isDragging) {
                        settleOn(ThinkingEffort.entries[sliderPosition.roundToInt().coerceIn(0, 4)])
                    }
                }
            }
        }
    }
    val selectedEffort = ThinkingEffort.entries[sliderPosition.roundToInt().coerceIn(0, 4)]

    LaunchedEffect(current) {
        if (!isDragging && !isSettling) {
            sliderPosition = current.ordinal.toFloat()
        }
    }

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
                Text(
                    stringResource(R.string.thinking_mode),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Switch(checked = thinkingEnabled, onCheckedChange = onThinkingEnabledChange)
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.thinking_effort), style = MaterialTheme.typography.titleMedium)
                IconButton(
                    onClick = { showEffortInfo = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = stringResource(R.string.thinking_effort_info),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    selectedEffort.id,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = sliderPosition,
                interactionSource = interactionSource,
                onValueChange = { value ->
                    settleJob?.cancel()
                    isSettling = false
                    isDragging = true
                    sliderPosition = value.coerceIn(0f, 4f)
                },
                onValueChangeFinished = {
                    if (isDragging) {
                        settleOn(ThinkingEffort.entries[sliderPosition.roundToInt().coerceIn(0, 4)])
                    }
                },
                valueRange = 0f..4f,
                steps = 0,
                enabled = thinkingEnabled,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                    activeTickColor = MaterialTheme.colorScheme.onPrimary,
                    inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )
            Layout(
                content = {
                    ThinkingEffort.entries.forEach { effort ->
                        Text(
                            text = effort.id,
                            modifier = Modifier.clickable(enabled = thinkingEnabled) {
                                settleOn(effort)
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (effort == selectedEffort && thinkingEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            fontWeight = if (effort == selectedEffort) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(32.dp)
            ) { measurables, constraints ->
                val placeables = measurables.map { it.measure(Constraints()) }
                val thumbInset = 10.dp.roundToPx()
                val trackWidth = constraints.maxWidth - thumbInset * 2
                layout(constraints.maxWidth, 32.dp.roundToPx()) {
                    placeables.forEachIndexed { index, placeable ->
                        val center = thumbInset + trackWidth * index / 4
                        placeable.placeRelative(center - placeable.width / 2, 0)
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.thinking_guidance), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.thinking_guidance_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
            confirmButton = {
                TextButton(onClick = { showEffortInfo = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
}
