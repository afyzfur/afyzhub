package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.domain.model.Message
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

    val settings by hostViewModel.settings.collectAsState()
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
                    modelLabel = settings.model,
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
            modelLabel = "${settings.model} · ${settings.provider.displayName}",
            providerLabel = settings.provider.displayName,
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
            onPromptClick = { prompt -> inputText = prompt },
            onRetry = { messageId -> viewModel.retryMessage(messageId) },
            onClearError = { viewModel.clearError() }
        )
    }
}


/**
 * 聊天页主体内容，与抽屉解耦。
 *
 * 阶段 3 改动：
 * - 顶栏改为双行，主标题为会话名，副标题为模型与提供商
 * - 空会话交给 EmptyChatContent，不再是一行灰字
 * - 消息渲染交给 MessageBlock，助手消息全宽
 * - 输入区交给 ChatInputBar，单一圆角容器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatContent(
    title: String?,
    modelLabel: String,
    providerLabel: String,
    messages: List<Message>,
    isLoading: Boolean,
    error: String?,
    listState: LazyListState,
    inputText: String,
    onInputChange: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onSend: () -> Unit,
    onPromptClick: (String) -> Unit,
    onRetry: (Long) -> Unit,
    onClearError: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                title = {
                    Column {
                        // 空会话时省略会话名，避免与首屏问候语重复占位
                        if (title != null) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = modelLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
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
                EmptyChatContent(
                    prompts = DefaultPrompts,
                    onPromptClick = onPromptClick,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    // 间距比改版前放宽，助手消息取消容器后需要更多留白来分隔
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBlock(
                            message = message,
                            onRetry = { onRetry(message.id) }
                        )
                    }
                    // 流式回复已在正文内逐字呈现，无需额外的等待指示
                    val streaming = messages.lastOrNull()
                        ?.let { !it.isFromUser && it.isSending } == true
                    if (isLoading && !streaming) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            error?.let { errorMessage ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = AppShapeTokens.SettingsGroup,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 4.dp),
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

            ChatInputBar(
                value = inputText,
                onValueChange = onInputChange,
                onSend = onSend,
                providerLabel = providerLabel,
                isLoading = isLoading
            )
        }
    }
}
