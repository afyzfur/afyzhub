package com.afyzhub.aichat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
            onDelete = viewModel::deleteConversation
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
                    onClear = viewModel::clearConversations
                )
            }
        }
    }
}

@Composable
private fun ChatScreen(state: AppState, viewModel: ChatViewModel) {
    var input by remember { mutableStateOf("") }
    val conversation = state.conversations.firstOrNull {
        it.id == state.selectedConversationId
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
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息") },
                    maxLines = 5,
                    enabled = !state.isGenerating
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (state.isGenerating) {
                            viewModel.stopGenerating()
                        } else {
                            viewModel.sendMessage(input)
                            input = ""
                        }
                    },
                    enabled = state.isGenerating || input.isNotBlank()
                ) {
                    if (state.isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("停止")
                    } else {
                        Text("发送")
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
            .padding(vertical = 5.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Text(
            text = if (isUser) "你" else "AI",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer {
            Text(
                text = message.content.ifBlank {
                    if (isUser) "" else "正在生成…"
                },
                modifier = Modifier
                    .fillMaxWidth(if (isUser) 0.88f else 1f)
                    .background(
                        color = if (isUser) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(12.dp),
                fontFamily = if (message.content.contains("```")) {
                    FontFamily.Monospace
                } else {
                    FontFamily.Default
                }
            )
        }
        TextButton(
            onClick = {
                clipboard.setText(AnnotatedString(message.content))
            }
        ) {
            Text("复制")
        }
    }
}

@Composable
private fun ConversationsDialog(
    state: AppState,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit
) {
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

@Composable
private fun SettingsScreen(
    state: AppState,
    onSave: (ApiConfig, String) -> Unit,
    onClear: () -> Unit
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
    var showClearConfirm by remember { mutableStateOf(false) }

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
            value = model,
            onValueChange = { model = it },
            label = { Text("模型 ID") },
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
            Switch(checked = useStream, onCheckedChange = { useStream = it })}
        Text(
            "关闭后使用普通请求，适合不支持 SSE 流式的第三方服务",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = {
                onSave(
                    ApiConfig(
                        baseUrl = baseUrl,
                        model = model,
                        systemPrompt = systemPrompt,
                        temperature = temperature,
                        maxTokens = maxTokens.toIntOrNull() ?: 2048,
                        useStream = useStream
                    ),
                    apiKey
                )
            },
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
    }
}