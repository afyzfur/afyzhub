package com.afyzfur.afyzhub.data.memory

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.afyzfur.afyzhub.data.local.dao.MessageDao
import com.afyzfur.afyzhub.data.local.entity.MessageEntity
import com.afyzfur.afyzhub.data.remote.provider.ChatClientRegistry
import com.afyzfur.afyzhub.data.remote.provider.ChatTurn
import com.afyzfur.afyzhub.data.settings.AppSettings
import com.afyzfur.afyzhub.data.settings.SettingsProvider
import com.afyzfur.afyzhub.domain.model.MemoryError
import com.afyzfur.afyzhub.domain.model.MemoryFrequency
import com.afyzfur.afyzhub.domain.model.MemoryItem
import com.afyzfur.afyzhub.domain.model.MemorySnapshot
import com.afyzfur.afyzhub.domain.model.MemoryTrigger
import com.afyzfur.afyzhub.domain.model.buildMemoryPrompt
import com.afyzfur.afyzhub.domain.model.parseMemoryList
import com.afyzfur.afyzhub.domain.model.parseThinking
import com.afyzfur.afyzhub.domain.model.stripSearchSources
import com.afyzfur.afyzhub.util.Constants
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 对话层只需要「当前应注入的记忆」，不关心它们如何产生。 */
interface MemoryProvider {
    /** 记忆关闭或为空时返回 null。 */
    suspend fun memoryPrompt(): String?

    companion object {
        val NONE: MemoryProvider = object : MemoryProvider {
            override suspend fun memoryPrompt(): String? = null
        }
    }
}

data class MemoryRunState(val running: Boolean = false, val error: MemoryError? = null)

/**
 * 长期记忆的存储与自动总结。
 *
 * 总结按消息 id 增量进行：只把上次之后新增的对话交给模型，连同已有记忆一起更新。
 * 全量重送历史会让每次总结的开销随使用时间无限增长。每批成功后立即落盘进度，
 * 请求中途失败时下次从失败的那批继续，不重复也不遗漏。
 */
