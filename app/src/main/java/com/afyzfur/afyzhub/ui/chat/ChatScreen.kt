package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import com.afyzfur.afyzhub.ui.components.ModelIcon
import com.afyzfur.afyzhub.ui.settings.ApiProfilesViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
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
import kotlinx.coroutines.delay
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
    onOpenBrowser: (String) -> Unit = {},
    apiProfilesViewModel: ApiProfilesViewModel = koinViewModel(),
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

    /** 模型选择半屏选择器开关 */
    var showModelPicker by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible

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

    /**
     * 滚动跟随策略。
     *
     * 贴底即时滚动（scrollToItem，无动画）+ 手势结算：
     * - 跟随中内容每变一次立即贴底。不用 animateScrollToItem：动画会把
     *   isScrollInProgress 置真，与用户手势无法区分；流式增量一个接一个，
     *   动画反复被打断重启，跟随状态在竞争里悄悄失效。即时滚动没有动画，
     *   isScrollInProgress 只剩用户手势一个来源
     * - 手势进行中一旦离开底部区域立即停跟随——上滑要抢在 fling 结束前
     *   生效，否则快速上滑会被中途到达的增量拽回底部
     * - 手势结束（settle）时按最终位置结算：停在底部就恢复跟随
     */
    // 跟随不再依赖开关状态: 由 atBottom(末尾项是否可见) + 内容变化直接决定
    // 手指按下即置位: 手势进行中暂停程序滚动, 抬起后按最终位置结算
    val pressedState = remember { mutableStateOf(false) }
    val lastTouchAt = remember { mutableStateOf(0L) }

    // 跟随判定: 看列表末尾那条内容“是否还在视口内可见”。
    // scrollToItem(messages.size) 会把末尾锚点滚进视口; 用户上滑后
    // 末尾项移出视口, layoutInfo 里就看不到它。这个判定与列表长短、
    // 是否可继续滚动都无关, 也不依赖任何手势事件。
    val atBottom by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastIndex = info.totalItemsCount - 1
            if (lastIndex < 0) false
            else info.visibleItemsInfo.any { it.index == lastIndex }
        }
    }
    val lastContentLength = messages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(messages.size, lastContentLength) {
        if (messages.isEmpty()) return@LaunchedEffect
        // 紧接着用户发话: 无条件回底, 自己的话必须进视野
        if (messages.last().isFromUser) {
            listState.scrollToItem(messages.size)
            return@LaunchedEffect
        }
        // 流式内容增长: 只有末尾项仍在视口内(用户没上滑离开底部)才跟随
        if (atBottom) {
            listState.scrollToItem(messages.size)
        }
    }
    val settings by hostViewModel.settings.collectAsState()
    val uiPreferences by hostViewModel.uiPreferences.collectAsState()
    val currentTitle = conversations.firstOrNull { it.id == currentConversationId }?.title
    val density = LocalDensity.current
    // 抽屉动画固定为统一的轻微弹性效果，不读取个性化里的消息动画选项。

    var drawerWidthPx by remember { mutableFloatStateOf(0f) }
    val drawerOffset = remember { Animatable(0f) }
    // 抽屉是否处于打开（或正在打开）状态。仅用于是否拦截外侧点击，
    // 位移与遮罩都在绘制阶段才读取，避免动画每帧重组整个聊天页
    var drawerOpen by remember { mutableStateOf(false) }
    // 抽屉底色：与抽屉本体同色，左侧补齐时才不会出现色差
    val drawerFillColor = MaterialTheme.colorScheme.surfaceContainerLow
    // 保留弹簧的回弹（会略微冲过 0），只把展开侧限制在抽屉宽度内，
    // 否则继续往右拖会让抽屉飞出屏幕。回弹溢出的那几像素在下面
    // 用抽屉同色补齐，因此不会露出聊天页背景
    fun drawerX(): Float = drawerOffset.value.coerceAtLeast(-drawerWidthPx)
    fun drawerProgress(): Float =
        if (drawerWidthPx > 0f) ((drawerX() + drawerWidthPx) / drawerWidthPx).coerceIn(0f, 1f) else 0f
    Box(
        Modifier
            .fillMaxSize()
            // 手势挂在最外层，并且在 Initial 通道读取原始位移。
            // 抽屉展开后会出现遮罩层，若沿用普通拖动检测会被子层的
            // 点击拦截吃掉后续事件，导致必须先松手才能左滑收起
            .pointerInput(drawerWidthPx) {
                val trigger = with(density) { 24.dp.toPx() }
                if (imeVisible) return@pointerInput
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    // 用目标态判断当前方向：自动动画还没走完也不会判错
                    var open = drawerWidthPx > 0f && drawerOffset.targetValue > -drawerWidthPx * 0.5f
                    var dragDistance = 0f
                    var totalX = 0f
                    var totalY = 0f
                    var lastX = down.position.x
                    var lastY = down.position.y
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dx = change.position.x - lastX
                        val dy = change.position.y - lastY
                        lastX = change.position.x
                        lastY = change.position.y
                        totalX += dx
                        totalY += dy
                        // 认定为横向拖动后消费事件，避免聊天页同时在滚动
                        if (abs(totalX) > slop && abs(totalX) > abs(totalY)) change.consume()
                        dragDistance += dx
                        // 只累计当前状态下“能触发切换”的方向，
                        // 反向的多余位移清零，展开与收起所需距离完全一致
                        dragDistance = if (open) dragDistance.coerceAtMost(0f) else dragDistance.coerceAtLeast(0f)
                        if (!open && dragDistance >= trigger) {
                            open = true
                            dragDistance = 0f
                            drawerOpen = true
                            scope.launch {
                                drawerOffset.animateTo(
                                    0f,
                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f)
                                )
                            }
                        } else if (open && dragDistance <= -trigger) {
                            open = false
                            dragDistance = 0f
                            scope.launch {
                                drawerOffset.animateTo(
                                    -drawerWidthPx,
                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f)
                                )
                                drawerOpen = false
                            }
                        }
                    }
                }
            }
    ) {

        ChatContent(
            title = currentTitle,
            modelLabel = "${settings.model} · $profileName",
            providerLabel = profileName,
            modelName = settings.model,
            searchEngine = settings.searchEngine,
            thinkingEffort = settings.thinkingEffort,
            onCycleThinkingEffort = {
                keyboardController?.hide()
                showEffortSheet = true
            },
            appearance = uiPreferences.chatAppearance,
            quickPrompts = uiPreferences.quickPrompts,
            shufflePrompts = uiPreferences.shufflePrompts,
            displayOptions = uiPreferences.messageDisplay,
            messages = messages,
            isLoading = isLoading,
            sendPhase = sendPhase,
            error = error,
            listState = listState,
            pressedState = pressedState,
            lastTouchAt = lastTouchAt,
            inputText = inputText,
            onInputChange = { inputText = it },
            onOpenDrawer = { drawerOpen = true; scope.launch { drawerOffset.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 560f)) } },
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
            onPickModel = {
                keyboardController?.hide()
                showModelPicker = true
            },
            onOpenBrowser = onOpenBrowser,
            undoable = undoable,
            onUndoRemoval = { viewModel.undoRemoval() },
            onDismissUndo = { viewModel.dismissUndo() },
            onLongPress = { message -> actionTarget = message },
            onPromptClick = { prompt -> inputText = prompt },
            onRetry = { messageId -> viewModel.retryMessage(messageId) },
            onClearError = { viewModel.clearError() }
        )
        // 只有抽屉打开时才建立遮罩交互层；关闭时不能覆盖聊天页面，
        // 否则会拦截菜单、输入框和消息点击。
        if (drawerOpen) Box(
            Modifier
                .fillMaxSize()
                // 在绘制阶段读取位移：每帧只重绘，不重组聊天页
                .drawBehind {
                    val progress = drawerProgress()
                    val xr = drawerX().roundToInt().toFloat()
                    if (progress > 0f) {
                        drawRect(Color.Black.copy(alpha = 0.32f * progress))
                    }
                    // 抽屉左侧圆角会缺出一小块，加上弹簧回弹露出的缝隙，
                    // 一起用抽屉同色填满，所以不会透出聊天页背景
                    val patchWidth = 40.dp.toPx()
                    val patchStart = if (progress > 0f) minOf(0f, xr) else 0f
                    val patchEnd = if (progress > 0f) maxOf(patchStart, minOf(size.width, xr + patchWidth)) else 0f
                    if (patchEnd > patchStart) {
                        drawRect(
                            color = drawerFillColor,
                            topLeft = Offset(patchStart, 0f),
                            size = Size(patchEnd - patchStart, size.height)
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        if (!drawerOpen) return@detectTapGestures
                        scope.launch {
                            drawerOffset.animateTo(-drawerWidthPx, tween(durationMillis = 150))
                            drawerOpen = false
                        }
                    })
                }
        )
            ModalDrawerSheet(
                drawerShape = AppShapeTokens.Drawer,
                // 与补齐色块用同一个颜色，圆角处不会出现色差
                drawerContainerColor = drawerFillColor,
                drawerTonalElevation = 0.dp,
                // 按屏宽取比例而非用 Material 默认的固定 360dp：后者在
                // 窄屏上几乎铺满，看不见背后的聊天页，也就失去了"临时
                // 拉出一层"的意味。留出的那一截同时是关闭抽屉的点击区
                modifier = Modifier
                    .onSizeChanged { size ->
                        if (drawerWidthPx == 0f) { drawerWidthPx = size.width.toFloat(); scope.launch { drawerOffset.snapTo(-drawerWidthPx) } }
                    }
                    .graphicsLayer {
                        // 每个选项只叠加一种明显效果；横向位移仍由抽屉手势本身负责
                        // 抽屉固定统一的轻微弹性，不受消息动画选项影响。
                    }
                    .offset { IntOffset(drawerX().roundToInt(), 0) }
                    .fillMaxWidth(DRAWER_WIDTH_FRACTION)
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
                        scope.launch { drawerOffset.animateTo(-drawerWidthPx, tween(220)); drawerOpen = false }
                    },
                    onNewConversation = {
                        hostViewModel.startNewConversation()
                        scope.launch { drawerOffset.animateTo(-drawerWidthPx, tween(220)); drawerOpen = false }
                    },
                    onDeleteConversation = { id ->
                        hostViewModel.deleteConversation(id)
                    },
                    onSettingsClick = {
                        scope.launch { drawerOffset.animateTo(-drawerWidthPx, tween(220)); drawerOpen = false }
                        onNavigateToSettings()
                    },
                    onModelClick = {
                        scope.launch { drawerOffset.animateTo(-drawerWidthPx, tween(220)); drawerOpen = false }
                        onNavigateToProvider()
                    }
                )
            }
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

    if (showModelPicker) {
        ModelPickerSheet(
            viewModel = apiProfilesViewModel,
            onDismiss = { showModelPicker = false },
            onManageProfiles = {
                showModelPicker = false
                onNavigateToProvider()
            }
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
    searchEngine: String,
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
    pressedState: androidx.compose.runtime.MutableState<Boolean>,
    lastTouchAt: androidx.compose.runtime.MutableState<Long>,
    inputText: String,
    onInputChange: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    /** 点输入栏左下角的模型区域 */
    onPickModel: () -> Unit,
    /** 打开应用内浏览器 */
    onOpenBrowser: (String) -> Unit,
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
            var inputBarHeight by remember { mutableStateOf(0.dp) }
            val density = LocalDensity.current
            // 增强透视时列表延伸到输入栏后面；关闭时由外层让位。
            val deep = appearance.inputBarDeepSeeThrough

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    // 监听手势放在滚动容器外层: LazyColumn 会消耗指针事件,
                    // 挂在它自身的 modifier 上不一定收得到抬起; 放在外层
                    // 并用 Initial 通道, 确保按下/抬起都能准确捕获。
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            lastTouchAt.value = System.currentTimeMillis()
                            pressedState.value = true
                            do {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                            } while (ev.changes.any { it.pressed })
                            lastTouchAt.value = System.currentTimeMillis()
                            pressedState.value = false
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            bottom = if (deep) 0.dp else inputBarHeight
                        )
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
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = if (deep) 12.dp + inputBarHeight else 12.dp
                        ),
                        // 间距比改版前放宽，助手消息取消容器后需要更多留白来分隔
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            MessageBlock(
                                message = message,
                                displayOptions = displayOptions,
                                appearance = appearance,
                                providerLabel = providerLabel,
                                searchEngine = searchEngine,
                                onRetry = { onRetry(message.id) },
                                onLongPress = { onLongPress(message) },
                                onLinkClick = onOpenBrowser
                            )
                        }
                        // 零占位的滚动锚点。animateScrollToItem(index) 是把
                        // index 对应项的"顶部"贴到视口顶——直接指最后一条
                        // 消息时, 流式增长的内容会把视口钉在消息开头, 新
                        // 增的文本在屏幕外。锚点垫在所有消息之后, 滚到它
                        // 才是真·列表底
                        // 锚点垫高: 滚到底时最后一条消息与视口底之间留出
                        // 与全局间距一致的呼吸空间, 视觉对齐在输入栏上方
                        // 而不是贴死在视口最底。
                        item(key = "bottom-anchor") {
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }

                }

                // 输入栏与撤回条一起叠在列表底部。
                //
                // 撤回条跟着输入栏走而非留在 Column 里：它的位置语义是
                // 「紧贴输入栏上方」，输入栏浮起来之后它也得跟上
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { size ->
                            // 量出实际高度折算成列表底部留白。输入栏高度
                            // 随内容变化（多行输入、状态行），写死会在
                            // 多行时挡住最后一条消息
                            inputBarHeight = with(density) { size.height.toDp() }
                        }
                ) {
                    // 错误提示也移到这一层。它原本在列表所在的 Column 末尾，
                    // 而输入栏现在叠在上面，留在原处会被压住
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
                    UndoBar(
                        removal = undoable,
                        onUndo = onUndoRemoval,
                        onDismiss = onDismissUndo
                    )
                    ChatInputBar(
                        
                        transparent = appearance.transparentInputBar,
                        seeThrough = appearance.inputBarSeeThrough,
                        floating = appearance.inputBarFloating && appearance.inputBarDeepSeeThrough,
                        deepSeeThrough = appearance.inputBarDeepSeeThrough,
                        onBodyHeightChange = { inputBarHeight = it },
                        
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
}

/**
 * 抽屉占屏幕宽度的比例。
 *
 * 0.82 留出约五分之一：足够看清背后是聊天页、也够点，同时抽屉本身
 * 仍有足够宽度容纳会话标题不至于频繁折行。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
/* v0.3.5-dev: 输入栏模型选择由全屏页改为底部半屏选择器 */
private fun ModelPickerSheet(
    viewModel: ApiProfilesViewModel,
    onDismiss: () -> Unit,
    onManageProfiles: () -> Unit
) {
    val store by viewModel.store.collectAsState()
    val activeId = store.active?.id

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) {
        // 键盘仍在显示时也强制打开到完整展开状态，避免只露出顶部一小段
        sheetState.expand()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Text(
                text = "选择模型",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onManageProfiles) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("管理配置", style = MaterialTheme.typography.labelMedium)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            store.profiles.forEach { profile ->
                if (profile.effectiveSelectedModels.isNotEmpty()) {
                    item(key = "header-${profile.id}") {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                    items(
                        items = profile.effectiveSelectedModels,
                        key = { "${profile.id}-$it" }
                    ) { model ->
                        val isSelected = profile.id == activeId && model == profile.effectiveModel
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(AppShapeTokens.SettingsGroup)
                                .clickable {
                                    viewModel.selectModelAndProfile(profile.id, model, onDismiss)
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            ModelIcon(modelName = model, size = 24.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = model,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "当前使用",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    item(key = "gap-${profile.id}") { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

private const val DRAWER_WIDTH_FRACTION = 0.82f
