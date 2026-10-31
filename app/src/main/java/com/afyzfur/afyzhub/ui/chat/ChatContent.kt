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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import com.afyzfur.afyzhub.ui.components.ModelIcon
import com.afyzfur.afyzhub.ui.settings.ApiProfilesViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.animation.core.animate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.geometry.Rect
import org.koin.androidx.compose.koinViewModel

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
internal fun ChatContent(
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
    focusRequestToken: Int,
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
            // Scaffold/TopAppBar stay stable during IME animation. The content region below
            // consumes animated IME insets directly; no second tween or delayed height state.
            modifier = Modifier.fillMaxSize(),
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
                            Icon(Icons.Default.Menu, contentDescription = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_0bb4ba2d2e46))
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
                    // Read system IME insets in the layout phase, on the SAME container
                    // that anchors the input bar and message viewport.
                    .imePadding()
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
                                    Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_6c14bd7f6f9e))
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
                         focusRequestToken = focusRequestToken,

                        
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