class MemoryRepository(
    private val dataStore: DataStore<Preferences>,
    private val messageDao: MessageDao,
    private val clientRegistry: ChatClientRegistry,
    private val settingsProvider: SettingsProvider,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis
) : MemoryProvider {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val itemsSerializer = ListSerializer(MemoryItem.serializer())
    private val mutex = Mutex()
    private val mutableRunState = MutableStateFlow(MemoryRunState())
    val runState: StateFlow<MemoryRunState> = mutableRunState.asStateFlow()

    val memoryFlow: Flow<MemorySnapshot> = dataStore.data.map { read(it) }.flowOn(Dispatchers.Default)
    val state: StateFlow<MemorySnapshot> = memoryFlow.stateIn(scope, SharingStarted.Eagerly, MemorySnapshot())

    override suspend fun memoryPrompt(): String? {
        val current = memoryFlow.first()
        return if (current.enabled && current.items.isNotEmpty()) buildMemoryPrompt(current.items) else null
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[enabledKey] = enabled }
        if (enabled) onTrigger(MemoryTrigger.ENABLED)
    }

    suspend fun setFrequency(frequency: MemoryFrequency) {
        dataStore.edit { it[frequencyKey] = frequency.id }
    }

    suspend fun addManual(text: String) {
        val content = text.trim().take(MAX_ITEM_CHARS)
        if (content.isEmpty()) return
        val now = clock()
        editItems { it + MemoryItem(UUID.randomUUID().toString(), content, manual = true, createdAt = now, updatedAt = now) }
    }

    /** 用户改过的条目转为手动记忆，之后的自动总结不再改写它。 */
    suspend fun update(id: String, text: String) {
        val content = text.trim().take(MAX_ITEM_CHARS)
        if (content.isEmpty()) return
        val now = clock()
        editItems { list -> list.map { if (it.id == id) it.copy(content = content, manual = true, updatedAt = now) else it } }
    }

    suspend fun delete(id: String) = editItems { list -> list.filterNot { it.id == id } }

    /** 只清空条目，不重置进度：之后只从新的对话里总结。 */
    suspend fun clear() = editItems { emptyList() }

    /** 按频率判断是否需要总结。在应用级作用域里运行，离开页面不会中断。 */
    fun onTrigger(trigger: MemoryTrigger) {
        scope.launch {
            val due = try {
                memoryFlow.first().isDue(trigger, clock())
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                false
            }
            if (due) summarizeNow()
        }
    }

    fun summarizeInBackground() {
        scope.launch { summarizeNow() }
    }

    /** 已有总结在运行时直接返回 false，不排队重复请求。 */
    suspend fun summarizeNow(): Boolean {
        if (!mutex.tryLock()) return false
        mutableRunState.value = MemoryRunState(running = true)
        var error: MemoryError? = null
        try {
            error = try {
                runSummary()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                MemoryError.REQUEST_FAILED
            }
        } finally {
            mutableRunState.value = MemoryRunState(running = false, error = error)
            mutex.unlock()
        }
        return error == null
    }

    private suspend fun runSummary(): MemoryError? {
        var settings: AppSettings? = null
        var remaining = MAX_BATCHES
        while (remaining-- > 0) {
            val state = memoryFlow.first()
            // 仍在生成的回复之后的消息留到下次，否则进度会越过它、永远漏掉这条回复。
            val pending = messageDao.getFirstPendingId()
            val batch = messageDao.getMessagesAfter(state.lastMessageId, BATCH_SIZE)
                .filter { pending == null || it.id < pending }
            val (transcript, lastId) = buildTranscript(batch, TRANSCRIPT_LIMIT) ?: break
            if (transcript.isBlank()) {
                commitProgress(lastId, null)
                continue
            }
            // 没有新对话时不读配置，也不报「未配置 API」。
            val active = settings ?: settingsProvider.current().also {
                if (it.apiKey.isBlank()) return MemoryError.NO_API
            }.copy(
                // 总结是后台辅助请求：不带思考预算、不联网，控制耗时与用量。
                thinkingEnabled = false,
                thinkingGuidanceEnabled = false,
                webSearchEnabled = false,
                geminiSearchEnabled = false
            ).also { settings = it }
            val reply = try {
                clientRegistry.clientFor(active.provider).complete(
                    listOf(
                        ChatTurn(role = Constants.ROLE_SYSTEM, content = SUMMARY_PROMPT),
                        ChatTurn(role = Constants.ROLE_USER, content = summaryInput(state.items, transcript))
                    ),
                    active
                ).content
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                return MemoryError.REQUEST_FAILED
            }
            val parsed = parseMemoryList(stripSearchSources(parseThinking(reply).answer))
                ?: return MemoryError.INVALID_RESPONSE
            commitProgress(lastId, parsed)
        }
        dataStore.edit { it[lastRunKey] = clock() }
        return null
    }

    private suspend fun commitProgress(lastId: Long, autoTexts: List<String>?) {
        val now = clock()
        dataStore.edit { prefs ->
            if (autoTexts != null) {
                // 重新读取：总结期间用户可能添加或编辑了手动记忆。
                val current = decodeItems(prefs[itemsKey])
                val manual = current.filter { it.manual }
                prefs[itemsKey] = encodeItems(manual + mergeAutoMemories(current.filterNot { it.manual }, autoTexts, manual, now))
            }
            prefs[lastMessageKey] = maxOf(prefs[lastMessageKey] ?: 0L, lastId)
        }
    }

    private suspend fun editItems(transform: (List<MemoryItem>) -> List<MemoryItem>) {
        dataStore.edit { prefs -> prefs[itemsKey] = encodeItems(transform(decodeItems(prefs[itemsKey]))) }
    }

    private fun read(prefs: Preferences) = MemorySnapshot(
        loaded = true,
        enabled = prefs[enabledKey] ?: false,
        frequency = MemoryFrequency.fromId(prefs[frequencyKey]),
        items = decodeItems(prefs[itemsKey]),
        lastRunAt = prefs[lastRunKey] ?: 0L,
        lastMessageId = prefs[lastMessageKey] ?: 0L
    )

    private fun decodeItems(raw: String?): List<MemoryItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(itemsSerializer, raw) }.getOrDefault(emptyList())
    }

    private fun encodeItems(items: List<MemoryItem>): String = json.encodeToString(itemsSerializer, items)

    private fun summaryInput(items: List<MemoryItem>, transcript: String) = buildString {
        val manual = items.filter { it.manual }
        val auto = items.filterNot { it.manual }
        append("用户手动保存的记忆（只读，不要输出，也不要重复）：\n")
        if (manual.isEmpty()) append("（无）\n") else manual.forEach { append("- ").append(it.content).append('\n') }
        append("\n已有的自动记忆：\n")
        if (auto.isEmpty()) append("（无）\n") else auto.forEach { append("- ").append(it.content).append('\n') }
        append("\n新的对话记录：\n").append(transcript)
    }

    private companion object {
        val enabledKey = booleanPreferencesKey("memory_enabled")
        val frequencyKey = stringPreferencesKey("memory_frequency")
        val itemsKey = stringPreferencesKey("memory_items")
        val lastRunKey = longPreferencesKey("memory_last_run_at")
        val lastMessageKey = longPreferencesKey("memory_last_message_id")

        const val BATCH_SIZE = 200
        const val MAX_BATCHES = 10
        const val TRANSCRIPT_LIMIT = 12_000

        val SUMMARY_PROMPT = """
            你负责维护一份“关于用户的长期记忆”，它会在以后的所有对话中提供给助手参考。
            输入包括：用户手动保存的记忆、已有的自动记忆，以及一段新的对话记录。
            请输出更新后的完整自动记忆列表：
            1. 只记录对以后对话长期有用的信息：用户的身份背景、长期目标与项目、偏好与习惯、明确要求记住的事。
            2. 不要记录一次性的问题、闲聊、助手回答的内容，也不要记录密码、密钥、证件号等敏感信息。
            3. 已有记忆仍然成立就原样保留；与新信息冲突时以新信息为准改写；意思相近的合并。
            4. 不要重复用户手动保存的记忆。
            5. 每条是一句简洁的中文陈述，以“用户”为主语，不超过 60 字；最多 30 条。
            6. 只输出 JSON 字符串数组，例如 ["用户是一名 Android 开发者"]，不要任何解释。
        """.trimIndent()
    }
}

