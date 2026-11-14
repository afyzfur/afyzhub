package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.data.local.entity.ConversationEntity
import com.afyzfur.afyzhub.data.remote.provider.ChatClient
import com.afyzfur.afyzhub.data.remote.provider.ChatClientRegistry
import com.afyzfur.afyzhub.data.remote.provider.ChatTurn
import com.afyzfur.afyzhub.data.remote.provider.CompletionResult
import com.afyzfur.afyzhub.data.remote.provider.StreamEvent
import com.afyzfur.afyzhub.data.remote.provider.TokenUsage
import com.afyzfur.afyzhub.data.repository.ChatRepositoryImpl
import com.afyzfur.afyzhub.data.settings.AppSettings
import com.afyzfur.afyzhub.data.settings.SettingsProvider
import com.afyzfur.afyzhub.domain.model.AiProvider
import com.afyzfur.afyzhub.util.Constants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * 记录调用参数的假客户端。
 *
 * 同时覆盖一次性与流式两条路径，便于断言 Repository 走了哪一条。
 */
private class RecordingClient(
    private val reply: String? = "好的",
    private val error: Exception? = null,
    private val chunks: List<String> = emptyList(),
    private val failAfter: Int? = null,
    /** 可选的 token 用量，默认不返回以模拟不支持 usage 的服务 */
    private val usage: TokenUsage? = null
) : ChatClient {

    var completeTurns: List<ChatTurn>? = null
    var streamTurns: List<ChatTurn>? = null
    var usedSettings: AppSettings? = null

    override suspend fun complete(
        turns: List<ChatTurn>,
        settings: AppSettings
    ): CompletionResult {
        completeTurns = turns
        usedSettings = settings
        error?.let { throw it }
        return CompletionResult(content = reply.orEmpty(), usage = usage)
    }

    override fun stream(turns: List<ChatTurn>, settings: AppSettings): Flow<StreamEvent> {
        streamTurns = turns
        usedSettings = settings
        return flow {
            chunks.forEachIndexed { index, chunk ->
                if (failAfter != null && index == failAfter) {
                    throw IOException("连接中断")
                }
                emit(StreamEvent.TextDelta(chunk))
            }
            // 中断路径在上面就抛了，走到这里说明流正常结束
            emit(StreamEvent.Finished(usage))
        }
    }

    override suspend fun listModels(settings: AppSettings): List<String> = emptyList()
}

private class FixedSettings(private val settings: AppSettings) : SettingsProvider {
    override suspend fun current(): AppSettings = settings
}

class ChatRepositoryImplTest {

    private lateinit var messageDao: FakeMessageDao
    private lateinit var conversationDao: FakeConversationDao

    @Before
    fun setUp() {
        messageDao = FakeMessageDao()
        conversationDao = FakeConversationDao()
    }

    private suspend fun newConversation(): Long =
        conversationDao.insertConversation(ConversationEntity(title = "新对话"))

    /** 搜索服务的假传输：单测不联网，返回空页让搜索结果为空 */
    private object NoopTransport : com.afyzfur.afyzhub.data.remote.provider.Transport {
        override suspend fun getForText(
            baseUrl: String,
            path: String,
            headers: Map<String, String>,
            query: Map<String, String>,
            logContext: com.afyzfur.afyzhub.data.log.RequestLogContext
        ): String = ""

        override suspend fun postForText(
            baseUrl: String,
            path: String,
            headers: Map<String, String>,
            body: String,
            query: Map<String, String>,
            logContext: com.afyzfur.afyzhub.data.log.RequestLogContext
        ): String = ""

        override fun postForSse(
            baseUrl: String,
            path: String,
            headers: Map<String, String>,
            body: String,
            query: Map<String, String>,
            logContext: com.afyzfur.afyzhub.data.log.RequestLogContext
        ): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.flowOf()
    }

