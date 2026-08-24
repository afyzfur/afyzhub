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

    override suspend fun sendMessage(conversationId: Long, content: String): Result<Message> {
        val userMessageId = messageDao.insertMessage(
            MessageEntity(
                conversationId = conversationId,
                content = content,
                role = Constants.ROLE_USER,
                status = Constants.STATUS_SENDING
            )
        )
        return requestCompletion(conversationId, userMessageId)
    }

    override suspend fun retryMessage(messageId: Long): Result<Message> {
        val message = messageDao.getMessageById(messageId)
            ?: return Result.failure(IllegalStateException("消息不存在"))
        if (message.role != Constants.ROLE_USER) {
            return Result.failure(IllegalStateException("只能重试用户消息"))
        }
        messageDao.updateStatus(messageId, Constants.STATUS_SENDING, null)
        return requestCompletion(message.conversationId, messageId)
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
        userMessageId: Long
    ): Result<Message> {
        var assistantId: Long? = null
        return try {
            val settings = settingsProvider.current()
            if (settings.apiKey.isBlank()) {
                throw IllegalStateException("请先在设置中配置 API Key")
            }
            // 具体协议差异由对应 provider 的客户端处理，此处只关心对话内容。
            val client = clientRegistry.clientFor(settings.provider)
            val turns = buildContext(conversationId, userMessageId)

            // 耗时从发出请求前开始计，包含网络往返与模型生成
            val startedAt = System.currentTimeMillis()

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
                collectStream(client, turns, settings, placeholderId)
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
        } catch (e: Exception) {
            val reason = e.message ?: "发送失败"
            // 流式中断时删除半截的占位回复，避免留下无意义的残片。
            assistantId?.let { messageDao.deleteMessageById(it) }
            messageDao.updateStatus(userMessageId, Constants.STATUS_FAILED, reason)
            Result.failure(e)
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
        placeholderId: Long
    ): CompletionResult {
        val builder = StringBuilder()
        var usage: TokenUsage? = null

        client.stream(turns, settings).collect { event ->
            when (event) {
                is StreamEvent.TextDelta -> {
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