internal const val MAX_ITEM_CHARS = 200
internal const val MAX_AUTO_ITEMS = 40
private const val USER_MESSAGE_LIMIT = 1_500
private const val ASSISTANT_MESSAGE_LIMIT = 600

/**
 * 把一批消息整理成对话记录，返回记录与已纳入的最后一条消息 id。
 * 助手回复去掉思考过程与搜索来源，并截得比用户消息短：记忆关于用户，不关于回答。
 */
internal fun buildTranscript(messages: List<MessageEntity>, limit: Int): Pair<String, Long>? {
    if (messages.isEmpty()) return null
    val out = StringBuilder()
    var lastIncluded = messages.first().id
    var conversation = -1L
    var hasText = false
    for (message in messages) {
        val text = when (message.role) {
            Constants.ROLE_USER -> message.content.trim().take(USER_MESSAGE_LIMIT)
            Constants.ROLE_ASSISTANT -> stripSearchSources(parseThinking(message.content).answer).trim().take(ASSISTANT_MESSAGE_LIMIT)
            else -> ""
        }
        val line = buildString {
            if (message.conversationId != conversation) append("\n[对话 ").append(message.conversationId).append("]\n")
            if (text.isNotEmpty()) {
                append(if (message.role == Constants.ROLE_USER) "用户：" else "助手：").append(text).append('\n')
            }
        }
        if (out.isNotEmpty() && out.length + line.length > limit) break
        out.append(line)
        conversation = message.conversationId
        lastIncluded = message.id
        if (text.isNotEmpty()) hasText = true
    }
    return (if (hasText) out.toString().trim() else "") to lastIncluded
}

/** 文本未变的条目保留原 id 与创建时间；模型返回空列表时不清空已有记忆。 */
internal fun mergeAutoMemories(
    previous: List<MemoryItem>,
    texts: List<String>,
    manual: List<MemoryItem>,
    now: Long
): List<MemoryItem> {
    val byContent = previous.associateBy { it.content }
    val manualContents = manual.map { it.content }.toSet()
    val cleaned = texts.map { it.trim().take(MAX_ITEM_CHARS) }
        .filter { it.isNotEmpty() && it !in manualContents }
        .distinct()
        .take(MAX_AUTO_ITEMS)
    if (cleaned.isEmpty()) return previous
    return cleaned.map { byContent[it] ?: MemoryItem(UUID.randomUUID().toString(), it, manual = false, createdAt = now, updatedAt = now) }
}