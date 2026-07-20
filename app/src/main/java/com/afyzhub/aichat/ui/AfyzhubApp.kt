package com.afyzhub.aichat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afyzhub.aichat.ChatViewModel
import com.afyzhub.aichat.data.ApiConfig
import com.afyzhub.aichat.data.AppState
import com.afyzhub.aichat.data.ChatMessage
import com.afyzhub.aichat.data.ContextMode

private enum class Page { CHAT, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AfyzhubApp(viewModel: ChatViewModel) {
    val state by viewModel.state
    var page by remember { mutableStateOf(Page.CHAT) }
    var showConversations by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissNotice()
        }
    }

    if (showConversations) {
        ConversationsDialog(
            state = state,
            onDismiss = { showConversations = false },
            onNew = {
                viewModel.newConversation()
                showConversations = false
            },
            onSelect = {
                viewModel.selectConversation(it)
                showConversations = false
            },
            onDelete = viewModel::deleteConversation,
            onRename = viewModel::renameConversation
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (page == Page.CHAT) {
                            "afyzhub · ${state.config.model}"
                        } else {
                            "API 与生成设置"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    TextButton(
                        onClick = {
                            if (page == Page.CHAT) showConversations = true
                            else page = Page.CHAT
                        }
                    ) {
                        Text(if (page == Page.CHAT) "会话" else "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            page = if (page == Page.CHAT) Page.SETTINGS else Page.CHAT
                        }
                    ) {
                        Text(if (page == Page.CHAT) "配置" else "聊天")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (page) {
                Page.CHAT -> ChatScreen(state, viewModel)
                Page.SETTINGS -> SettingsScreen(
                    state = state,
                    onSave = { config, key ->
                        viewModel.saveConfig(config, key)
                        page = Page.CHAT
                    },
                    onClear = viewModel::clearConversations,
                    onClearAll = viewModel::clearAllData,
                    onLoadModels = { cfg, key -> viewModel.loadModels(cfg, key) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(state: AppState, viewModel: ChatViewModel) {
    var input by remember { mutableStateOf("") }
    val conversation = state.conversations.firstOrNull {
        it.id == state.selectedConversationId
    }

    // 请求失败时把输入回填到输入框，便于用户直接重发
    LaunchedEffect(state.lastFailedInput) {
        state.lastFailedInput?.let {
            if (input.isBlank()) input = it
            viewModel.consumeFailedInput()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        if (conversation == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "开始一段新对话",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = if (state.hasApiKey) {
                            "API 已配置"
                        } else {
                            "请先点击右上角“配置”填写 API Key"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                    )
                    Button(onClick = viewModel::newConversation) {
                        Text("新建对话")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(conversation.messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
            }
        }

        Surface(tonalElevation = 3.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息…") },
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    enabled = !state.isGenerating
                )
                Spacer(Modifier.width(8.dp))
                // 圆形发送/停止按钮
                Surface(
                    onClick = {
                        if (state.isGenerating) {
                            viewModel.stopGenerating()
                        } else {
                            viewModel.sendMessage(input)
                            input = ""
                        }
                    },
                    enabled = state.isGenerating || input.isNotBlank(),
                    shape = CircleShape,
                    color = if (state.isGenerating || input.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (state.isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                "↑",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (input.isNotBlank()) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val clipboard = LocalClipboardManager.current
    val isUser = message.role == "user"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // 角色标签
        Text(
            text = if (isUser) "你" else "afyzhub",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 3.dp)
        )
        // 气泡：用户右侧、AI 左侧，非对称圆角更贴近聊天观感
        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = if (isUser) {
                RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
            } else {
                RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
            },
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(if (isUser) 0.9f else 1f)
        ) {
            SelectionContainer {
                Text(
                    text = message.content.ifBlank {
                        if (isUser) "" else "正在生成…"
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontFamily = if (message.content.contains("```")) {
                        FontFamily.Monospace
                    } else {
                        FontFamily.Default
                    }
                )
            }
        }
        // 复制按钮：仅在有内容时显示，轻量小号
        if (message.content.isNotBlank()) {
            TextButton(
                onClick = { clipboard.setText(AnnotatedString(message.content)) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 8.dp, vertical = 2.dp
                )
            ) {
                Text("复制", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ConversationsDialog(
    state: AppState,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit
) {
    // 正在重命名的会话 id 与临时输入
    var renamingId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    if (renamingId != null) {
        AlertDialog(
            onDismissRequest = { renamingId = null },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renamingId?.let { onRename(it, renameText) }
                        renamingId = null
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingId = null }) {
                    Text("取消")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("会话") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Button(
                    onClick = onNew,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("新建对话")
                }
                state.conversations.forEach { conversation ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(conversation.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = conversation.title,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (
                                conversation.id == state.selectedConversationId
                            ) FontWeight.Bold else FontWeight.Normal
                        )
                        TextButton(
                            onClick = {
                                renameText = conversation.title
                                renamingId = conversation.id
                            }
                        ) {
                            Text("重命名")
                        }
                        TextButton(onClick = { onDelete(conversation.id) }) {
                            Text("删除")
                        }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

// 上下文窗口友好显示：>=1M 显示为 M，否则显示为 K
private fun formatContext(tokens: Int): String = when {
    tokens >= 1_000_000 -> {
        val m = tokens / 1_000_000.0
        if (m == m.toInt().toDouble()) "${m.toInt()}M" else "%.1fM".format(m)
    }
    else -> "${tokens / 1000}K"
}

// 预设主题色（种子色）。null 表示跟随系统动态取色。
private val PRESET_SEED_COLORS: List<Pair<String, Long?>> = listOf(
    "动态" to null,
    "橙" to 0xFFF57C00,
    "蓝" to 0xFF1976D2,
    "绿" to 0xFF388E3C,
    "紫" to 0xFF7B1FA2,
    "红" to 0xFFD32F2F,
    "青" to 0xFF00838F,
    "粉" to 0xFFC2185B
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: AppState,
    onSave: (ApiConfig, String) -> Unit,
    onClear: () -> Unit,
    onClearAll: () -> Unit,
    onLoadModels: (ApiConfig, String) -> Unit
) {
    var baseUrl by remember(state.config) { mutableStateOf(state.config.baseUrl) }
    var model by remember(state.config) { mutableStateOf(state.config.model) }
    var apiKey by remember { mutableStateOf("") }
    var systemPrompt by remember(state.config) {
        mutableStateOf(state.config.systemPrompt)
    }
    var temperature by remember(state.config) {
        mutableFloatStateOf(state.config.temperature)
    }
    var maxTokens by remember(state.config) {
        mutableStateOf(state.config.maxTokens.toString())
    }
    var useStream by remember(state.config) { mutableStateOf(state.config.useStream) }
    var seedColor by remember(state.config) { mutableStateOf(state.config.themeSeedColor) }
    var contextMode by remember(state.config) { mutableStateOf(state.config.contextMode) }
    var contextLimit by remember(state.config) {
        mutableStateOf(state.config.contextLimit.toString())
    }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    // 构造当前编辑中的临时配置，供拉取模型使用
    fun currentConfig() = ApiConfig(
        baseUrl = baseUrl,
        model = model,
        systemPrompt = systemPrompt,
        temperature = temperature,
        maxTokens = maxTokens.toIntOrNull() ?: 2048,
        useStream = useStream,
        themeSeedColor = seedColor,
        contextMode = contextMode,
        contextLimit = contextLimit.toIntOrNull() ?: 20
    )

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清除全部会话？") },
            text = { Text("此操作不可撤销，但不会删除 API 配置。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClear()
                        showClearConfirm = false
                    }
                ) {
                    Text("确认清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("清除全部数据？") },
            text = { Text("将删除所有会话以及本机加密保存的 API Key，此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showClearAllConfirm = false
                    }
                ) {
                    Text("确认清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "OpenAI-compatible 服务",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Base URL（仅 HTTPS）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = {
                Text(
                    if (state.hasApiKey) {
                        "API Key（留空则保持不变）"
                    } else {
                        "API Key"
                    }
                )
            },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // 模型：可手动输入，也可从 /models 拉取后下拉选择
        ExposedDropdownMenuBox(
            expanded = modelMenuExpanded,
            onExpandedChange = { modelMenuExpanded = !modelMenuExpanded }
        ) {
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("模型 ID") },
                singleLine = true,
                trailingIcon = {
                    if (state.availableModels.isNotEmpty()) {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            if (state.availableModels.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = modelMenuExpanded,
                    onDismissRequest = { modelMenuExpanded = false }
                ) {
                    state.availableModels.forEach { m ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(m.id)
                                    m.contextWindow?.let {
                                        Text(
                                            "上下文 ${formatContext(it)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            onClick = {
                                model = m.id
                                modelMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { onLoadModels(currentConfig(), apiKey) },
                enabled = !state.isLoadingModels
            ) {
                if (state.isLoadingModels) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("获取中")
                } else {
                    Text("获取模型列表")
                }
            }
            if (state.availableModels.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "共 ${state.availableModels.size} 个",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedTextField(
            value = systemPrompt,
            onValueChange = { systemPrompt = it },
            label = { Text("System Prompt") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )

        Text("Temperature：${"%.1f".format(temperature)}")
        Slider(
            value = temperature,
            onValueChange = { temperature = it },
            valueRange = 0f..2f
        )

        OutlinedTextField(
            value = maxTokens,
            onValueChange = { maxTokens = it.filter(Char::isDigit) },
            label = { Text("最大输出 Token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("流式输出（SSE）", modifier = Modifier.weight(1f))
            Switch(checked = useStream, onCheckedChange = { useStream = it })
        }
        Text(
            "关闭后使用普通请求，适合不支持 SSE 流式的第三方服务",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // 上下文长度模式
        Text("上下文长度", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = contextMode == ContextMode.LIMITED,
                onClick = { contextMode = ContextMode.LIMITED },
                label = { Text("固定条数") }
            )
            FilterChip(
                selected = contextMode == ContextMode.MAX,
                onClick = { contextMode = ContextMode.MAX },
                label = { Text("MAX（按模型自动）") }
            )
        }
        if (contextMode == ContextMode.LIMITED) {
            OutlinedTextField(
                value = contextLimit,
                onValueChange = { contextLimit = it.filter(Char::isDigit) },
                label = { Text("携带最近消息条数") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        } else {
            Text(
                "MAX 模式会根据所选模型的上下文窗口，尽可能多地携带历史消息",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // 主题色
        Text("主题色", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PRESET_SEED_COLORS.forEach { (name, colorValue) ->
                val selected = seedColor == colorValue
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .then(
                                if (colorValue != null) {
                                    Modifier.background(Color(colorValue))
                                } else {
                                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                                }
                            )
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .clickable { seedColor = colorValue }
                    )
                    Text(
                        name,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        Button(
            onClick = { onSave(currentConfig(), apiKey) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存配置")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("数据与隐私", style = MaterialTheme.typography.titleMedium)
        Text(
            "API Key 使用 Android Keystore 加密并仅保存在本设备。会话记录保存在应用私有存储中。",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = { showClearConfirm = true }) {
            Text("清除本地会话")
        }
        OutlinedButton(onClick = { showClearAllConfirm = true }) {
            Text("清除全部数据（含 API Key）")
        }
    }
}