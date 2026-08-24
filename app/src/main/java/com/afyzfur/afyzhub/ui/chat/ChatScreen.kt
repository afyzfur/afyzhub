package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.domain.model.Message
import com.afyzfur.afyzhub.ui.components.MarkdownText
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 聊天页，应用的根页面。
 *
 * 结构变更（阶段 2）：原先由 HomeScreen 经导航进入并接收 conversationId 参数，
 * 现在改为应用启动目标，会话列表移入抽屉。因此当前会话不再来自路由参数，
 * 而是由 [ChatHostViewModel] 持有。
 *
 * 启动时为空白新会话（id 为 null），首次发送消息才落库，
 * 避免每次打开应用都产生一条空的"新对话"记录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    hostViewModel: ChatHostViewModel = koinViewModel(),
    viewModel: ChatViewModel = koinViewModel()
) {
    val conversations by hostViewModel.conversations.collectAsState()
    val currentConversationId by hostViewModel.currentConversationId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 会话切换时重新订阅消息；空白新会话则清空列表
    LaunchedEffect(currentConversationId) {
        val id = currentConversationId
        if (id != null) {
            viewModel.loadMessages(id)
        } else {
            viewModel.clearMessages()
        }
    }

    // 流式输出时最后一条消息内容会持续变化，需要一并作为滚动触发条件。
    val lastContentLength = messages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(messages.size, lastContentLength) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val currentTitle = conversations.firstOrNull { it.id == currentConversationId }?.title

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = AppShapeTokens.Drawer
            ) {
                ConversationDrawer(
                    conversations = conversations,
                    currentConversationId = currentConversationId,
                    onConversationClick = { id ->
                        hostViewModel.openConversation(id)
                        scope.launch { drawerState.close() }
                    },
                    onNewConversation = {
                        hostViewModel.startNewConversation()
                        scope.launch { drawerState.close() }
                    },
                    onDeleteConversation = { id ->
                        hostViewModel.deleteConversation(id)
                    },
                    onSettingsClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    }
                )
            }
        }
    ) {
        ChatContent(
            title = currentTitle,
            messages = messages,
            isLoading = isLoading,
            error = error,
            listState = listState,
            inputText = inputText,
            onInputChange = { inputText = it },
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onSend = {
                val text = inputText
                if (text.isNotBlank() && !isLoading) {
                    inputText = ""
                    // 会话 id 的获取延迟到 ViewModel 的协程内，
                    // 空白新会话在那时才落库
                    viewModel.sendMessage(text) { hostViewModel.ensureConversation() }
                }
            },
            onRetry = { messageId -> viewModel.retryMessage(messageId) },
            onClearError = { viewModel.clearError() }
        )
    }
}

/**
 * 聊天页主体内容，与抽屉解耦以便单独预览与测试。
 *
 * 消息渲染与流式光标逻辑沿用改版前的实现，本阶段只调整外层结构。
 * 输入区的容器化重做属于阶段 3。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatContent(
    title: String?,
    messages: List<Message>,
    isLoading: Boolean,
    error: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    inputText: String,
    onInputChange: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onSend: () -> Unit,
    onRetry: (Long) -> Unit,
    onClearError: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                // 空白新会话尚无标题，显示应用名占位
                title = { Text(title ?: "AfyzHub") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "打开会话列表")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (messages.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "发送一条消息开始对话",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageItem(
                            message = message,
                            onRetry = { onRetry(message.id) }
                        )
                    }
                    // 流式回复已在气泡内逐字呈现，无需额外的等待指示。
                    val streaming = messages.lastOrNull()
                        ?.let { !it.isFromUser && it.isSending } == true
                    if (isLoading && !streaming) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }

            error?.let { errorMessage ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearError) {
                            Text("关闭")
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入消息...") },
                        maxLines = 5,
                        enabled = !isLoading
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = onSend,
                        modifier = Modifier.size(56.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItem(
    message: Message,
    onRetry: () -> Unit = {}
) {
    val isUser = message.isFromUser

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = when {
                message.isFailed -> MaterialTheme.colorScheme.errorContainer
                isUser -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            // 助手消息可能含代码块，给更宽的上限以减少横向滚动。
            modifier = Modifier.widthIn(max = if (isUser) 300.dp else 340.dp)
        ) {
            val contentColor = when {
                message.isFailed -> MaterialTheme.colorScheme.onErrorContainer
                isUser -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            if (isUser || message.isFailed) {
                // 用户输入按原样显示，不解析 Markdown。
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    modifier = Modifier.padding(12.dp)
                )
            } else {
                MarkdownText(
                    // 流式生成中的空回复先显示光标，避免出现空白气泡。
                    text = message.content.ifEmpty { if (message.isSending) "▍" else "" },
                    color = contentColor,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // 失败的消息提供重试入口，避免留下无法处理的孤立消息。
        if (message.isFailed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.errorMessage ?: "发送失败",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onRetry) {
                    Text("重试")
                }
            }
        } else if (message.isSending && isUser) {
            // 仅用户消息需要“发送中”提示；助手消息的进度由光标体现。
            Text(
                text = "发送中…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