    /** 默认关闭流式，让既有断言仍走一次性返回路径。 */
    private fun repository(
        client: ChatClient,
        settings: AppSettings = AppSettings(
            apiKey = "sk-test",
            model = "gpt-4o",
            streamEnabled = false
        )
    ) = ChatRepositoryImpl(
        conversationDao,
        messageDao,
        ChatClientRegistry(mapOf(settings.provider to client)),
        FixedSettings(settings),
        com.afyzfur.afyzhub.data.remote.provider.WebSearchService(NoopTransport)
    )

    @Test
    fun `请求携带完整历史上下文`() = runTest {
        val client = RecordingClient()
        val repo = repository(client)
        val conversationId = newConversation()

        repo.sendMessage(conversationId, "第一个问题")
        repo.sendMessage(conversationId, "第二个问题")

        val sent = client.completeTurns
        assertNotNull(sent)
        // 用户1、助手1、用户2 共三条，证明多轮上下文已带上。
        val sentTurns = checkNotNull(sent)
        assertEquals(3, sentTurns.size)
        assertEquals("第一个问题", sentTurns[0].content)
        assertEquals(Constants.ROLE_ASSISTANT, sentTurns[1].role)
        assertEquals("第二个问题", sentTurns[2].content)
    }

    @Test
    fun `请求使用设置中选择的模型`() = runTest {
        val client = RecordingClient()
        val repo = repository(
            client,
            AppSettings(apiKey = "sk-test", model = "my-custom-model", streamEnabled = false)
        )

        repo.sendMessage(newConversation(), "你好")

        assertEquals("my-custom-model", client.usedSettings?.model)
    }

    @Test
    fun `按当前提供商选择对应客户端`() = runTest {
        val openai = RecordingClient(reply = "来自 OpenAI")
        val claude = RecordingClient(reply = "来自 Claude")
        val settings = AppSettings(
            provider = AiProvider.ANTHROPIC,
            apiKey = "sk-ant-test",
            model = "claude-x",
            streamEnabled = false
        )
        val repo = ChatRepositoryImpl(
            conversationDao,
            messageDao,
            ChatClientRegistry(
                mapOf(
                    AiProvider.OPENAI to openai,
                    AiProvider.ANTHROPIC to claude
                )
            ),
            FixedSettings(settings),
            com.afyzfur.afyzhub.data.remote.provider.WebSearchService(NoopTransport)
        )

        val result = repo.sendMessage(newConversation(), "你好")

        assertEquals("来自 Claude", result.getOrNull()?.content)
        // 未选中的提供商不应被调用。
        assertEquals(null, openai.completeTurns)
    }

    @Test
    fun `提供商无对应实现时报错而非静默失败`() = runTest {
        val settings = AppSettings(
            provider = AiProvider.GEMINI,
            apiKey = "key",
            streamEnabled = false
        )
        val repo = ChatRepositoryImpl(
            conversationDao,
            messageDao,
            // 故意只注册 OpenAI，模拟缺失实现。
            ChatClientRegistry(mapOf(AiProvider.OPENAI to RecordingClient())),
            FixedSettings(settings),
            com.afyzfur.afyzhub.data.remote.provider.WebSearchService(NoopTransport)
        )

        val result = repo.sendMessage(newConversation(), "你好")

        assertTrue(result.isFailure)
        val stored = messageDao.current.single()
        assertEquals(Constants.STATUS_FAILED, stored.status)
    }

    @Test
    fun `发送失败时用户消息标记为失败并可重试`() = runTest {
        val failing = RecordingClient(error = RuntimeException("网络异常"))
        val repo = repository(failing)
        val conversationId = newConversation()

        val result = repo.sendMessage(conversationId, "会失败的消息")

        assertTrue(result.isFailure)
        val stored = messageDao.current.single()
        assertEquals(Constants.STATUS_FAILED, stored.status)
        assertEquals("网络异常", stored.errorMessage)

        // 换成可用的客户端后重试应成功，且不产生重复的用户消息。
        val retryRepo = repository(RecordingClient(reply = "恢复了"))
        val retry = retryRepo.retryMessage(stored.id)

        assertTrue(retry.isSuccess)
        val users = messageDao.current.filter { it.role == Constants.ROLE_USER }
        assertEquals(1, users.size)
        assertEquals(Constants.STATUS_SUCCESS, users.single().status)
    }

