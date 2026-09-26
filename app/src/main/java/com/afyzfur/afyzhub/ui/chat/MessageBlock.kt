package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.layout.Arrangement
import coil.compose.SubcomposeAsyncImage
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.data.settings.AvatarMode
import com.afyzfur.afyzhub.data.settings.BubbleStyle
import com.afyzfur.afyzhub.data.settings.ChatAppearance
import com.afyzfur.afyzhub.data.settings.MessageDisplayOptions
import com.afyzfur.afyzhub.domain.model.Message
import com.afyzfur.afyzhub.domain.model.parseThinking
import com.afyzfur.afyzhub.domain.model.ContentBlock
import com.afyzfur.afyzhub.domain.model.parseContentBlocks
import com.afyzfur.afyzhub.domain.model.parseSearchQuery
import com.afyzfur.afyzhub.domain.model.parseSearchSources
import com.afyzfur.afyzhub.domain.model.stripSearchSources
import com.afyzfur.afyzhub.domain.model.stripSearchTag
import com.afyzfur.afyzhub.ui.components.LocalImage
import com.afyzfur.afyzhub.ui.components.ModelIcon
import com.afyzfur.afyzhub.ui.components.UserAvatar
import com.afyzfur.afyzhub.ui.components.MarkdownText
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens

