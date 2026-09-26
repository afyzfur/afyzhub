package com.afyzfur.afyzhub.data.repository

import com.afyzfur.afyzhub.data.local.dao.ConversationDao
import com.afyzfur.afyzhub.data.local.dao.MessageDao
import com.afyzfur.afyzhub.data.local.entity.ConversationEntity
import com.afyzfur.afyzhub.data.local.entity.MessageEntity
import com.afyzfur.afyzhub.data.remote.provider.ChatClient
import com.afyzfur.afyzhub.data.remote.provider.ChatClientRegistry
import com.afyzfur.afyzhub.data.remote.provider.ChatTurn
import com.afyzfur.afyzhub.data.remote.provider.CompletionResult
import com.afyzfur.afyzhub.data.remote.provider.StreamEvent
import com.afyzfur.afyzhub.data.remote.provider.WebSearchService
import com.afyzfur.afyzhub.data.remote.provider.TokenUsage
import com.afyzfur.afyzhub.data.settings.AppSettings
import com.afyzfur.afyzhub.data.settings.SettingsProvider
import com.afyzfur.afyzhub.domain.model.AiProvider
import com.afyzfur.afyzhub.domain.model.Conversation
import com.afyzfur.afyzhub.domain.model.ConversationItem
import com.afyzfur.afyzhub.domain.model.Message
import com.afyzfur.afyzhub.domain.model.SendPhase
import com.afyzfur.afyzhub.domain.model.sanitizeHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.afyzfur.afyzhub.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val LT = "<"
private const val GT = ">"

/** 搜索来源块: 记录本次回复实际用到的搜索结果, UI 渲染为可展开列表 */
private const val SEARCH_SOURCES_OPEN = LT + "sources" + GT
private const val SEARCH_SOURCES_CLOSE = LT + "/sources" + GT

