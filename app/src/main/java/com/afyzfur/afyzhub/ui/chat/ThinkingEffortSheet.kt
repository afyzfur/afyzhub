package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.testTag
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.domain.model.ThinkingEffort
import com.afyzfur.afyzhub.ui.components.IconThinking
import com.afyzhub.chat.R

internal val thinkingEffortSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

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
    val sheetShape = thinkingEffortSheetShape
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = sheetShape,
        // Clip via Surface.shape, after the sheet translation, not around the outer layout.
        modifier = Modifier.testTag("thinking-sheet"),
        tonalElevation = 0.dp,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(IconThinking, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.thinking_mode), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(checked = thinkingEnabled, onCheckedChange = onThinkingEnabledChange)
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.thinking_effort), style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f))
                Text(if (thinkingEnabled) current.id.uppercase() else "OFF",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { showEffortInfo = true }) {
                    Icon(Icons.Default.Info, contentDescription = stringResource(R.string.thinking_effort_info),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            AnimatedThinkingEffortSelector(current, thinkingEnabled, onEffortChange)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(stringResource(R.string.thinking_guidance), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.thinking_guidance_hint), modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = thinkingGuidanceEnabled, onCheckedChange = onGuidanceEnabledChange)
            }
            Spacer(Modifier.height(8.dp))
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
