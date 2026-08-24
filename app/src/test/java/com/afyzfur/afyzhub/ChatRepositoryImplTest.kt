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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
        FixedSettings(settings)
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
        assertEquals(3, sent!!.size)
        assertEquals("第一个问题", sent[0].content)
        assertEquals(Constants.ROLE_ASSISTANT, sent[1].role)
        assertEquals("第二个问题", sent[2].content)
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
            FixedSettings(settings)
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
            FixedSettings(settings)
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
    fun `上下文长度受上限约束`() = runTest {
        val client = RecordingClient()
        val repo = repository(client)
        val conversationId = newConversation()

        repeat(15) { repo.sendMessage(conversationId, "问题$it") }

        val size = client.completeTurns!!.size
        assertTrue(size <= Constants.MAX_CONTEXT_MESSAGES)
    }
}
