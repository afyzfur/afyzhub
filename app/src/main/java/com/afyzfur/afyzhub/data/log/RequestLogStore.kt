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
 * 成功的记录只放内存，进程结束即丢失——它们的用途是"刚才这条为什么慢"，
 * 排查与发生在同一次会话内。
 *
 * 失败的记录额外落盘：报错原因常常要隔一阵才想起来查，那时应用早已
 * 重启过。此前全部只存内存，重进就没了，等于最需要的信息最容易丢。
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
            // 失败的额外落盘，成功的不落
            if (!entry.isSuccess) appendPersisted(entry)
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

    suspend fun clear() {
        mutex.withLock {
            _entries.value = emptyList()
            // 清空要连落盘的一起清，否则重进后又冒出来
            logFile()?.delete()
        }
    }

    /**
     * 追加一条失败记录到文件。
     *
     * 每次都整体重写而非追加写：条数上限只有 30，重写的开销可以忽略，
     * 换来的是不需要处理"文件里已有多少条"和截断时的部分写入。
     *
     * 调用方已持有 mutex。
     */
    private fun appendPersisted(entry: RequestLogEntry) {
        val file = logFile() ?: return
        try {
            val existing = _entries.value
                .filterNot { it.isSuccess }
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
