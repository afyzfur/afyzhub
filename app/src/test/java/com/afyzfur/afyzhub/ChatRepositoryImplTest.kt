package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.data.local.entity.ConversationEntity
import com.afyzfur.afyzhub.data.remote.OpenAIApi
import com.afyzfur.afyzhub.data.remote.dto.ChatRequest
import com.afyzfur.afyzhub.data.remote.dto.ChatResponse
import com.afyzfur.afyzhub.data.remote.dto.Choice
import com.afyzfur.afyzhub.data.remote.dto.ResponseMessage
import com.afyzfur.afyzhub.data.repository.ChatRepositoryImpl
import com.afyzfur.afyzhub.data.settings.AppSettings
import com.afyzfur.afyzhub.data.settings.SettingsProvider
import com.afyzfur.afyzhub.util.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 记录最近一次请求的假 API。 */
private class RecordingApi(
    private val reply: String? = "好的",
    private val error: Exception? = null
) : OpenAIApi {
    var lastRequest: ChatRequest? = null

    override suspend fun createChatCompletion(request: ChatRequest): ChatResponse {
        lastRequest = request
        error?.let { throw it }
        return ChatResponse(
            id = "test",
            choices = listOf(
                Choice(
                    index = 0,
                    message = ResponseMessage(role = Constants.ROLE_ASSISTANT, content = reply.orEmpty())
                )
            )
        )
    }
}

private class FixedSettings(
    private val settings: AppSettings
) : SettingsProvider {
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

    private fun repository(
        api: OpenAIApi,
        settings: AppSettings = AppSettings(apiKey = "sk-test", model = "gpt-4o")
    ) = ChatRepositoryImpl(conversationDao, messageDao, api, FixedSettings(settings))

    @Test
    fun `请求携带完整历史上下文`() = runTest {
        val api = RecordingApi()
        val repo = repository(api)
        val conversationId = newConversation()

        repo.sendMessage(conversationId, "第一个问题")
        repo.sendMessage(conversationId, "第二个问题")

        val sent = api.lastRequest
        assertNotNull(sent)
        // 用户1、助手1、用户2 共三条，证明多轮上下文已带上。
        assertEquals(3, sent!!.messages.size)
        assertEquals("第一个问题", sent.messages[0].content)
        assertEquals(Constants.ROLE_ASSISTANT, sent.messages[1].role)
        assertEquals("第二个问题", sent.messages[2].content)
    }

    @Test
    fun `请求使用设置中选择的模型`() = runTest {
        val api = RecordingApi()
        val repo = repository(api, AppSettings(apiKey = "sk-test", model = "my-custom-model"))

        repo.sendMessage(newConversation(), "你好")

        assertEquals("my-custom-model", api.lastRequest?.model)
    }

    @Test
    fun `发送失败时用户消息标记为失败并可重试`() = runTest {
        val failing = RecordingApi(error = RuntimeException("网络异常"))
        val repo = repository(failing)
        val conversationId = newConversation()

        val result = repo.sendMessage(conversationId, "会失败的消息")
        assertTrue(result.isFailure)

        val stored = messageDao.current.single()
        assertEquals(Constants.STATUS_FAILED, stored.status)
        assertEquals("网络异常", stored.errorMessage)

        // 换成可用的 API 后重试应成功，且不产生重复的用户消息。
        val working = RecordingApi(reply = "恢复了")
        val retryRepo = repository(working)
        val retry = retryRepo.retryMessage(stored.id)

        assertTrue(retry.isSuccess)
        val users = messageDao.current.filter { it.role == Constants.ROLE_USER }
        assertEquals(1, users.size)
        assertEquals(Constants.STATUS_SUCCESS, users.single().status)
    }

    @Test
    fun `未配置密钥时直接失败`() = runTest {
        val api = RecordingApi()
        val repo = repository(api, AppSettings(apiKey = ""))

        val result = repo.sendMessage(newConversation(), "你好")

        assertTrue(result.isFailure)
        // 未配置密钥不应发出网络请求。
        assertEquals(null, api.lastRequest)
    }

    @Test
    fun `上下文长度受上限约束`() = runTest {
        val api = RecordingApi()
        val repo = repository(api)
        val conversationId = newConversation()

        repeat(15) { repo.sendMessage(conversationId, "问题$it") }

        val size = api.lastRequest!!.messages.size
        assertTrue(size <= Constants.MAX_CONTEXT_MESSAGES)
    }
}