class ChatRepositoryImpl(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val clientRegistry: ChatClientRegistry,
    private val settingsProvider: SettingsProvider,
    private val webSearchService: WebSearchService
) : ChatRepository {

    override fun getAllConversations(): Flow<List<Conversation>> =
        conversationDao.getAllConversations().map { list -> list.map { it.toDomain() } }

    override fun getConversationItems(): Flow<List<ConversationItem>> =
        conversationDao.getConversationSummaries().map { list ->
            list.map { summary ->
                ConversationItem(
                    id = summary.id,
                    title = summary.title,
                    updatedAt = summary.updatedAt,
                    // 在此处截断而不是留给 UI：摘要只用于一行预览，
                    // 长文本传到 UI 层再截断没有意义，还会白占内存
                    summary = summary.summary,
                    lastMessage = summary.lastMessage
                        ?.replace('\n', ' ')
                        ?.trim()
                        ?.take(SUMMARY_MAX_LENGTH)
                        ?.takeIf { it.isNotEmpty() },
                    pinned = summary.pinned,
                    starred = summary.starred,
                    note = summary.note,
                    group = summary.group
                )
            }
        }

    override fun getMessagesByConversationId(conversationId: Long): Flow<List<Message>> =
        messageDao.getMessagesByConversationId(conversationId).map { list -> list.map { it.toDomain() } }

    override suspend fun createConversation(title: String): Long =
        conversationDao.insertConversation(ConversationEntity(title = title))

    override suspend fun sendMessage(
        conversationId: Long,
        content: String,
        onPhase: (SendPhase) -> Unit
    ): Result<Message> {
        val userMessageId = messageDao.insertMessage(
            MessageEntity(
                conversationId = conversationId,
                content = content,
                role = Constants.ROLE_USER,
                status = Constants.STATUS_SENDING
            )
        )
        // 兜底：插库返回后到 requestCompletion 的 try 之间若被取消，
        // 这条消息就没人收尾，会永久停在「发送中」。窗口很窄但确实存在
        try {
            return requestCompletion(conversationId, userMessageId, onPhase)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                messageDao.updateStatus(userMessageId, Constants.STATUS_SUCCESS, null)
            }
            throw e
        }
    }

    override suspend fun retryMessage(
        messageId: Long,
        onPhase: (SendPhase) -> Unit
    ): Result<Message> {
        val message = messageDao.getMessageById(messageId)
            ?: return Result.failure(IllegalStateException("消息不存在"))
        if (message.role != Constants.ROLE_USER) {
            return Result.failure(IllegalStateException("只能重试用户消息"))
        }
        messageDao.updateStatus(messageId, Constants.STATUS_SENDING, null)
        return requestCompletion(message.conversationId, messageId, onPhase)
    }

    /**
     * 请求模型回复。
     *
     * 根据设置选择流式或一次性返回。无论走哪条路径，成功后
     * [userMessageId] 标记为 success，失败则标记 failed 并记录原因，
     * 界面据此提供重试入口。
     */
    private suspend fun requestCompletion(
        conversationId: Long,
        userMessageId: Long,
        onPhase: (SendPhase) -> Unit
    ): Result<Message> {
        var assistantId: Long? = null
        return try {
            onPhase(SendPhase.CONNECTING)
            val settings = settingsProvider.current()
            if (settings.apiKey.isBlank()) {
                throw IllegalStateException("请先在设置中配置 API Key")
            }
            // 具体协议差异由对应 provider 的客户端处理，此处只关心对话内容。
            val client = clientRegistry.clientFor(settings.provider)
            val turns = buildContext(conversationId, userMessageId)

            // 耗时从发出请求前开始计，包含网络往返与模型生成
            val startedAt = System.currentTimeMillis()
            onPhase(SendPhase.WAITING)

            val outcome = if (settings.streamEnabled) {
                // 先占位再逐段填充，界面即可实时看到增量文本。
                val placeholderId = messageDao.insertMessage(
                    MessageEntity(
                        conversationId = conversationId,
                        content = "",
                        role = Constants.ROLE_ASSISTANT,
                        status = Constants.STATUS_SENDING
                    )
                )
                assistantId = placeholderId
                collectStream(client, turns, settings, placeholderId, onPhase)
            } else {
                client.complete(turns, settings)
            }

            var reply = outcome.content
            if (reply.isBlank()) {
                throw IllegalStateException("模型返回内容为空")
            }

            // 应用层联网搜索：模型请求了搜索则执行, 把结果作为
            // 补充上下文再次请求, 用新回复替换。Gemini 走原生
            // grounding, 不会输出搜索标签, 此块自然跳过
            var searchUsage = outcome.usage
            var searchQuery = WebSearchService.extractQuery(reply)
            println("[AfyzSearch] " + "extractQuery=" + searchQuery + " replyLen=" + reply.length)
            // 查询词补全: 模型常把问题缩写(「重庆的天气」→「重庆市」)。
            // 若查询词是用户问题的子串且明显更短, 直接用用户问题搜索,
            // 一次搜索覆盖完整需求。
            if (searchQuery != null) {
                val userQ = turns.lastOrNull { it.role == "user" }?.content?.trim()
                if (userQ != null && userQ.length > searchQuery.length &&
                    userQ.contains(searchQuery)) {
                    println("[AfyzSearch] query expanded: " + searchQuery + " -> " + userQ)
                    searchQuery = userQ
                }
            }
            if (searchQuery != null &&
                settings.webSearchEnabled &&
                settings.inAppBrowserEnabled &&
                settings.provider != AiProvider.GEMINI
            ) {
                onPhase(SendPhase.SEARCHING)
                val results = webSearchService.search(searchQuery, engineId = settings.searchEngine)
                println("[AfyzSearch] " + "searchDone size=" + results.size + " q=" + searchQuery)
                // 结果已拿到，进入读取整理阶段；结果为空时直接跳过
                // BROWSING，让模型按无结果路径兜底回答
                if (results.isNotEmpty()) onPhase(SendPhase.BROWSING)
                // 搜索到的页面以 sources 块附在回复后: UI 解析渲染
                // 为可展开的来源列表, 二轮请求里模型也能看到自己"查过什么"
                val sourcesBlock = if (results.isEmpty()) "" else "\n" +
                    SEARCH_SOURCES_OPEN + "\n" +
                    results.joinToString("\n") { it.title.replace('\n', ' ') + " :: " + it.url } +
                    "\n" + SEARCH_SOURCES_CLOSE
                // 用户原始问题: 搜索查询词可能比问题窄(模型缩写), 回答必须
                // 覆盖完整需求。从上下文取最后一条 user 轮, retry 路径同样有效。
                val originalQuestion = turns.lastOrNull { it.role == "user" }?.content ?: searchQuery
                                val searchedTurns = turns + listOf(
                    // 第二轮请求的 assistant 历史: 保留第一轮原始内容(含搜索
                    // 标签, 代表"我已发起这次搜索"), 并在末尾追加占位说明,
                    // 让模型明确知道搜索已执行、现在该基于结果回答。
                    // 不能剥标签: 剥掉后查询词裸露成正文, 模型会以为上次
                    // 没搜成而在二轮再发一次搜索(重复搜索的根源)。
                    ChatTurn(
                        role = "assistant",
                        content = reply + "\n（以上为我本轮已输出的内容：已发起网络搜索并停止输出，等待系统返回搜索结果后继续回答。）" + sourcesBlock
                    ),

                    ChatTurn(
                        role = "user",
                        // 带上用户原始问题: 搜索查询词可能比问题窄(模型缩写),
                        // 回答必须覆盖完整需求而不只是查询词。
                        // 用户问题从上下文取(最后一条 user 轮), retry 路径同样有效。
                        content = "以下是「" + searchQuery + "」的搜索结果：\n\n" +
                            WebSearchService.formatResults(results) +
                            "\n\n用户的原始问题是：「" + originalQuestion + "」。" +
                            "请结合搜索结果与原始问题，用中文直接给出完整的正文回答；" +
                            "搜索结果可能只覆盖了问题的一部分，不足的部分可用你的知识补充。" +
                            "不要罗列链接、标题或网址列表（来源已由界面单独展示）；" +
                            "也不要再输出任何搜索标签，搜索已完成。"
                    )
                )
                val secondOutcome = if (settings.streamEnabled) {
                    collectStream(client, searchedTurns, settings, assistantId!!, onPhase, reply)
                } else {
                    client.complete(searchedTurns, settings)
                }
                searchUsage = secondOutcome.usage ?: searchUsage
                // 模型有时复读上下文里的搜索/来源标签: 复读的闭合 sources 会把
                // 整段正文当来源剥掉(表现为回答输出完突然消失)。这里在拼接
                // 官方 sources 块之前先清掉模型自己输出的这类标签
                // 第二轮流式起点是第一轮内容(initialContent=reply), 所以:
                // - 流式时从 reply 之后截出的才是第二轮自己的输出; 非流式时
                //   complete 返回的就是纯第二轮内容。复读的 web_search 标签
                //   全部剥掉——它们的查询从未被真正搜索, 留着会多出一个永远
                //   “正在获取…”的搜索块(即“第二次搜索不到”)。
                val secondRoundPart = if (settings.streamEnabled && secondOutcome.content.length > reply.length) {
                    secondOutcome.content.substring(reply.length)
                } else {
                    secondOutcome.content
                }
                var cleanedSecond = WebSearchService.stripBareUrlLines(
                    WebSearchService.stripSearchTagsOnly(
                        WebSearchService.stripModelEchoTags(secondRoundPart)
                    )
                )
                // 模型有时会把二轮上下文里的占位句复读进正文,
                // 表现为正文开头出现一句奇怪的括号说明。剥掉它。
                cleanedSecond = cleanedSecond.replace(
                    "（以上为我本轮已输出的内容：已发起网络搜索并停止输出，等待系统返回搜索结果后继续回答。）", ""
                )
                // 第一轮内容截到第一个搜索标签为止: 模型在标签后继续输出
                // 的内容是搜索前草稿, 和第二轮回答重复(“回答两次”的真凶)。
                reply = WebSearchService.truncateAfterFirstSearchTag(reply) + cleanedSecond + sourcesBlock
                println("[AfyzSearch] " + "finalized replyLen=" + reply.length + " hasTag=" + reply.contains("<web_search>"))
                if (secondOutcome.content.isBlank()) {
                    throw IllegalStateException("模型返回内容为空")
                }
            }
            val latencyMs = System.currentTimeMillis() - startedAt
            messageDao.updateStatus(userMessageId, Constants.STATUS_SUCCESS, null)

            val finalId = assistantId?.also {
                messageDao.finalizeAssistantMessage(
                    id = it,
                    content = reply,
                    status = Constants.STATUS_SUCCESS,
                    model = settings.model,
                    promptTokens = searchUsage?.promptTokens,
                    completionTokens = searchUsage?.completionTokens,
                    latencyMs = latencyMs,
                    cachedTokens = searchUsage?.cachedTokens
                )
            } ?: messageDao.insertMessage(
                MessageEntity(
                    conversationId = conversationId,
                    content = reply,
                    role = Constants.ROLE_ASSISTANT,
                    status = Constants.STATUS_SUCCESS,
                    model = settings.model,
                    promptTokens = searchUsage?.promptTokens,
                    completionTokens = searchUsage?.completionTokens,
                    latencyMs = latencyMs,
                    cachedTokens = searchUsage?.cachedTokens
                )
            )
            touchConversation(conversationId)
            Result.success(
                Message(
                    id = finalId,
                    conversationId = conversationId,
                    content = reply,
                    role = Constants.ROLE_ASSISTANT,
                    createdAt = System.currentTimeMillis(),
                    model = settings.model,
                    promptTokens = searchUsage?.promptTokens,
                    completionTokens = searchUsage?.completionTokens,
                    latencyMs = latencyMs,
                    cachedTokens = searchUsage?.cachedTokens
                )
            )
        } catch (e: CancellationException) {
            // 用户主动暂停。与失败不同，已生成的内容要保留——
            // 暂停的本意是"到此为止"，不是"作废重来"。
            //
            // 收尾操作必须放在 NonCancellable 里：当前协程已进入取消状态，
            // 任何挂起的数据库写入会立刻再次抛出取消异常，状态就落不了盘，
            // 消息会永久停在"发送中"。
            withContext(NonCancellable) {
                finalizeCancelled(conversationId, userMessageId, assistantId)
            }
            // 继续向上抛：取消异常不该被吞掉，否则协程框架无法
            // 正确结束这条协程链
            throw e
        } catch (e: Exception) {
            val reason = e.message ?: "发送失败"
            // 流式中断时删除半截的占位回复，避免留下无意义的残片。
            assistantId?.let { messageDao.deleteMessageById(it) }
            messageDao.updateStatus(userMessageId, Constants.STATUS_FAILED, reason)
            Result.failure(e)
        }
    }

    override suspend fun deleteMessage(messageId: Long) {
        messageDao.deleteMessageById(messageId)
    }

    override suspend fun rollbackTo(messageId: Long): List<Message> {
        val message = messageDao.getMessageById(messageId) ?: return emptyList()
        // 先取快照再删。顺序反过来就没得可查了，而快照是撤回的唯一依据
        val removed = messageDao.getMessagesFrom(
            conversationId = message.conversationId,
            createdAt = message.createdAt,
            id = messageId
        ).map { it.toDomain() }
        messageDao.deleteFrom(
            conversationId = message.conversationId,
            createdAt = message.createdAt,
            id = messageId
        )
        touchConversation(message.conversationId)
        return removed
    }

    override suspend fun restoreMessages(messages: List<Message>) {
        if (messages.isEmpty()) return
        // 带原 id 插回，DAO 的冲突策略是替换，因此顺序与元信息都不变
        messages.forEach { messageDao.insertMessage(it.toEntity()) }
        touchConversation(messages.first().conversationId)
    }

    override suspend fun regenerate(
        assistantMessageId: Long,
        onPhase: (SendPhase) -> Unit
    ): Result<Message> {
        val assistant = messageDao.getMessageById(assistantMessageId)
            ?: return Result.failure(IllegalStateException("消息不存在"))
        if (assistant.role != Constants.ROLE_ASSISTANT) {
            return Result.failure(IllegalStateException("只能重新生成助手回复"))
        }

        // 找到这条回复对应的用户提问：同会话中排在它之前的最后一条用户消息
        val history = messageDao.getMessagesOnce(assistant.conversationId)
        val userMessage = history
            .filter { it.role == Constants.ROLE_USER }
            .lastOrNull {
                it.createdAt < assistant.createdAt ||
                    (it.createdAt == assistant.createdAt && it.id < assistant.id)
            }
            ?: return Result.failure(IllegalStateException("找不到对应的提问"))

        // 先删旧回复，否则它会进入新请求的上下文，
        // 模型会看到自己刚说过的话
        messageDao.deleteMessageById(assistantMessageId)
        return requestCompletion(assistant.conversationId, userMessage.id, onPhase)
    }

    override suspend fun settleInterrupted(conversationId: Long) {
        // 先删空占位再归位：顺序反了的话空消息已不是 SENDING，
        // 删除条件就匹配不到，会留下一条空白气泡
        val deleted = messageDao.deleteEmptyMessagesByStatus(
            conversationId,
            Constants.STATUS_SENDING
        )
        val settled = messageDao.settlePendingMessages(
            conversationId = conversationId,
            fromStatus = Constants.STATUS_SENDING,
            toStatus = Constants.STATUS_SUCCESS
        )

        // 只在确实改动过数据时更新时间戳。
        //
        // 无条件 touch 是个严重问题：这个方法在每次打开会话时都会调用，
        // 于是所有会话的 updatedAt 都被刷成当前时间，
        // 抽屉里的时间分组全部塌成「今天」
        if (deleted > 0 || settled > 0) {
            touchConversation(conversationId)
        }
    }

    /**
     * 暂停后的收尾。
     *
     * 用户消息标记成功——它确实发出去了。助手回复若已有内容则保留并
     * 标记成功，使其能进入后续对话的上下文；若一个字都没收到就删掉占位，
     * 留一条空消息没有意义。
     */
    private suspend fun finalizeCancelled(
        conversationId: Long,
        userMessageId: Long,
        assistantId: Long?
    ) {
        messageDao.updateStatus(userMessageId, Constants.STATUS_SUCCESS, null)

        if (assistantId == null) {
            return
        }
        val partial = messageDao.getMessageById(assistantId)?.content.orEmpty()
        if (partial.isBlank()) {
            messageDao.deleteMessageById(assistantId)
        } else {
            messageDao.updateStatus(assistantId, Constants.STATUS_SUCCESS, null)
            touchConversation(conversationId)
        }
    }

    /**
     * 消费流式增量，边写库边累积完整文本。
     *
     * usage 只出现在流末尾的 Finished 事件里，且部分提供商不返回，
     * 因此返回值与非流式共用 [CompletionResult]，usage 可空。
     */
    private suspend fun collectStream(
        client: ChatClient,
        turns: List<ChatTurn>,
        settings: AppSettings,
        placeholderId: Long,
        onPhase: (SendPhase) -> Unit,
        initialContent: String = ""
    ): CompletionResult {
        // 搜索后的二次请求以第一轮内容为底追加: 思考与搜索
        // 标签都是这条回复的一部分, 覆盖式重置会全部丢失
        val builder = StringBuilder(initialContent)
        var usage: TokenUsage? = null
        var receivedThisRound = false
        // 平滑显示: 揭示进度与网络到达节奏解耦。网络是突发式投递的,
        // 若直接把缓冲区写库, 会出现“几百毫秒无变化→突然蹦一大段”的一顿一顿。
        // 改为逐字揭示: 每 40ms 把“已揭示”部分落库,
        // 步长按积压量自适应推进——API 一次给一大段也逐字显现,
        // API 慢时原样透传, 观感始终丝滑。
        var lastWritten = ""
        val revealLock = Any()
        var networkDone = false
        var revealed = initialContent.length
        val smoother = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(60)
                val snapshot: String
                val finished: Boolean
                synchronized(revealLock) {
                    val full = builder.toString()
                    if (revealed < full.length) {
                        val backlog = full.length - revealed
                        // DeepSeek 风格逐字渐显: 每 60ms 小步进固定字数,
                        // 步长随积压自适应但幅度小(backlog/20), 视觉上是
                        // 连续的小步进而非大段跳变; 积压大时按比例加速
                        // 追平, 不会无限落后
                        revealed += maxOf(2, (backlog / 20).coerceAtLeast(1))
                        if (revealed > full.length) revealed = full.length
                    }
                    // 半标签防护: 截断点若落在协议标签的中间, 库里会短暂
                    // 出现 "<thi…" 这类残段, UI 解析不稳定造成闪烁。
                    // 遇到不完整标签时把揭示点回退到标签开始之前。
                    var cut = minOf(revealed, full.length)
                    if (cut > 0 && cut < full.length) {
                        val scanStart = maxOf(0, cut - 14)
                        val tail = full.substring(scanStart, cut)
                        for (tag in listOf("<think>", "</think>", "<web_search>", "</web_search>", "<sources>", "</sources>")) {
                            var k = maxOf(0, tail.length - tag.length + 1)
                            while (k < tail.length) {
                                val cand = tail.substring(k)
                                if (cand.length < tag.length && tag.startsWith(cand)) {
                                    cut = scanStart + k
                                    k = tail.length
                                } else {
                                    k++
                                }
                            }
                        }
                    }
                    snapshot = full.substring(0, cut)
                    finished = networkDone && revealed >= builder.length
                }
                if (snapshot != lastWritten) {
                    messageDao.updateContent(placeholderId, snapshot)
                    lastWritten = snapshot
                }
                if (finished) break
            }
        }
        try {
            client.stream(turns, settings).collect { event ->
                when (event) {
                    is StreamEvent.TextDelta -> {
                        if (!receivedThisRound) {
                            receivedThisRound = true
                            onPhase(SendPhase.RECEIVING)
                        }
                        synchronized(revealLock) { builder.append(event.delta) }
                    }
                    is StreamEvent.Finished -> {
                        usage = event.usage
                    }
                }
            }
        } finally {
            // 无论成功或异常, 都结束平滑器, 避免漂浪下去
            synchronized(revealLock) { networkDone = true }
            smoother.join()
        }
        messageDao.updateContent(placeholderId, builder.toString())
        return CompletionResult(content = builder.toString(), usage = usage)
    }

    /**
     * 组装多轮上下文。
     *
     * 只取成功的历史消息，外加本次待发送的用户消息，并按
     * [Constants.MAX_CONTEXT_MESSAGES] 保留最近若干条以控制 token 消耗。
     */
    private suspend fun buildContext(
        conversationId: Long,
        currentMessageId: Long
    ): List<ChatTurn> {
        val history = messageDao.getMessagesOnce(conversationId)
        val usable = history.filter {
            it.id == currentMessageId || it.status == Constants.STATUS_SUCCESS
        }
        val turns = usable
            .takeLast(Constants.MAX_CONTEXT_MESSAGES)
            .map {
                // 助手历史里的协议标签(web_search/sources)只服务 UI 渲染,
                // 原样传回会诱导模型复读: 上一轮的搜索标签会让模型在
                // 新一轮里模仿着再输出标签, 造成重复搜索/重复回答。
                // 深度清洗: web_search 标签连查询词替换为 [搜索: x],
                // sources 块连同标题::链接整体替换为 [已附搜索来源]。
                // 若只剥标签, 查询词裸露、来源明文残留, 模型会认为
                // 上一轮已有搜索结果, 新一轮不再发搜索标签(搜不到)。
                val c = if (it.role == Constants.ROLE_ASSISTANT) {
                    sanitizeHistory(it.content)
                } else {
                    it.content
                }
                ChatTurn(role = it.role, content = c)
            }

        // 系统提示词注入在对话最前：约束是"这个助手是什么样"，
        // 属于所有轮次的前置条件，放在历史消息之后会失去效力
        val settings = settingsProvider.current()
        val systemPrompt = settings.systemPrompt.trim()
        // 联网搜索指令：仅当开关开启且提供商无原生搜索时注入。
        // Gemini 的服务端 grounding 质量更高，保持原生路径
        val searchInstruction = if (
            settings.webSearchEnabled &&
            settings.inAppBrowserEnabled &&
            settings.provider != AiProvider.GEMINI
        ) {
            WebSearchService.instruction()
        } else null

        return buildList {
            if (systemPrompt.isNotEmpty()) {
                add(ChatTurn(role = "system", content = systemPrompt))
            }
            if (searchInstruction != null) {
                add(ChatTurn(role = "system", content = searchInstruction))
            }
            addAll(turns)
        }
    }

    private suspend fun touchConversation(conversationId: Long) {
        conversationDao.getConversationById(conversationId)?.let { entity ->
            conversationDao.updateConversation(
                entity.copy(updatedAt = System.currentTimeMillis())
            )
        }
    }

    override suspend fun updateSummary(conversationId: Long, summary: String) {
        val trimmed = summary.trim()
        if (trimmed.isEmpty()) return
        conversationDao.updateSummary(conversationId, trimmed)
    }

    override suspend fun getConversationSummary(conversationId: Long): String? =
        conversationDao.getConversationById(conversationId)?.summary?.takeIf { it.isNotBlank() }

    override suspend fun renameConversation(conversationId: Long, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        // 不刷新 updatedAt：改名不是"对话有了新进展"，此前的实现会把
        // 会话挪进抽屉的「今天」分组，改个错别字就让它跳位
        conversationDao.updateTitle(conversationId, trimmed)
    }

    override suspend fun setPinned(conversationId: Long, pinned: Boolean) {
        conversationDao.updatePinned(conversationId, pinned)
    }

    override suspend fun setStarred(conversationId: Long, starred: Boolean) {
        conversationDao.updateStarred(conversationId, starred)
    }

    override suspend fun updateNote(conversationId: Long, note: String?) {
        // 空串归一成 null，界面判空只需要认一种形式
        conversationDao.updateNote(conversationId, note?.trim()?.ifBlank { null })
    }

    override suspend fun updateGroup(conversationId: Long, group: String) {
        conversationDao.updateGroup(conversationId, group.trim())
    }

    override fun observeGroups(): Flow<List<String>> = conversationDao.getGroups()

    override suspend fun deleteConversation(conversationId: Long) {
        // 直接按 id 删，省掉一次先查后删。消息由外键级联清理
        conversationDao.deleteConversationById(conversationId)
    }

    private fun ConversationEntity.toDomain() = Conversation(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    /** 撤回删除时用：把领域模型还原成实体，保留原 id 以精确插回。 */
    private fun Message.toEntity() = MessageEntity(
        id = id,
        conversationId = conversationId,
        content = content,
        role = role,
        status = status,
        errorMessage = errorMessage,
        createdAt = createdAt,
        model = model,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        latencyMs = latencyMs
    )

    private fun MessageEntity.toDomain() = Message(
        id = id,
        conversationId = conversationId,
        content = content,
        role = role,
        status = status,
        errorMessage = errorMessage,
        createdAt = createdAt,
        model = model,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        latencyMs = latencyMs,
        cachedTokens = cachedTokens
    )

    private companion object {
        private const val BATCH_UPDATE_WINDOW_MS = 50L
        /** 抽屉摘要行的字符上限，足够填满一行且留有余量 */
        const val SUMMARY_MAX_LENGTH = 60
    }
}
