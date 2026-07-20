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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.SolidColor
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
import com.afyzhub.aichat.data.InputBarStyle

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
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (page == Page.CHAT) "afyzhub" else "设置",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (page == Page.CHAT) {
                            Text(
                                state.config.model,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (page == Page.CHAT) showConversations = true
                            else page = Page.CHAT
                        }
                    ) {
                        Icon(
                            imageVector = if (page == Page.CHAT) Icons.Filled.Menu
                            else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (page == Page.CHAT) "会话列表" else "返回"
                        )
                    }
                },
                actions = {
                    if (page == Page.CHAT) {
                        IconButton(onClick = { viewModel.newConversation() }) {
                            Icon(Icons.Filled.Add, contentDescription = "新建对话")
                        }
                        IconButton(onClick = { page = Page.SETTINGS }) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置")
                        }
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

        // 悬浮卡片式输入区（颜色/透明度随 inputBarStyle 变化）
        val baseColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
        val barColor = when (state.config.inputBarStyle) {
            InputBarStyle.SOLID -> baseColor
            InputBarStyle.TRANSLUCENT -> baseColor.copy(alpha = 0.82f)
            InputBarStyle.FROSTED -> baseColor.copy(alpha = 0.6f)
        }
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = barColor,
            shadowElevation = if (state.config.inputBarStyle == InputBarStyle.SOLID) 6.dp else 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isGenerating,
                    maxLines = 5,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (input.isEmpty()) {
                            Text(
                                "输入消息…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
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
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (state.isGenerating) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = "停止生成",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                tint = if (input.isNotBlank()) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp)
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        // AI 头像
        if (!isUser) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // 气泡：更大圆角；用户 primary 实色，AI 用高层容器色
            Surface(
                color = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                },
                shape = if (isUser) {
                    RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp)
                } else {
                    RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 22.dp)
                }
            ) {
                SelectionContainer {
                    Text(
                        text = message.content.ifBlank {
                            if (isUser) "" else "正在思考…"
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontFamily = if (message.content.contains("```")) {
                            FontFamily.Monospace
                        } else {
                            FontFamily.Default
                        }
                    )
                }
            }
            // 复制按钮：仅 AI 消息且有内容时显示
            if (!isUser && message.content.isNotBlank()) {
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(message.content)) },
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "复制",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
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
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("新建对话")
                }
                Spacer(Modifier.height(4.dp))
                state.conversations.forEach { conversation ->
                    val isSelected = conversation.id == state.selectedConversationId
                    Surface(
                        onClick = { onSelect(conversation.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            Color.Transparent
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = conversation.title,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            IconButton(
                                onClick = {
                                    renameText = conversation.title
                                    renamingId = conversation.id
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "重命名",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDelete(conversation.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
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

// 设置分组卡片：标题 + 圆角容器包裹内容
@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp)
    )
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
    Spacer(Modifier.height(18.dp))
}

// 常用上下文长度预设（token）：32K / 64K / 128K / 256K / 512K / 1M
private val CONTEXT_PRESETS = listOf(32_000, 64_000, 128_000, 256_000, 512_000, 1_000_000)

// 上下文窗口友好显示：四舍五入到最接近的 K / M，避免出现 65K、262K 之类的零头
private fun formatContext(tokens: Int): String = when {
    tokens >= 1_000_000 -> {
        val m = tokens / 1_000_000.0
        if (m == m.toInt().toDouble()) "${m.toInt()}M" else "%.1fM".format(m)
    }
    else -> "${Math.round(tokens / 1000.0).toInt()}K"
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
    var inputBarStyle by remember(state.config) { mutableStateOf(state.config.inputBarStyle) }
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
        contextLimit = contextLimit.toIntOrNull() ?: 32_768,
        inputBarStyle = inputBarStyle
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
            .padding(16.dp)
    ) {
      SettingsGroup(title = "API 服务") {
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
      }

      SettingsGroup(title = "生成参数") {
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
      }

      SettingsGroup(title = "外观") {
        // 输入栏外观
        Text("输入栏样式", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = inputBarStyle == InputBarStyle.SOLID,
                onClick = { inputBarStyle = InputBarStyle.SOLID },
                label = { Text("不透明") }
            )
            FilterChip(
                selected = inputBarStyle == InputBarStyle.TRANSLUCENT,
                onClick = { inputBarStyle = InputBarStyle.TRANSLUCENT },
                label = { Text("半透明") }
            )
            FilterChip(
                selected = inputBarStyle == InputBarStyle.FROSTED,
                onClick = { inputBarStyle = InputBarStyle.FROSTED },
                label = { Text("磨砂") }
            )
        }
      }

      SettingsGroup(title = "上下文") {
        Text("上下文长度", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = contextMode == ContextMode.LIMITED,
                onClick = { contextMode = ContextMode.LIMITED },
                label = { Text("自定义长度") }
            )
            FilterChip(
                selected = contextMode == ContextMode.MAX,
                onClick = { contextMode = ContextMode.MAX },
                label = { Text("MAX（按模型自动）") }
            )
        }
        if (contextMode == ContextMode.LIMITED) {
            // 常用长度预设
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CONTEXT_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = contextLimit == preset.toString(),
                        onClick = { contextLimit = preset.toString() },
                        label = { Text(formatContext(preset)) }
                    )
                }
            }
            OutlinedTextField(
                value = contextLimit,
                onValueChange = { contextLimit = it.filter(Char::isDigit) },
                label = { Text("上下文长度（token）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        } else {
            Text(
                "MAX 模式会根据所选模型的上下文窗口，尽可能多地携带历史消息。" +
                    "仅支持官方标准模型（如 gpt-4o、claude、deepseek、gemini 等）；" +
                    "第三方中转的自定义模型名无法识别，建议改用「自定义长度」手动指定。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
      }

      SettingsGroup(title = "主题色") {
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
      }

      Button(
          onClick = { onSave(currentConfig(), apiKey) },
          modifier = Modifier.fillMaxWidth()
      ) {
          Text("保存配置")
      }
      Spacer(Modifier.height(18.dp))

      SettingsGroup(title = "数据与隐私") {
        Text(
            "API Key 使用 Android Keystore 加密并仅保存在本设备。会话记录保存在应用私有存储中。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = { showClearConfirm = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("清除本地会话")
        }
        OutlinedButton(
            onClick = { showClearAllConfirm = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("清除全部数据（含 API Key）")
        }
      }
    }
}