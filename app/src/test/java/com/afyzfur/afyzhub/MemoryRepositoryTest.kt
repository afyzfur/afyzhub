package com.afyzfur.afyzhub

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.afyzfur.afyzhub.data.local.entity.ConversationEntity
import com.afyzfur.afyzhub.data.local.entity.MessageEntity
import com.afyzfur.afyzhub.data.memory.MemoryRepository
import com.afyzfur.afyzhub.data.memory.mergeAutoMemories
import com.afyzfur.afyzhub.data.remote.provider.ChatClient
import com.afyzfur.afyzhub.data.remote.provider.ChatClientRegistry
import com.afyzfur.afyzhub.data.remote.provider.ChatTurn
import com.afyzfur.afyzhub.data.remote.provider.CompletionResult
import com.afyzfur.afyzhub.data.remote.provider.StreamEvent
import com.afyzfur.afyzhub.data.remote.provider.Transport
import com.afyzfur.afyzhub.data.remote.provider.WebSearchService
import com.afyzfur.afyzhub.data.repository.ChatRepositoryImpl
import com.afyzfur.afyzhub.data.settings.AppSettings
import com.afyzfur.afyzhub.data.settings.SettingsProvider
import com.afyzfur.afyzhub.domain.model.AiProvider
import com.afyzfur.afyzhub.domain.model.MemoryError
import com.afyzfur.afyzhub.domain.model.MemoryFrequency
import com.afyzfur.afyzhub.domain.model.MemoryItem
import com.afyzfur.afyzhub.domain.model.MemorySnapshot
import com.afyzfur.afyzhub.domain.model.MemoryTrigger
import com.afyzfur.afyzhub.domain.model.parseMemoryList
import com.afyzfur.afyzhub.util.Constants
import com.afyzfur.afyzhub.util.DisplayModeSpec
import com.afyzfur.afyzhub.util.highestRefreshMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRepositoryTest {
    private class MemoryStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val next = transform(data.value); data.value = next; return next
        }
    }

    private class ScriptedClient(var reply: String = "[]") : ChatClient {
        val requests = mutableListOf<List<ChatTurn>>()
        val usedSettings = mutableListOf<AppSettings>()
        override suspend fun complete(turns: List<ChatTurn>, settings: AppSettings): CompletionResult {
            requests += turns
            usedSettings += settings
            return CompletionResult(reply)
        }
        override fun stream(turns: List<ChatTurn>, settings: AppSettings): Flow<StreamEvent> = emptyFlow()
        override suspend fun listModels(settings: AppSettings): List<String> = emptyList()
    }

    private class MutableSettings(var value: AppSettings) : SettingsProvider {
        override suspend fun current(): AppSettings = value
    }

    private val messages = FakeMessageDao()
    private val client = ScriptedClient()
    private val settings = MutableSettings(AppSettings(apiKey = "sk", thinkingEnabled = true, webSearchEnabled = true))
    private var now = 1_000_000L

    private fun repository() = MemoryRepository(
        MemoryStore(), messages, ChatClientRegistry(mapOf(AiProvider.DEFAULT to client)), settings,
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.StandardTestDispatcher()), { now }
    )

    private suspend fun say(role: String, text: String, conversation: Long = 1L, status: String = Constants.STATUS_SUCCESS) =
        messages.insertMessage(MessageEntity(conversationId = conversation, content = text, role = role, status = status))

    @Test fun summarizesOnlyNewMessagesAndCarriesExistingMemoryForward() = runTest {
        val repo = repository()
        repo.setEnabled(true)
        say(Constants.ROLE_USER, "我是 Android 开发者")
        say(Constants.ROLE_ASSISTANT, "<think>推理</think>好的")
        client.reply = "```json\n[\"用户是 Android 开发者\"]\n```"
        assertTrue(repo.summarizeNow())
        val first = repo.memoryFlow.first()
        assertEquals(listOf("用户是 Android 开发者"), first.items.map { it.content })
        assertEquals(2L, first.lastMessageId)
        assertEquals(now, first.lastRunAt)
        val firstInput = client.requests.single().last().content
        assertTrue(firstInput.contains("我是 Android 开发者"))
        assertFalse("Reasoning is not sent for summarization", firstInput.contains("推理"))
        // Background summaries never spend reasoning budget or trigger web search.
        assertFalse(client.usedSettings.single().thinkingEnabled)
        assertFalse(client.usedSettings.single().webSearchEnabled)

        say(Constants.ROLE_USER, "我喜欢简洁的回答")
        client.reply = "[\"用户是 Android 开发者\", \"用户喜欢简洁的回答\"]"
        assertTrue(repo.summarizeNow())
        val secondInput = client.requests.last().last().content
        assertTrue(secondInput.contains("我喜欢简洁的回答"))
        assertFalse("Already summarized messages are not resent", secondInput.contains("我是 Android 开发者\n"))
        assertTrue("Existing memories are given back for merging", secondInput.contains("- 用户是 Android 开发者"))
        val second = repo.memoryFlow.first()
        assertEquals(first.items.first().id, second.items.first().id)
        assertEquals(3L, second.lastMessageId)

        assertTrue(repo.summarizeNow())
        assertEquals("No new messages means no request", 2, client.requests.size)
    }

    @Test fun manualMemoriesSurviveAutomaticUpdatesAndAreNotDuplicated() = runTest {
        val repo = repository()
        repo.setEnabled(true)
        repo.addManual("用户住在重庆")
        say(Constants.ROLE_USER, "帮我写个脚本")
        client.reply = "[\"用户住在重庆\", \"用户经常写脚本\"]"
        assertTrue(repo.summarizeNow())
        val items = repo.memoryFlow.first().items
        assertEquals(listOf("用户住在重庆", "用户经常写脚本"), items.map { it.content })
        assertEquals(listOf(true, false), items.map { it.manual })

        val auto = items.last()
        repo.update(auto.id, "用户常用 Python 写脚本")
        say(Constants.ROLE_USER, "今天天气怎么样")
        client.reply = "[]"
        assertTrue(repo.summarizeNow())
        val after = repo.memoryFlow.first().items
        assertEquals("An empty model reply does not erase memories", 2, after.size)
        assertTrue(after.single { it.id == auto.id }.manual)
    }

    @Test fun failuresKeepProgressSoTheSameMessagesAreRetried() = runTest {
        val repo = repository()
        repo.setEnabled(true)
        say(Constants.ROLE_USER, "记住我叫小王")
        client.reply = "我无法理解"
        assertFalse(repo.summarizeNow())
        assertEquals(MemoryError.INVALID_RESPONSE, repo.runState.value.error)
        assertEquals(0L, repo.memoryFlow.first().lastMessageId)

        settings.value = settings.value.copy(apiKey = "")
        assertFalse(repo.summarizeNow())
        assertEquals(MemoryError.NO_API, repo.runState.value.error)

        settings.value = settings.value.copy(apiKey = "sk")
        client.reply = "- 用户叫小王"
        assertTrue(repo.summarizeNow())
        assertNull(repo.runState.value.error)
        assertEquals(listOf("用户叫小王"), repo.memoryFlow.first().items.map { it.content })
        assertTrue(client.requests.last().last().content.contains("记住我叫小王"))
    }

    @Test fun repliesStillStreamingAreLeftForTheNextRun() = runTest {
        val repo = repository()
        repo.setEnabled(true)
        say(Constants.ROLE_USER, "第一句")
        say(Constants.ROLE_ASSISTANT, "生成中", status = Constants.STATUS_SENDING)
        say(Constants.ROLE_USER, "另一个对话", conversation = 2L)
        client.reply = "[\"用户在测试\"]"
        assertTrue(repo.summarizeNow())
        assertEquals("Progress stops before the unfinished reply", 1L, repo.memoryFlow.first().lastMessageId)
        assertFalse(client.requests.single().last().content.contains("另一个对话"))
    }

    @Test fun memoryIsInjectedIntoEveryConversationOnlyWhileEnabled() = runTest {
        val repo = repository()
        repo.addManual("用户偏好中文回答")
        assertNull("Disabled memory is never sent", repo.memoryPrompt())
        repo.setEnabled(true)
        assertTrue(repo.memoryPrompt().orEmpty().contains("用户偏好中文回答"))

        val chatClient = ScriptedClient("好的")
        val conversations = FakeConversationDao()
        val chatMessages = FakeMessageDao()
        val chat = ChatRepositoryImpl(
            conversations, chatMessages,
            ChatClientRegistry(mapOf(AiProvider.DEFAULT to chatClient)),
            MutableSettings(AppSettings(apiKey = "sk", streamEnabled = false, systemPrompt = "你是小助手")),
            WebSearchService(NoopTransport),
            repo
        )
        val id = conversations.insertConversation(ConversationEntity(title = "新对话"))
        chat.sendMessage(id, "你好")
        val turns = chatClient.requests.first()
        assertEquals("Role setting stays first", "system", turns[0].role)
        assertTrue(turns[0].content.contains("你是小助手"))
        assertEquals("system", turns[1].role)
        assertTrue(turns[1].content.contains("用户偏好中文回答"))
        assertEquals("你好", turns.last().content)
    }

    @Test fun frequencyDecidesWhenAutomaticSummariesRun() {
        val day = 24L * 60 * 60 * 1000
        fun state(frequency: MemoryFrequency, last: Long, enabled: Boolean = true) =
            MemorySnapshot(loaded = true, enabled = enabled, frequency = frequency, lastRunAt = last)
        val now = 100 * day
        assertTrue(state(MemoryFrequency.APP_OPEN, now).isDue(MemoryTrigger.APP_OPEN, now))
        assertFalse(state(MemoryFrequency.DAILY, now - day + 1).isDue(MemoryTrigger.APP_OPEN, now))
        assertTrue(state(MemoryFrequency.DAILY, now - day).isDue(MemoryTrigger.APP_OPEN, now))
        assertFalse(state(MemoryFrequency.WEEKLY, now - 6 * day).isDue(MemoryTrigger.APP_OPEN, now))
        assertTrue(state(MemoryFrequency.WEEKLY, now - 7 * day).isDue(MemoryTrigger.APP_OPEN, now))
        assertFalse(state(MemoryFrequency.MEMORY_SCREEN, 0L).isDue(MemoryTrigger.APP_OPEN, now))
        assertTrue(state(MemoryFrequency.MEMORY_SCREEN, now).isDue(MemoryTrigger.MEMORY_SCREEN, now))
        assertFalse(state(MemoryFrequency.DAILY, now).isDue(MemoryTrigger.MEMORY_SCREEN, now))
        assertTrue(state(MemoryFrequency.WEEKLY, 0L).isDue(MemoryTrigger.ENABLED, now))
        assertFalse(state(MemoryFrequency.APP_OPEN, now, enabled = false).isDue(MemoryTrigger.APP_OPEN, now))
        assertTrue("A clock moved backwards does not block runs", state(MemoryFrequency.WEEKLY, now + day).isDue(MemoryTrigger.APP_OPEN, now))
    }

    @Test fun parsesJsonCodeBlocksAndBulletListsAndRejectsProse() {
        assertEquals(listOf("a", "b"), parseMemoryList("结果：\n```json\n[\"a\", \" b \"]\n```"))
        assertEquals(listOf("用户喜欢猫", "用户在北京"), parseMemoryList("1. 用户喜欢猫\n2、用户在北京"))
        assertEquals(emptyList<String>(), parseMemoryList("[]"))
        assertNull(parseMemoryList("抱歉，我不能完成"))
    }

    @Test fun mergeKeepsIdentityOfUnchangedEntries() {
        val kept = MemoryItem("keep", "用户喜欢猫", createdAt = 1L, updatedAt = 1L)
        val merged = mergeAutoMemories(listOf(kept), listOf("用户喜欢猫", "用户喜欢猫", "用户在北京"), emptyList(), 9L)
        assertEquals(listOf("keep"), merged.take(1).map { it.id })
        assertEquals(2, merged.size)
        assertEquals(9L, merged.last().createdAt)
        assertNotNull(merged.last().id)
    }

    @Test fun refreshRateSelectsHighestModeAtCurrentResolution() {
        val current = DisplayModeSpec(1, 1080, 2400, 60f)
        val modes = listOf(
            current,
            DisplayModeSpec(2, 1080, 2400, 120f),
            DisplayModeSpec(3, 1080, 2400, 90f),
            DisplayModeSpec(4, 1440, 3200, 144f)
        )
        assertEquals(2, highestRefreshMode(current, modes)?.id)
        assertEquals(1, highestRefreshMode(current, listOf(current))?.id)
    }

    private object NoopTransport : Transport {
        override suspend fun getForText(baseUrl: String, path: String, headers: Map<String, String>, query: Map<String, String>, logContext: com.afyzfur.afyzhub.data.log.RequestLogContext) = ""
        override suspend fun postForText(baseUrl: String, path: String, headers: Map<String, String>, body: String, query: Map<String, String>, logContext: com.afyzfur.afyzhub.data.log.RequestLogContext) = ""
        override fun postForSse(baseUrl: String, path: String, headers: Map<String, String>, body: String, query: Map<String, String>, logContext: com.afyzfur.afyzhub.data.log.RequestLogContext): Flow<String> = emptyFlow()
    }
}
