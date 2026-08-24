package com.afyzfur.afyzhub.data.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * 请求日志的内存存储。
 *
 * 只保存在内存，进程结束即丢失。选择不落库的理由：
 * 日志的用途是"刚才那条消息为什么失败"，排查与故障在同一次会话内；
 * 而落库需要再加一次数据库迁移，并且请求体和响应体体积不小，
 * 长期累积会明显占用空间，还得额外做清理策略。
 *
 * 超出 [MAX_ENTRIES] 时丢弃最旧的记录。
 */
class RequestLogStore {

    private val _entries = MutableStateFlow<List<RequestLogEntry>>(emptyList())

    /** 最新的在前，便于界面直接展示。 */
    val entries: StateFlow<List<RequestLogEntry>> = _entries.asStateFlow()

    private val nextId = AtomicLong(1)

    // 记录来自任意 IO 线程，读写列表需串行化
    private val mutex = Mutex()

    /** 分配一个记录 id。调用方在请求开始时取，结束时用它提交。 */
    fun newId(): Long = nextId.getAndIncrement()

    suspend fun record(entry: RequestLogEntry) {
        mutex.withLock {
            val updated = ArrayList<RequestLogEntry>(_entries.value.size + 1)
            updated.add(entry)
            updated.addAll(_entries.value)
            // 超限时截断尾部（最旧的）
            _entries.value = if (updated.size > MAX_ENTRIES) {
                updated.subList(0, MAX_ENTRIES).toList()
            } else {
                updated
            }
        }
    }

    suspend fun clear() {
        mutex.withLock { _entries.value = emptyList() }
    }

    private companion object {
        /**
         * 保留条数上限。
         *
         * 每条记录含请求体与响应体，按单条 8KB 估算，100 条约 800KB，
         * 对移动端可接受；再多则查看时也难以定位。
         */
        const val MAX_ENTRIES = 100
    }
}
