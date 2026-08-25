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
import com.afyzfur.afyzhub.data.remote.provider.TokenUsage
import com.afyzfur.afyzhub.data.settings.AppSettings
import com.afyzfur.afyzhub.data.settings.SettingsProvider
import com.afyzfur.afyzhub.domain.model.Conversation
import com.afyzfur.afyzhub.domain.model.ConversationItem
import com.afyzfur.afyzhub.domain.model.Message
import com.afyzfur.afyzhub.domain.model.SendPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.afyzfur.afyzhub.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepositoryImpl(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val clientRegistry: ChatClientRegistry,
    private val settingsProvider: SettingsProvider
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
                    lastMessage = summary.lastMessage
                        ?.replace('\n', ' ')
                        ?.trim()
                        ?.take(SUMMARY_MAX_LENGTH)
                        ?.takeIf { it.isNotEmpty() }
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

            val latencyMs = System.currentTimeMillis() - startedAt
            val reply = outcome.content
            if (reply.isBlank()) {
                throw IllegalStateException("模型返回内容为空")
            }
            messageDao.updateStatus(userMessageId, Constants.STATUS_SUCCESS, null)

            val finalId = assistantId?.also {
                messageDao.finalizeAssistantMessage(
                    id = it,
                    content = reply,
                    status = Constants.STATUS_SUCCESS,
                    model = settings.model,
                    promptTokens = outcome.usage?.promptTokens,
                    completionTokens = outcome.usage?.completionTokens,
                    latencyMs = latencyMs
                )
            } ?: messageDao.insertMessage(
                MessageEntity(
                    conversationId = conversationId,
                    content = reply,
                    role = Constants.ROLE_ASSISTANT,
                    status = Constants.STATUS_SUCCESS,
                    model = settings.model,
                    promptTokens = outcome.usage?.promptTokens,
                    completionTokens = outcome.usage?.completionTokens,
                    latencyMs = latencyMs
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
                    promptTokens = outcome.usage?.promptTokens,
                    completionTokens = outcome.usage?.completionTokens,
                    latencyMs = latencyMs
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

    override suspend fun rollbackTo(messageId: Long) {
        val message = messageDao.getMessageById(messageId) ?: return
        messageDao.deleteFrom(
            conversationId = message.conversationId,
            createdAt = message.createdAt,
            id = messageId
        )
        touchConversation(message.conversationId)
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
        onPhase: (SendPhase) -> Unit
    ): CompletionResult {
        val builder = StringBuilder()
        var usage: TokenUsage? = null

        client.stream(turns, settings).collect { event ->
            when (event) {
                is StreamEvent.TextDelta -> {
                    // 首个片段到达即离开等待阶段。此后内容在陆续显现，
                    // 用户能直接看到进展，状态文字的作用就减弱了
                    if (builder.isEmpty()) onPhase(SendPhase.RECEIVING)
                    builder.append(event.delta)
                    messageDao.updateContent(placeholderId, builder.toString())
                }
                is StreamEvent.Finished -> usage = event.usage
            }
        }

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
        return usable
            .takeLast(Constants.MAX_CONTEXT_MESSAGES)
            .map { ChatTurn(role = it.role, content = it.content) }
    }

    private suspend fun touchConversation(conversationId: Long) {
        conversationDao.getConversationById(conversationId)?.let { entity ->
            conversationDao.updateConversation(
                entity.copy(updatedAt = System.currentTimeMillis())
            )
        }
    }

    override suspend fun renameConversation(conversationId: Long, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        conversationDao.getConversationById(conversationId)?.let { entity ->
            conversationDao.updateConversation(
                entity.copy(title = trimmed, updatedAt = System.currentTimeMillis())
            )
        }
    }

    override suspend fun deleteConversation(conversationId: Long) {
        conversationDao.getConversationById(conversationId)?.let {
            conversationDao.deleteConversation(it)
        }
    }

    private fun ConversationEntity.toDomain() = Conversation(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt
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
        latencyMs = latencyMs
    )

    private companion object {
        /** 抽屉摘要行的字符上限，足够填满一行且留有余量 */
        const val SUMMARY_MAX_LENGTH = 60
    }
}