    @Test
    fun `未配置密钥时直接失败`() = runTest {
        val client = RecordingClient()
        val repo = repository(client, AppSettings(apiKey = ""))

        val result = repo.sendMessage(newConversation(), "你好")

        assertTrue(result.isFailure)
        // 未配置密钥不应发出网络请求。
        assertEquals(null, client.completeTurns)
        assertEquals(null, client.streamTurns)
    }

    @Test
    fun `流式回复分片拼接为完整内容`() = runTest {
        val client = RecordingClient(chunks = listOf("你", "好", "，世界"))
        val repo = repository(
            client,
            AppSettings(apiKey = "sk-test", streamEnabled = true)
        )

        val result = repo.sendMessage(newConversation(), "打个招呼")

        assertTrue(result.isSuccess)
        assertEquals("你好，世界", result.getOrNull()?.content)
        // 走的是流式路径而非一次性接口。
        assertNotNull(client.streamTurns)
        assertEquals(null, client.completeTurns)
        val assistant = messageDao.current.single { it.role == Constants.ROLE_ASSISTANT }
        assertEquals("你好，世界", assistant.content)
        assertEquals(Constants.STATUS_SUCCESS, assistant.status)
    }

    @Test
    fun `流式中断时不留下残缺回复`() = runTest {
        val client = RecordingClient(chunks = listOf("开头", "中间"), failAfter = 1)
        val repo = repository(
            client,
            AppSettings(apiKey = "sk-test", streamEnabled = true)
        )

        val result = repo.sendMessage(newConversation(), "会断开")

        assertTrue(result.isFailure)
        // 半截的助手占位消息应被删除，只剩标记失败的用户消息。
        assertTrue(messageDao.current.none { it.role == Constants.ROLE_ASSISTANT })
        val user = messageDao.current.single()
        assertEquals(Constants.STATUS_FAILED, user.status)
        assertEquals("连接中断", user.errorMessage)
    }

    @Test
    fun `关闭流式时走一次性接口`() = runTest {
        val client = RecordingClient(reply = "一次性回复", chunks = listOf("不应被使用"))
        val repo = repository(
            client,
            AppSettings(apiKey = "sk-test", streamEnabled = false)
        )

        val result = repo.sendMessage(newConversation(), "你好")

        assertEquals("一次性回复", result.getOrNull()?.content)
        assertEquals(null, client.streamTurns)
        assertNotNull(client.completeTurns)
    }

    @Test
    fun `thinking guidance only applies to enabled unsupported models`() = runTest {
        val client = RecordingClient()
        val settings = AppSettings(
            provider = AiProvider.OPENAI,
            model = "gpt-4o",
            apiKey = "sk-test",
            streamEnabled = false,
            thinkingEnabled = true,
            thinkingGuidanceEnabled = true
        )
        repository(client, settings).sendMessage(newConversation(), "question")
        val system = checkNotNull(client.completeTurns).filter { it.role == Constants.ROLE_SYSTEM }
        assertEquals(1, system.size)
        assertTrue(system.single().content.contains("审慎分析问题"))

        val nativeClient = RecordingClient()
        repository(nativeClient, settings.copy(model = "o3-mini"))
            .sendMessage(newConversation(), "question")
        assertTrue(checkNotNull(nativeClient.completeTurns).none { it.role == Constants.ROLE_SYSTEM })

        val disabledClient = RecordingClient()
        repository(disabledClient, settings.copy(thinkingEnabled = false, thinkingGuidanceEnabled = false))
            .sendMessage(newConversation(), "question")
        assertTrue(checkNotNull(disabledClient.completeTurns).none { it.role == Constants.ROLE_SYSTEM })

        val guidanceOnlyClient = RecordingClient()
        repository(guidanceOnlyClient, settings.copy(thinkingEnabled = false))
            .sendMessage(newConversation(), "question")
        assertTrue(checkNotNull(guidanceOnlyClient.completeTurns).any { it.role == Constants.ROLE_SYSTEM })
    }

