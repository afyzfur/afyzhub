package com.afyzfur.afyzhub.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.domain.model.PromptGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import java.util.UUID

@Composable
fun PromptAdvancedScreen(profileId: String, onNavigateBack: () -> Unit, viewModel: ApiProfilesViewModel = koinViewModel()) {
    val store by viewModel.store.collectAsState()
    val profile = store.profiles.firstOrNull { it.id == profileId }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var pendingCard by remember { mutableStateOf<PromptGroup?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            try {
                pendingCard = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { CharacterCardImporter.read(it) } ?: error(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_b79005e90850))
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { importError = e.message ?: com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_a01a8d53938f) }
            finally { importing = false }
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            SettingsPageHeader(title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_974b6e3af51a), onNavigateBack = onNavigateBack)
            if (profile == null) {
                Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_b6a188aba8f1), modifier = Modifier.padding(24.dp))
            } else {
                val group = profile.activePrompt
                fun edit(transform: (PromptGroup) -> PromptGroup) {
                    val groupId = group.id
                    viewModel.changePrompts(profileId) { p -> p.copy(promptGroups = p.availablePrompts.map { if (it.id == groupId) transform(it) else it }) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_7f362bad0c29))
                    SettingsGroup {
                        SettingsDropdownItem(icon = Icons.Default.PlayArrow, title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_2401b106e4ec), subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_ffc95cc3f495), options = profile.availablePrompts, selected = group, label = { it.name }, onSelect = { selected -> viewModel.changePrompts(profileId) { it.copy(activePromptId = selected.id) } })
                        SettingsItemDivider()
                        SettingsTextFieldItem(identityKey = profileId + group.id, title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_65731c12ae52), value = group.name, onValueChange = { text -> edit { it.copy(name = text) } })
                        SettingsItemDivider()
                        SettingsActionItem(icon = Icons.Default.Add, title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_fa4ff6b10e32), onClick = {
                            viewModel.changePrompts(profileId) { p ->
                                val next = PromptGroup(UUID.randomUUID().toString(), "提示词 ${p.availablePrompts.size + 1}")
                                p.copy(promptGroups = p.availablePrompts + next, activePromptId = next.id)
                            }
                        })
                        SettingsItemDivider()
                        SettingsActionItem(icon = Icons.Default.Add, title = if (importing) com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_20d49aca4989) else com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_eff24c79566c), subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_d5247a5f3dca), onClick = { if (!importing) launcher.launch(arrayOf("*/*")) })
                    }
                    SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_338affe37fbd))
                    SettingsGroup { SettingsTextFieldItem(identityKey = profileId + group.id, title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_338affe37fbd), value = group.role, onValueChange = { text -> edit { it.copy(role = text) } }, placeholder = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_d490709e1007), singleLine = false) }
                    SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_05adbf557d50))
                    SettingsGroup { SettingsTextFieldItem(identityKey = profileId + group.id, title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_05adbf557d50), value = group.opening, onValueChange = { text -> edit { it.copy(opening = text) } }, subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_c2bad11d6afd), singleLine = false) }
                    SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_7f80204448ae))
                    SettingsGroup { SettingsTextFieldItem(identityKey = profileId + group.id, title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_7f80204448ae), value = group.chat, onValueChange = { text -> edit { it.copy(chat = text) } }, placeholder = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_633df04b9d49), singleLine = false) }
                    SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_4989b5cf9483))
                    SettingsGroup { SettingsActionItem(icon = Icons.Default.Delete, title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_28f3d050234f), destructive = true, onClick = { deleting = true }) }
                    Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_4a11c79b6032), modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodySmall)
                }
                if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_30352098391a)) }, text = { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.prompt_delete_confirm, group.name)) }, confirmButton = { TextButton(onClick = {
                    deleting = false
                    val id = group.id
                    viewModel.changePrompts(profileId) { p ->
                        val remaining = p.availablePrompts.filterNot { it.id == id }.ifEmpty { listOf(PromptGroup(UUID.randomUUID().toString(), com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_881373050260))) }
                        p.copy(promptGroups = remaining, activePromptId = remaining.first().id)
                    }
                }) { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_3755f56f2f83)) } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_4d0b4688c787)) } })
            }
        }
    }
    pendingCard?.let { card ->
        AlertDialog(onDismissRequest = { pendingCard = null }, title = { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.card_import_title, card.name)) }, text = { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_3f68db538f8d)) }, confirmButton = { TextButton(onClick = { viewModel.changePrompts(profileId) { p -> p.copy(promptGroups = p.availablePrompts + card, activePromptId = card.id) }; pendingCard = null }) { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_60e2bcad85ac)) } }, dismissButton = { TextButton(onClick = { pendingCard = null }) { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_4d0b4688c787)) } })
    }
    importError?.let { message -> AlertDialog(onDismissRequest = { importError = null }, title = { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_a01a8d53938f)) }, text = { Text(message) }, confirmButton = { TextButton(onClick = { importError = null }) { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_f526c89937e1)) } }) }
}
