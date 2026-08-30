package com.afyzfur.afyzhub.data.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * 请求日志的存储。
 *
 * 成功与失败的记录都落盘，由 [LogRetention] 决定何时清掉。此前成功的
 * 只放内存、失败的落盘且永不过期——前者让"昨天那次为什么慢"无从查证，
 * 后者让文件无限增长，两个方向都不理想。
 *
 * 落盘用单独的 JSON 文件而非 DataStore：单条响应体可达数 KB，
 * 混进设置的 preferences 会让每次读设置都带上这些数据。
 *
 * [persistDir] 为 null 时退化为纯内存，便于在单元测试里使用。
 *
 * 超出 [MAX_ENTRIES] 时丢弃最旧的记录。
 */
class RequestLogStore(
    private val persistDir: File? = null
) {

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
            // 成功与失败都落盘。此前只落失败，重启后就查不到
            // "刚才那几条分别用了哪个模型、各花了多久"
            persistAll()
        }
    }

    /**
     * 载入上次运行留下的失败记录。
     *
     * 由应用启动时调用一次。文件损坏或解析失败时静默清空——
     * 日志读不出来不该影响应用启动，而且它本身是可再生的诊断信息。
     */
    suspend fun restore() {
        val file = logFile() ?: return
        mutex.withLock {
            val restored = try {
                if (!file.exists()) return@withLock
                requestLogJson
                    .decodeFromString(
                        ListSerializer(
                            PersistedErrorLog.serializer()
                        ),
                        file.readText()
                    )
                    .map { it.toEntry(nextId.getAndIncrement()) }
            } catch (_: Exception) {
                file.delete()
                return@withLock
            }
            // 放在已有记录之后：本次运行产生的更值得先看到
            _entries.value = _entries.value + restored
        }
    }

    /**
     * 按保留策略清掉过期记录。
     *
     * 在 [restore] 之后调用一次即可，不另设定时器：日志的用途是回头
     * 排查，过期与否只在打开列表时才有意义，而应用启动必然早于查看。
     *
     * 返回删除的条数，便于界面给出反馈。
     */
    suspend fun purgeExpired(retention: LogRetention, now: Long = System.currentTimeMillis()): Int {
        mutex.withLock {
            val expired = expiredLogs(_entries.value, retention, now)
            if (expired.isEmpty()) return 0
            val expiredIds = expired.map { it.id }.toSet()
            _entries.value = _entries.value.filterNot { it.id in expiredIds }
            persistAll()
            return expired.size
        }
    }

    /**
     * 删除指定的若干条记录。
     *
     * 用于筛选后的批量删除——把当前筛选结果全部清掉是"只留下我关心的"
     * 最直接的做法，比逐条删实用。
     */
    suspend fun remove(ids: Set<Long>) {
        if (ids.isEmpty()) return
        mutex.withLock {
            _entries.value = _entries.value.filterNot { it.id in ids }
            persistAll()
        }
    }

    suspend fun clear() {
        mutex.withLock {
            _entries.value = emptyList()
            // 清空要连落盘的一起清，否则重进后又冒出来
            logFile()?.delete()
        }
    }

    /**
     * 把当前记录整体写入文件。
     *
     * 整体重写而非追加：条数上限不高，重写的开销可以忽略，换来的是
     * 不需要处理"文件里已有多少条"和截断时的部分写入。
     *
     * 现在每次请求都会走到这里（此前只有失败才写），写入频率明显提高。
     * 之所以仍可接受：条数上限限制了单次写入的体积，而这个函数在
     * mutex 内、IO 线程上执行，不会卡住请求本身。
     *
     * 调用方已持有 mutex。
     */
    private fun persistAll() {
        val file = logFile() ?: return
        try {
            val existing = _entries.value
                .take(MAX_PERSISTED_ERRORS)
                .map { it.toPersisted() }
            file.writeText(
                requestLogJson.encodeToString(
                    ListSerializer(
                        PersistedErrorLog.serializer()
                    ),
                    existing
                )
            )
        } catch (_: Exception) {
            // 写日志失败不该影响正在进行的请求
        }
    }

    private fun logFile(): File? = persistDir?.let { File(it, LOG_FILE_NAME) }

    internal companion object {
        /**
         * 保留条数上限。
         *
         * 每条记录含请求体与响应体，按单条 8KB 估算，100 条约 800KB，
         * 对移动端可接受；再多则查看时也难以定位。
         */
        const val MAX_ENTRIES = 100

        /** 失败记录的落盘文件名 */
        const val LOG_FILE_NAME = "request_errors.json"
    }
}