/**
 * 单条消息。
 *
 * 气泡与否由 [ChatAppearance] 决定，用户与助手各自可选。
 * 无气泡时占满宽度，代码块与表格能完整展开；有气泡时宽度随内容
 * 并限制上限，避免长行贴满屏幕。
 *
 * 头像位在开启时占据固定宽度，两侧消息各自靠内对齐，
 * 使同一侧的消息主体保持竖直对齐而不因有无头像错开。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBlock(
    message: Message,
    displayOptions: MessageDisplayOptions,
    appearance: ChatAppearance,
    providerLabel: String,
    searchEngine: String = "bing",
    onRetry: () -> Unit = {},
    onLongPress: () -> Unit = {},
    /** 链接点击: 导航到应用内浏览器 */
    onLinkClick: ((String) -> Unit)? = null
) {
    val fromUser = message.isFromUser
    val style = if (fromUser) appearance.userBubble else appearance.assistantBubble

    Row(
        modifier = Modifier.fillMaxWidth(),
        // 用户消息整体靠右，助手靠左
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
        // 助手头像在消息左侧
        if (appearance.showAvatars && appearance.showAssistantAvatar && !fromUser) {
            MessageAvatar(
                appearance = appearance,
                fromUser = false,
                providerLabel = providerLabel,
                modelName = message.model
            )
            Spacer(Modifier.size(8.dp))
        }

        Column(
            horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
            // 一律用 weight 让正文占据剩余空间，而不是让它按内容取宽。
            //
            // 原先用户侧走 widthIn：Row 会先满足正文的宽度诉求，
            // 长消息把可用宽度吃光后头像只剩零宽，表现为一条竖线或干脆不见。
            // weight 使正文只拿"扣除头像后"的剩余部分。
            // 上限仍需要，否则短消息的气泡会被拉成整行宽
            modifier = Modifier
                .weight(1f, fill = false)
                .then(
                    if (style == BubbleStyle.PLAIN && !fromUser) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.widthIn(max = 320.dp)
                    }
                )
        ) {
            when {
                message.isFailed -> FailedMessage(
                    message = message,
                    onRetry = onRetry,
                    // 失败消息此前没接长按，导致发失败的消息既删不掉
                    // 也复制不了，只能一直留在会话里
                    onLongPress = onLongPress
                )
                else -> MessageBody(
                    message = message,
                    style = style,
                    fromUser = fromUser,
                    searchEngine = searchEngine,
                    onLongPress = onLongPress,
                    onLinkClick = onLinkClick
                )
            }

            // 失败消息已有错误提示与重试按钮，再加元信息只会更乱
            if (!message.isFailed && !message.isSending) {
                MessageMetaRow(
                    message = message,
                    options = displayOptions,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // 用户头像在消息右侧
        if (appearance.showAvatars && appearance.showUserAvatar && fromUser) {
            Spacer(Modifier.size(8.dp))
            MessageAvatar(
                appearance = appearance,
                fromUser = true,
                providerLabel = providerLabel,
                modelName = message.model
            )
        }
    }
}

/**
 * 消息正文。
 *
 * 用户消息按原样显示不解析 Markdown——用户输入的 `*` 之类符号
 * 通常是字面意思，解析反而会吞掉字符。
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MessageBody(
    message: Message,
    style: BubbleStyle,
    fromUser: Boolean,
    searchEngine: String,
    onLongPress: () -> Unit,
    onLinkClick: ((String) -> Unit)? = null
) {
    // 只有助手回复会带思考标签，用户消息不必解析。
    // remember 以内容为键：流式输出时每个增量都会重组，
    // 每次重跑正则在长回复上是可观的开销
    // 顺序化拆分: 一条回复里思考/搜索/正文可能交替出现多段
    // 单次解析派生全部视图数据: 旧实现 parseContentBlocks 跑了两遍 +
    // parseSearchSources 一遍, 流式期间每 50ms 重组一次全是重复正则扫描
    val parsed = remember(message.content, fromUser) {
        if (fromUser) null else parseContentBlocks(message.content)
    }
    val contentBlocks = parsed ?: emptyList()
    val answerText = remember(parsed, message.content, fromUser) {
        when {
            fromUser -> message.content
            parsed == null -> ""
            else -> parsed.filterIsInstance<ContentBlock.Answer>()
                .joinToString("\n\n") { it.text }.trim()
        }
    }
    val searchSources = remember(parsed, message.content, fromUser) {
        if (fromUser) emptyList() else parseSearchSources(message.content)
    }

    val content: @Composable () -> Unit = {
        if (fromUser) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = if (style == BubbleStyle.BUBBLE) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        } else {
            MarkdownText(
                // 用剥掉标签后的正文，否则 <think> 会原样显示
                text = answerText,
                color = if (style == BubbleStyle.BUBBLE) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                onLinkClick = onLinkClick,
                // 链接文本内部的 pointerInput 会消费指针事件,
                // 外层 combinedClickable 收不到长按, 故在这里一并传递
                onLongPress = onLongPress
            )
        }
    }

    // 长按挂在正文容器上而非整行：整行包含头像与空白区域，
    // 在那些位置长按弹菜单会显得没有指向性。
    //
    // 正文内部的 LinkAwareText 已自带长按处理(它的 pointerInput 先于
    // 本层收到事件, 且实测可用), 因此这里只保留 combinedClickable——
    // 涟漪反馈由它提供。此前额外叠加的 pointerInput 长按兜底会把按下
    // 事件先行消费, 正是"点击特效消失"的元凶, 已移除。
    // 显式传 indication 确保涟漪始终可见(不受主题/Surface 影响)
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val longPress = Modifier.combinedClickable(
        interactionSource = interactionSource,
        indication = androidx.compose.material3.ripple,
        // 单击不做事，但必须提供——combinedClickable 要求有 onClick。
        // 传空 lambda 的副作用是正文会有涟漪反馈，
        // 这反而提示了"这里可以按"
        onClick = {},
        onLongClick = onLongPress
    )

    // 思考/搜索块按出现顺序独立成栏, 与正式回答是并列关系。
    // 两次思考+一次搜索 = 三个条, 段落先后与模型实际行为一致
    contentBlocks.forEach { block ->
        when (block) {
            is ContentBlock.Think -> ReasoningBlock(
                reasoning = block.text,
                thinking = block.ongoing,
                bubbleStyle = style,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            is ContentBlock.Search -> SearchBlock(
                query = block.query,
                sources = searchSources,
                searchEngine = searchEngine,
                onLinkClick = onLinkClick,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            is ContentBlock.Answer -> {}
        }
    }

    // 思考进行中而正文尚未开始时不渲染气泡，否则会出现一个空容器。
    // 这正是截图里那条空白圆角块的来源
    //
    // 等待首 token 时（无思考、正文仍为空）同样不渲染：等 AI 开口
    // 之前界面上不该有任何占位框，进度由输入栏的阶段文字说明
    if (message.isSending && answerText.isBlank() && !fromUser) {
        return
    }

    when (style) {
        BubbleStyle.BUBBLE -> Surface(
            color = if (fromUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            // 两侧形状一致，来源方向由对齐与底色区分
            shape = if (fromUser) {
                AppShapeTokens.UserMessage
            } else {
                AppShapeTokens.AssistantMessage
            }
        ) {
            Box(
                // 高度动画: 流式逐字写入时高度每帧跳变是闪烁的主要来源,
                // 动画把跳变平滑为过渡(默认 spring 已足够快, 不拖沓)
                modifier = longPress
                    .animateContentSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                content()
            }
        }

        // 助手侧撑满宽度让代码块与表格完整展开；用户侧不撑满，
        // 否则容器占满整行后文本会从左边缘开始排，与靠右的意图相反
        BubbleStyle.PLAIN -> Box(
            // 同 BUBBLE: 平滑流式期间的高度变化
            modifier = if (fromUser) {
                longPress.animateContentSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .then(longPress)
            }
        ) {
            content()
        }
    }

}

/**
 * 头像。
 *
 * 自定义图片缺失时回落到内置图标而非留空——留空会让头像列出现空洞，
 * 破坏消息的竖直对齐。
 */
@Composable
private fun MessageAvatar(
    appearance: ChatAppearance,
    fromUser: Boolean,
    providerLabel: String,
    /** 该条消息使用的模型名，用于匹配厂商图标；v3 之前的消息为 null */
    modelName: String?
) {
    // 用户侧直接复用共用组件，与抽屉顶部保持一致
    if (fromUser) {
        UserAvatar(appearance = appearance, size = 32.dp)
        return
    }

    val path = appearance.assistantAvatarPath
    val useCustom = appearance.avatarMode == AvatarMode.CUSTOM && !path.isNullOrBlank()

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            // 同 UserAvatar：requiredSize 防止被正文挤压变形
            .requiredSize(32.dp)
            .clip(CircleShape)
    ) {
        if (useCustom) {
            LocalImage(
                path = path!!,
                version = appearance.imageVersion,
                contentDescription = null,
                // 与设置页缩略图用同一个值，两处看到的效果才一致
                blur = appearance.avatarBlur,
                modifier = Modifier.size(32.dp)
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // 优先用消息自身记录的模型名——历史消息可能来自
                    // 与当前配置不同的模型。缺失时退回提供商名，
                    // 两者都匹配不到时 ModelIcon 内部会显示首字母
                    ModelIcon(
                        modelName = modelName ?: providerLabel,
                        size = 24.dp
                    )
                }
            }
        }
    }
}

