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
                    context.contentResolver.openInputStream(uri)?.use { CharacterCardImporter.read(it) } ?: error("无法读取文件")
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { importError = e.message ?: "导入失败" }
            finally { importing = false }
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            SettingsPageHeader(title = "高级提示词", onNavigateBack = onNavigateBack)
            if (profile == null) {
                Text("配置载入中或已被删除", modifier = Modifier.padding(24.dp))
            } else {
                val group = profile.activePrompt
                fun edit(transform: (PromptGroup) -> PromptGroup) {
                    val groupId = group.id
                    viewModel.changePrompts(profileId) { p -> p.copy(promptGroups = p.availablePrompts.map { if (it.id == groupId) transform(it) else it }) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    SettingsCategoryTitle("提示词分组")
                    SettingsGroup {
                        SettingsDropdownItem(icon = Icons.Default.PlayArrow, title = "当前分组", subtitle = "独立保存，同一 API 配置可随时切换", options = profile.availablePrompts, selected = group, label = { it.name }, onSelect = { selected -> viewModel.changePrompts(profileId) { it.copy(activePromptId = selected.id) } })
                        SettingsItemDivider()
                        SettingsTextFieldItem(identityKey = profileId + group.id, title = "分组名称", value = group.name, onValueChange = { text -> edit { it.copy(name = text) } })
                        SettingsItemDivider()
                        SettingsActionItem(icon = Icons.Default.Add, title = "新建分组", onClick = {
                            viewModel.changePrompts(profileId) { p ->
                                val next = PromptGroup(UUID.randomUUID().toString(), "提示词 ${p.availablePrompts.size + 1}")
                                p.copy(promptGroups = p.availablePrompts + next, activePromptId = next.id)
                            }
                        })
                        SettingsItemDivider()
                        SettingsActionItem(icon = Icons.Default.Add, title = if (importing) "正在读取角色卡…" else "导入角色卡", subtitle = "支持 JSON、PNG 角色卡；导入为新分组，不覆盖已有内容", onClick = { if (!importing) launcher.launch(arrayOf("*/*")) })
                    }
                    SettingsCategoryTitle("角色设定")
                    SettingsGroup { SettingsTextFieldItem(identityKey = profileId + group.id, title = "角色设定", value = group.role, onValueChange = { text -> edit { it.copy(role = text) } }, placeholder = "身份、性格、背景和行为边界", singleLine = false) }
                    SettingsCategoryTitle("开场白")
                    SettingsGroup { SettingsTextFieldItem(identityKey = profileId + group.id, title = "开场白", value = group.opening, onValueChange = { text -> edit { it.copy(opening = text) } }, subtitle = "启用时作为新对话的首条助手消息；不修改已有对话", singleLine = false) }
                    SettingsCategoryTitle("聊天设定")
                    SettingsGroup { SettingsTextFieldItem(identityKey = profileId + group.id, title = "聊天设定", value = group.chat, onValueChange = { text -> edit { it.copy(chat = text) } }, placeholder = "回答语言、格式和聊天规则", singleLine = false) }
                    SettingsCategoryTitle("管理")
                    SettingsGroup { SettingsActionItem(icon = Icons.Default.Delete, title = "删除当前提示词分组", destructive = true, onClick = { deleting = true }) }
                    Text("修改自动保存。关闭总开关只停止应用提示词，不清空任何分组。", modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodySmall)
                }
                if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("删除提示词分组") }, text = { Text("将删除「${group.name}」，此操作无法恢复。") }, confirmButton = { TextButton(onClick = {
                    deleting = false
                    val id = group.id
                    viewModel.changePrompts(profileId) { p ->
                        val remaining = p.availablePrompts.filterNot { it.id == id }.ifEmpty { listOf(PromptGroup(UUID.randomUUID().toString(), "默认提示词")) }
                        p.copy(promptGroups = remaining, activePromptId = remaining.first().id)
                    }
                }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } })
            }
        }
    }
    pendingCard?.let { card ->
        AlertDialog(onDismissRequest = { pendingCard = null }, title = { Text("导入「${card.name}」") }, text = { Text("将新增提示词分组并选中。导入角色设定、首条开场白和聊天规则；世界书、附件、脚本和替代开场白暂不执行，原始 JSON 会保留。请先检查导入内容。") }, confirmButton = { TextButton(onClick = { viewModel.changePrompts(profileId) { p -> p.copy(promptGroups = p.availablePrompts + card, activePromptId = card.id) }; pendingCard = null }) { Text("导入") } }, dismissButton = { TextButton(onClick = { pendingCard = null }) { Text("取消") } })
    }
    importError?.let { message -> AlertDialog(onDismissRequest = { importError = null }, title = { Text("导入失败") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { importError = null }) { Text("确定") } }) }
}
