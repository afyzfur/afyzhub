package com.afyzfur.afyzhub.domain.model

import androidx.annotation.StringRes
import com.afyzhub.chat.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** 一条关于用户的长期记忆。[manual] 为 true 表示由用户添加或编辑过，自动总结不会改写它。 */
@Serializable
data class MemoryItem(
    val id: String,
    val content: String,
    val manual: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

enum class MemoryFrequency(val id: String, @StringRes val labelRes: Int) {
    APP_OPEN("app_open", R.string.memory_frequency_app_open),
    DAILY("daily", R.string.memory_frequency_daily),
    WEEKLY("weekly", R.string.memory_frequency_weekly),
    MEMORY_SCREEN("memory_screen", R.string.memory_frequency_memory_screen);

    companion object {
        val DEFAULT = DAILY
        fun fromId(id: String?): MemoryFrequency = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

enum class MemoryTrigger { APP_OPEN, MEMORY_SCREEN, ENABLED }

enum class MemoryError(@StringRes val messageRes: Int) {
    NO_API(R.string.memory_error_no_api),
    REQUEST_FAILED(R.string.memory_error_request),
    INVALID_RESPONSE(R.string.memory_error_format)
}

data class MemorySnapshot(
    /** false until the store has been read, so the UI does not flash default values. */
    val loaded: Boolean = false,
    val enabled: Boolean = false,
    val frequency: MemoryFrequency = MemoryFrequency.DEFAULT,
    val items: List<MemoryItem> = emptyList(),
    val lastRunAt: Long = 0L,
    /** Highest message id already summarized; messages are read incrementally after it. */
    val lastMessageId: Long = 0L
) {
    fun isDue(trigger: MemoryTrigger, now: Long): Boolean {
        if (!enabled) return false
        // A clock moved backwards would otherwise block daily and weekly runs indefinitely.
        val elapsed = if (now < lastRunAt) Long.MAX_VALUE else now - lastRunAt
        return when (trigger) {
            MemoryTrigger.ENABLED -> lastRunAt == 0L
            MemoryTrigger.MEMORY_SCREEN -> frequency == MemoryFrequency.MEMORY_SCREEN
            MemoryTrigger.APP_OPEN -> when (frequency) {
                MemoryFrequency.APP_OPEN -> true
                MemoryFrequency.DAILY -> elapsed >= DAY_MS
                MemoryFrequency.WEEKLY -> elapsed >= WEEK_MS
                MemoryFrequency.MEMORY_SCREEN -> false
            }
        }
    }
}

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val WEEK_MS = 7 * DAY_MS

private val memoryListJson = Json { ignoreUnknownKeys = true }
private val bulletLine = Regex("^(?:[-*•]|\\d+[.)、])\\s*(.+)$")

/**
 * 解析模型返回的记忆列表。
 *
 * 优先取 JSON 字符串数组（允许外面包着代码块），其次接受逐行的列表项。
 * 两种都识别不了时返回 null，调用方保持原有记忆不变。
 */
fun parseMemoryList(text: String): List<String>? {
    val body = text.trim()
    val start = body.indexOf('[')
    val end = body.lastIndexOf(']')
    if (start >= 0 && end > start) {
        val decoded = runCatching {
            memoryListJson.decodeFromString(ListSerializer(String.serializer()), body.substring(start, end + 1))
        }.getOrNull()
        if (decoded != null) return decoded.map { it.trim() }.filter { it.isNotEmpty() }
    }
    return body.lines()
        .mapNotNull { line -> bulletLine.find(line.trim())?.groupValues?.get(1)?.trim() }
        .filter { it.isNotEmpty() }
        .ifEmpty { null }
}

fun buildMemoryPrompt(items: List<MemoryItem>): String = buildString {
    append("【关于用户的长期记忆】\n")
    append(
        "以下内容由以往对话总结或用户手动添加。请在相关时自然地参考，使回答更贴合用户；" +
            "不要逐条复述，也不要主动提起这些记忆，除非用户问起。若与用户当前的说法冲突，以当前说法为准。\n"
    )
    items.forEach { append("- ").append(it.content).append('\n') }
}.trimEnd()