/**
 * 失败消息。
 *
 * 始终用容器承载：错误内容需要视觉上被隔离出来，
 * 无容器的错误文本容易被当成正常回复。
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FailedMessage(
    message: Message,
    onRetry: () -> Unit,
    onLongPress: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = AppShapeTokens.AssistantMessage,
        // clip 在 combinedClickable 之前，涟漪才会跟随气泡圆角
        modifier = Modifier
            .clip(AppShapeTokens.AssistantMessage)
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress
            )
    ) {
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }

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
}

/**
 * 联网搜索块: 展示这条回复触发的搜索查询。
 *
 * 与思考块同样的独立成栏处理——它是回复的附属信息,
 * 不是正文的一部分。样式刻意做得低调: 一行图标加文字,
 * 不与气泡争夺注意力
 */
/** 由页面 url 取站点 favicon 地址。直连站点自身最可靠： */
private fun faviconUrl(url: String): String {
    val host = url.removePrefix("https://").removePrefix("http://")
        .substringBefore('/')
    return if (host.isBlank()) "" else "https://$host/favicon.ico"
}

/**
 * 站点图标。三级降级：站点 favicon -> 首字母圆形占位。
 *
 * 不用 Google 的 favicon 服务：该域名在部分网络下不可达，
 * 请求超时后只剩空白。直连站点自身兼容性最好，失败时用
 * 首字母占位保证始终有可辨识的视觉锚点。
 */
@Composable
private fun SiteIcon(url: String, size: Dp) {
    val host = url.removePrefix("https://").removePrefix("http://")
        .substringBefore('/')
    val letter = host.removePrefix("www.").firstOrNull()?.uppercase() ?: "·"
    SubcomposeAsyncImage(
        model = faviconUrl(url),
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .clip(CircleShape),
        loading = {
            // 加载中给同尺寸的浅色圆点占位，避免行高跳动
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
        },
        error = {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    )
}

@Composable
private fun EngineBadge(engine: String) {
    val key = engine.trim().lowercase()
    val (label, color) = when {
        key == "baidu" || key == "百度" -> "度" to androidx.compose.ui.graphics.Color(0xFF2932E1)
        key == "google" -> "G" to androidx.compose.ui.graphics.Color(0xFF4285F4)
        else -> "B" to androidx.compose.ui.graphics.Color(0xFF00809D)
    }
    Box(Modifier.size(18.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White)
    }
}

@Composable
private fun SearchBlock(
    query: String,
    sources: List<Pair<String, String>>,
    searchEngine: String = "bing",
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null
) {
    // DeepSeek 风格: 收起一行(favicon+标题+查询词), 展开为来源列表。
    // 每条来源 = favicon + 标题, 点整行进内置浏览器; 不显示摘要
    var expanded by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = AppShapeTokens.SettingsGroup,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapeTokens.SettingsGroup)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // 图标跟随当前实际选择的搜索引擎。
                SiteIcon(
                    url = when (searchEngine.lowercase()) {
                        "baidu" -> "https://www.baidu.com"
                        "google" -> "https://www.google.com"
                        else -> "https://www.bing.com"
                    },
                    size = 16.dp
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = "已联网搜索",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = query,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.KeyboardArrowUp
                    } else {
                        Icons.Filled.KeyboardArrowDown
                    },
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            // 来源列表: favicon + 标题, 点整行进内置浏览器
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(bottom = 10.dp)) {
                    if (sources.isEmpty()) {
                        // streaming: sources not yet persisted
                        // 与下方来源行同宽的内边距, 否则文字贴左边缘
                        Text(
                            text = "正在获取搜索结果…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                    }
                    sources.forEach { (title, url) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(AppShapeTokens.SettingsGroup)
                                .clickable { onLinkClick?.invoke(url) }
                                .padding(horizontal = 14.dp, vertical = 5.dp)
                        ) {
                            // 站点图标: 直连 favicon, 失败退首字母
                            SiteIcon(url = url, size = 16.dp)
                            Spacer(Modifier.size(10.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}