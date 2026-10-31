package com.afyzfur.afyzhub.data.repository

import com.afyzfur.afyzhub.data.local.dao.MessageDao
import com.afyzfur.afyzhub.data.remote.provider.ChatClient
import com.afyzfur.afyzhub.data.remote.provider.ChatTurn
import com.afyzfur.afyzhub.data.remote.provider.CompletionResult
import com.afyzfur.afyzhub.data.remote.provider.StreamEvent
import com.afyzfur.afyzhub.data.remote.provider.TokenUsage
import com.afyzfur.afyzhub.data.settings.AppSettings
import com.afyzfur.afyzhub.domain.model.SendPhase
import kotlinx.coroutines.flow.collect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Focused collaborator for streaming reveal and throttled Room writes. */
internal class ChatStreamCollector {
    /**
     * 消费流式增量，边写库边累积完整文本。
     *
     * usage 只出现在流末尾的 Finished 事件里，且部分提供商不返回，
     * 因此返回值与非流式共用 [CompletionResult]，usage 可空。
     */
    /** 查询词与用户问题的字符重合度(0~1)。模型的缩写查询常不是子串,
     * 例如「重庆市」vs「重庆今天天气」, 用字符重合度兜住这类情况。 */
    private fun sharedChars(a: String, b: String): Double {
        val setA = a.filter { !it.isWhitespace() }.toSet()
        if (setA.isEmpty()) return 0.0
        val setB = b.filter { !it.isWhitespace() }.toSet()
        return setA.count { it in setB }.toDouble() / setA.size
    }

    private fun mergeTokenUsage(old: TokenUsage?, next: TokenUsage?): TokenUsage? {
        if (old == null) return next
        if (next == null) return old
        return TokenUsage(
            promptTokens = maxOf(old.promptTokens, next.promptTokens),
            completionTokens = maxOf(old.completionTokens, next.completionTokens),
            cachedTokens = listOfNotNull(old.cachedTokens, next.cachedTokens).maxOrNull()
        )
    }

    suspend fun collect(
        client: ChatClient,
        turns: List<ChatTurn>,
        settings: AppSettings,
        messageDao: MessageDao,
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
        // 改为逐字揭示: 每 33ms 把“已揭示”部分落库,
        // 步长按积压量自适应推进——API 一次给一大段也逐字显现,
        // API 慢时原样透传, 观感始终丝滑。
        var lastWritten = ""
        val revealLock = Any()
        var networkDone = false
        var revealed = initialContent.length
        val smoother = CoroutineScope(currentCoroutineContext()).launch(Dispatchers.IO) {
            while (true) {
                delay(33)
                val snapshot: String
                val finished: Boolean
                synchronized(revealLock) {
                    val full = builder.toString()
                    if (revealed < full.length) {
                        val backlog = full.length - revealed
                        // DeepSeek 风格逐字渐显: 每 33ms 小步进固定字数,
                        // 步长随积压自适应但幅度小(backlog/20), 视觉上是
                        // 连续的小步进而非大段跳变; 积压大时按比例加速
                        // 追平, 不会无限落后
                        revealed += maxOf(4, (backlog / 10).coerceAtLeast(1))
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
                        usage = mergeTokenUsage(usage, event.usage)
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

}
