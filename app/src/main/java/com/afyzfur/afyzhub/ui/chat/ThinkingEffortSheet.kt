package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            ) {
                Box(
                    modifier = Modifier.size(44.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(IconThinking, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.thinking_mode), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.thinking_mode_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = thinkingEnabled, onCheckedChange = onThinkingEnabledChange)
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp)
                    ) {
                        Text(stringResource(R.string.thinking_effort), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { showEffortInfo = true }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = stringResource(R.string.thinking_effort_info),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (thinkingEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                if (thinkingEnabled) current.id.uppercase() else "OFF",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (thinkingEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.thinking_effort_short_hint),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AnimatedThinkingEffortSelector(current, thinkingEnabled, onEffortChange)
                }
            }
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.thinking_guidance), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.thinking_guidance_hint),
                            modifier = Modifier.padding(top = 4.dp, end = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = thinkingGuidanceEnabled, onCheckedChange = onGuidanceEnabledChange)
                }
            }
            Spacer(Modifier.height(20.dp))
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
