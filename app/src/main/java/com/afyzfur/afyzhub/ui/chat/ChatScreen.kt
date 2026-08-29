package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import com.afyzfur.afyzhub.data.settings.ChatAppearance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.afyzfur.afyzhub.domain.model.SendPhase
import com.afyzfur.afyzhub.ui.components.ChatBackgroundLayer
import com.afyzfur.afyzhub.data.settings.MessageDisplayOptions
import com.afyzfur.afyzhub.domain.model.ThinkingEffort
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
    onNavigateToProvider: () -> Unit,
    /** 打开纯模型切换页 */
    onNavigateToModelPicker: () -> Unit,
    hostViewModel: ChatHostViewModel = koinViewModel(),
    viewModel: ChatViewModel = koinViewModel()
) {
    val conversations by hostViewModel.conversations.collectAsState()
    val currentConversationId by hostViewModel.currentConversationId.collectAsState()
    val groups by hostViewModel.groups.collectAsState()
    // 显示配置组的自定义名称而非提供商名。多组同一提供商时全都
    // 写「OpenAI」分不出是哪一组，而组名是用户自己起的
    val profileName by hostViewModel.activeProfileName.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val sendPhase by viewModel.sendPhase.collectAsState()

    /** 长按选中的消息，非空时显示操作表 */
    var actionTarget by remember { mutableStateOf<Message?>(null) }

    /** 「信息」选中的消息，与操作表分开以便先关表再开对话框 */
    var infoTarget by remember { mutableStateOf<Message?>(null) }

    val clipboard = LocalClipboardManager.current
    val error by viewModel.error.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    // 「编辑并重发」记下的消息 id：非空时下一次发送要先删掉它及其后续。
    // 记在这里而非直接执行删除，是为了让用户改完不发时消息不会丢
    var editingMessageId by remember { mutableStateOf<Long?>(null) }

    /** 思考程度选择表的开关 */
    var showEffortSheet by remember { mutableStateOf(false) }

    val undoable by viewModel.undoable.collectAsState()

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
    val uiPreferences by hostViewModel.uiPreferences.collectAsState()
    val currentTitle = conversations.firstOrNull { it.id == currentConversationId }?.title

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = AppShapeTokens.Drawer
            ) {
                ConversationDrawer(
                    existingGroups = groups,
                    onTogglePin = hostViewModel::setPinned,
                    onToggleStar = hostViewModel::setStarred,
                    onRenameConversation = hostViewModel::renameConversation,
                    onUpdateNote = hostViewModel::updateNote,
                    onMoveToGroup = hostViewModel::moveToGroup,
                    conversations = conversations,
                    currentConversationId = currentConversationId,
                    modelLabel = settings.model,
                    appearance = uiPreferences.chatAppearance,
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
                    },
                    onModelClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToProvider()
                    }
                )
            }
        }
    ) {
        ChatContent(
            title = currentTitle,
            modelLabel = "${settings.model} · $profileName",
            providerLabel = profileName,
            modelName = settings.model,
            thinkingEffort = settings.thinkingEffort,
            onCycleThinkingEffort = { showEffortSheet = true },
            appearance = uiPreferences.chatAppearance,
            quickPrompts = uiPreferences.quickPrompts,
            shufflePrompts = uiPreferences.shufflePrompts,
            displayOptions = uiPreferences.messageDisplay,
            messages = messages,
            isLoading = isLoading,
            sendPhase = sendPhase,
            error = error,
            listState = listState,
            inputText = inputText,
            onInputChange = { inputText = it },
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onSend = {
                val text = inputText
                if (text.isNotBlank() && !isLoading) {
                    inputText = ""
                    val editing = editingMessageId
                    editingMessageId = null
                    // 会话 id 的获取延迟到 ViewModel 的协程内，
                    // 空白新会话在那时才落库
                    if (editing != null) {
                        viewModel.editAndResend(editing, text) {
                            hostViewModel.ensureConversation()
                        }
                    } else {
                        viewModel.sendMessage(text) { hostViewModel.ensureConversation() }
                    }
                }
            },
            onStop = { viewModel.stopGenerating() },
            onPickModel = onNavigateToModelPicker,
            undoable = undoable,
            onUndoRemoval = { viewModel.undoRemoval() },
            onDismissUndo = { viewModel.dismissUndo() },
            onLongPress = { message -> actionTarget = message },
            onPromptClick = { prompt -> inputText = prompt },
            onRetry = { messageId -> viewModel.retryMessage(messageId) },
            onClearError = { viewModel.clearError() }
        )
    }

    actionTarget?.let { target ->
        val dismiss = { actionTarget = null }
        MessageActionSheet(
            fromUser = target.isFromUser,
            onDismiss = dismiss,
            onCopy = {
                clipboard.setText(AnnotatedString(target.content))
                dismiss()
            },
            onDelete = {
                viewModel.deleteMessage(target.id)
                dismiss()
            },
            onInfo = {
                // 先关操作表再开对话框，两者同时存在会叠在一起
                dismiss()
                infoTarget = target
            },
            onRegenerate = {
                viewModel.regenerate(target.id)
                dismiss()
            },
            onEditResend = {
                // 只回填输入框并记下待替换的消息，删除留到真正发送时
                // 一起做。此前在这里就先删，用户若改完不发，消息已经
                // 没了；而且删除与发送是两个独立协程，暂停键会失灵
                inputText = target.content
                editingMessageId = target.id
                dismiss()
            },
            onRollback = {
                // 把被回滚掉的第一条内容填进输入栏。回滚通常是
                // "这轮问得不好，重新问"，内容还要用
                viewModel.rollbackTo(target.id) { content -> inputText = content }
                dismiss()
            }
        )
    }

    infoTarget?.let { target ->
        MessageInfoDialog(message = target, onDismiss = { infoTarget = null })
    }

    if (showEffortSheet) {
        ThinkingEffortSheet(
            current = settings.thinkingEffort,
            onSelect = hostViewModel::setThinkingEffort,
            onDismiss = { showEffortSheet = false }
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
    modelName: String,
    thinkingEffort: ThinkingEffort,
    onCycleThinkingEffort: () -> Unit,
    appearance: ChatAppearance,
    quickPrompts: List<String>,
    shufflePrompts: Boolean,
    displayOptions: MessageDisplayOptions,
    messages: List<Message>,
    isLoading: Boolean,
    sendPhase: SendPhase,
    error: String?,
    listState: LazyListState,
    inputText: String,
    onInputChange: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    /** 点输入栏左下角的模型区域 */
    onPickModel: () -> Unit,
    /** 可撤回的删除，非空时在输入栏上方显示提示 */
    undoable: UndoableRemoval? = null,
    onUndoRemoval: () -> Unit = {},
    onDismissUndo: () -> Unit = {},
    onLongPress: (Message) -> Unit,
    onPromptClick: (String) -> Unit,
    onRetry: (Long) -> Unit,
    onClearError: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 背景图铺满整个页面（含系统栏区域）。图片与效果的组合逻辑
        // 收在 ChatBackgroundLayer 里，设置页的预览用的是同一个组件，
        // 因此预览所见即此处所得
        ChatBackgroundLayer(
            appearance = appearance,
            modifier = Modifier.fillMaxSize()
        )

        Scaffold(
            // 有背景图时容器透明，让下层的图片透出；
            // 顶栏与输入框自身的色阶仍然保留，否则文字会直接压在图上
            containerColor = if (appearance.hasBackgroundImage) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            // 键盘弹出时整体上移，使输入框始终可见。
            // 需配合 manifest 的 windowSoftInputMode="adjustResize"，
            // 否则窗口不重新布局，内容会被整体顶到状态栏下方
            modifier = Modifier.imePadding(),
            // 底部 inset 交给输入框自己处理，使其背景能延伸到屏幕底边。
            // 若保留默认值，Scaffold 与输入框会各加一次导航栏内边距
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
            ),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        // 顶栏透明由设置决定：铺色会在背景图上方切出一条色带，
                    // 但不透明时文字对比度更稳定，取舍交给用户
                    containerColor = if (appearance.transparentTopBar) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
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
                        prompts = quickPrompts,
                        shufflePrompts = shufflePrompts,
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
                                displayOptions = displayOptions,
                                appearance = appearance,
                                providerLabel = providerLabel,
                                onRetry = { onRetry(message.id) },
                                onLongPress = { onLongPress(message) }
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

                // 撤回提示紧贴输入栏上方：删除操作与撤回入口在
                // 同一处视线内，且不遮挡刚被删掉那段内容的位置
                UndoBar(
                    removal = undoable,
                    onUndo = onUndoRemoval,
                    onDismiss = onDismissUndo
                )
                ChatInputBar(
                    transparent = appearance.transparentInputBar,
                    value = inputText,
                    onValueChange = onInputChange,
                    onSend = onSend,
                    onStop = onStop,
                    onPickModel = onPickModel,
                    providerLabel = providerLabel,
                    isLoading = isLoading,
                    statusLabel = sendPhase.label,
                    modelName = modelName,
                    thinkingEffort = thinkingEffort,
                    onCycleThinkingEffort = onCycleThinkingEffort
                )
            }
        }
    }
}
