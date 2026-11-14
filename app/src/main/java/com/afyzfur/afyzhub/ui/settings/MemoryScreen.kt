package com.afyzfur.afyzhub.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afyzfur.afyzhub.data.memory.MAX_ITEM_CHARS
import com.afyzfur.afyzhub.data.memory.MemoryRepository
import com.afyzfur.afyzhub.data.memory.MemoryRunState
import com.afyzfur.afyzhub.domain.model.MemoryFrequency
import com.afyzfur.afyzhub.domain.model.MemoryItem
import com.afyzfur.afyzhub.domain.model.MemorySnapshot
import com.afyzfur.afyzhub.domain.model.MemoryTrigger
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens
import com.afyzfur.afyzhub.util.AppText
import com.afyzhub.chat.R
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class MemoryViewModel(private val repository: MemoryRepository) : ViewModel() {
    val state: StateFlow<MemorySnapshot> = repository.state
    val runState: StateFlow<MemoryRunState> = repository.runState

    fun onScreenOpened() = repository.onTrigger(MemoryTrigger.MEMORY_SCREEN)
    fun summarizeNow() = repository.summarizeInBackground()
    fun setEnabled(enabled: Boolean) { viewModelScope.launch { repository.setEnabled(enabled) } }
    fun setFrequency(frequency: MemoryFrequency) { viewModelScope.launch { repository.setFrequency(frequency) } }
    fun add(text: String) { viewModelScope.launch { repository.addManual(text) } }
    fun update(id: String, text: String) { viewModelScope.launch { repository.update(id, text) } }
    fun delete(id: String) { viewModelScope.launch { repository.delete(id) } }
    fun clear() { viewModelScope.launch { repository.clear() } }
}

@Composable
fun MemoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: MemoryViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val run by viewModel.runState.collectAsState()
    LaunchedEffect(Unit) { viewModel.onScreenOpened() }
    MemoryContent(
        state = state,
        run = run,
        onNavigateBack = onNavigateBack,
        onEnabledChange = viewModel::setEnabled,
        onFrequencyChange = viewModel::setFrequency,
        onSummarize = viewModel::summarizeNow,
        onAdd = viewModel::add,
        onUpdate = viewModel::update,
        onDelete = viewModel::delete,
        onClear = viewModel::clear
    )
}

private data class MemoryEditTarget(val id: String?, val text: String)

@Composable
internal fun MemoryContent(
    state: MemorySnapshot,
    run: MemoryRunState,
    onNavigateBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onFrequencyChange: (MemoryFrequency) -> Unit,
    onSummarize: () -> Unit,
    onAdd: (String) -> Unit,
    onUpdate: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit
) {
    var editing by remember { mutableStateOf<MemoryEditTarget?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            SettingsPageHeader(title = stringResource(R.string.memory_title), onNavigateBack = onNavigateBack)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                MemoryHero(state, run, onSummarize)
                SettingsCategoryTitle(stringResource(R.string.memory_section_settings))
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Default.Psychology,
                        title = stringResource(R.string.memory_enabled),
                        subtitle = stringResource(R.string.memory_enabled_hint),
                        checked = state.enabled,
                        onCheckedChange = onEnabledChange
                    )
                    SettingsItemDivider()
                    SettingsDropdownItem(
                        icon = Icons.Default.DateRange,
                        title = stringResource(R.string.memory_frequency),
                        subtitle = "",
                        options = MemoryFrequency.entries,
                        selected = state.frequency,
                        label = { AppText.get(it.labelRes) },
                        onSelect = onFrequencyChange
                    )
                }
                SettingsCategoryTitle(stringResource(R.string.memory_section_items, state.items.size))
                SettingsGroup {
                    SettingsActionItem(
                        icon = Icons.Default.Add,
                        title = stringResource(R.string.memory_add),
                        onClick = { editing = MemoryEditTarget(null, "") }
                    )
                    if (state.loaded && state.items.isEmpty()) {
                        SettingsItemDivider()
                        Text(
                            text = stringResource(R.string.memory_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                        )
                    }
                    state.items.forEach { item ->
                        SettingsItemDivider()
                        MemoryRow(
                            item = item,
                            onEdit = { editing = MemoryEditTarget(item.id, item.content) },
                            onDelete = { onDelete(item.id) }
                        )
                    }
                }
                if (state.items.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    SettingsGroup {
                        SettingsActionItem(
                            icon = Icons.Default.Delete,
                            title = stringResource(R.string.memory_clear),
                            destructive = true,
                            onClick = { confirmClear = true }
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    editing?.let { target ->
        MemoryEditor(
            target = target,
            onDismiss = { editing = null },
            onSave = { text ->
                if (target.id == null) onAdd(text) else onUpdate(target.id, text)
                editing = null
            }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.memory_clear_title)) },
            text = { Text(stringResource(R.string.memory_clear_confirm, state.items.size)) },
            confirmButton = {
                TextButton(onClick = { onClear(); confirmClear = false }) {
                    Text(stringResource(R.string.memory_clear_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.memory_cancel)) }
            }
        )
    }
}

@Composable
private fun MemoryHero(state: MemorySnapshot, run: MemoryRunState, onSummarize: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = AppShapeTokens.SettingsGroup,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = AppShapeTokens.CircleButton,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.memory_hero_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.memory_hero_body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (run.running) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = memoryStatus(state, run),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f).testTag("memory-status")
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                    onClick = onSummarize,
                    enabled = state.enabled && !run.running,
                    modifier = Modifier.fillMaxWidth().testTag("memory-summarize")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (run.running) R.string.memory_summarizing else R.string.memory_summarize_now))
                }
            run.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(it.messageRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("memory-error")
                )
            }
        }
    }
}

@Composable
private fun memoryStatus(state: MemorySnapshot, run: MemoryRunState): String = when {
    run.running -> stringResource(R.string.memory_status_running)
    !state.enabled -> stringResource(R.string.memory_status_off)
    state.lastRunAt == 0L -> stringResource(R.string.memory_status_never)
    else -> stringResource(
        R.string.memory_status_last,
        DateUtils.getRelativeTimeSpanString(state.lastRunAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    )
}

@Composable
private fun MemoryRow(item: MemoryItem, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("memory-item-${item.id}")
            .settingsRowClickable(onClick = onEdit)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(if (item.manual) R.string.memory_source_manual else R.string.memory_source_auto),
                style = MaterialTheme.typography.labelSmall,
                color = if (item.manual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.testTag("memory-delete-${item.id}")) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.memory_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MemoryEditor(target: MemoryEditTarget, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(target) { mutableStateOf(target.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (target.id == null) R.string.memory_add else R.string.memory_edit)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(MAX_ITEM_CHARS) },
                    placeholder = { Text(stringResource(R.string.memory_add_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("memory-editor")
                )
                if (target.id != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.memory_edit_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("memory-save")
            ) { Text(stringResource(R.string.memory_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.memory_cancel)) }
        }
    )
}