    @Test
    fun `上下文长度受上限约束`() = runTest {
        val client = RecordingClient()
        val repo = repository(client)
        val conversationId = newConversation()

        repeat(15) { repo.sendMessage(conversationId, "问题$it") }

        val size = checkNotNull(client.completeTurns).size
        assertTrue(size <= Constants.MAX_CONTEXT_MESSAGES)
    }
    @Test
    fun cacheSurvivesPersistenceObservationAndUndoInBothRequestModes() = runTest {
        for (stream in listOf(false, true)) {
            val usage = TokenUsage(1000, 20, 800)
            val client = RecordingClient(reply = "answer", chunks = listOf("answer"), usage = usage)
            val repo = repository(client, AppSettings(apiKey = "test", streamEnabled = stream))
            val conversationId = newConversation()
            val result = checkNotNull(repo.sendMessage(conversationId, "hello").getOrNull())
            assertEquals(800, result.cachedTokens)
            val observed = repo.getMessagesByConversationId(conversationId).first()
            val assistant = observed.single { it.role == Constants.ROLE_ASSISTANT }
            assertEquals(800, assistant.cachedTokens)
            val removed = repo.rollbackTo(assistant.id)
            repo.restoreMessages(removed)
            val restored = repo.getMessagesByConversationId(conversationId).first()
                .single { it.id == assistant.id }
            assertEquals(800, restored.cachedTokens)
            assertEquals(1000, restored.promptTokens)
            assertEquals(20, restored.completionTokens)
        }
    }

    @Test
    fun advancedSearchBuffersFirstPassAndDoesNotRepeatSpeculativeDraft() = runTest {
        val client = object : ChatClient {
            val requests = mutableListOf<List<ChatTurn>>()
            val streamRequests = mutableListOf<List<ChatTurn>>()
            override suspend fun complete(turns: List<ChatTurn>, settings: AppSettings): CompletionResult {
                requests += turns
                return CompletionResult("我暂时没有实时天气数据。<web_search>重庆今天天气</web_search>")
            }
            override fun stream(turns: List<ChatTurn>, settings: AppSettings): Flow<StreamEvent> {
                streamRequests += turns
                return flowOf(StreamEvent.TextDelta("搜索结果没有提供温度，无法确认。"), StreamEvent.Finished())
            }
            override suspend fun listModels(settings: AppSettings): List<String> = emptyList()
        }
        val settings = AppSettings(apiKey = "sk", model = "gpt-4o", streamEnabled = true, webSearchEnabled = true)
        val repo = ChatRepositoryImpl(
            conversationDao, messageDao, ChatClientRegistry(mapOf(settings.provider to client)), FixedSettings(settings),
            com.afyzfur.afyzhub.data.remote.provider.WebSearchService(NoopTransport)
        )
        val sent = checkNotNull(repo.sendMessage(newConversation(), "我查一下重庆今天的天气").getOrNull())
        assertFalse(sent.content.contains("我暂时没有实时天气数据"))
        assertTrue(sent.content.contains("<web_search>重庆今天天气</web_search>"))
        assertTrue(client.streamRequests.single().last().content.contains("用户的原始问题"))
        assertTrue(client.streamRequests.single().last().content.contains("不要用已有知识补写实时天气"))
        assertEquals("First pass is buffered rather than streamed", 1, client.requests.size)
        assertEquals("Second pass streams once", 1, client.streamRequests.size)
        assertEquals(1, Regex("<web_search>").findAll(sent.content).count())
    }
}
