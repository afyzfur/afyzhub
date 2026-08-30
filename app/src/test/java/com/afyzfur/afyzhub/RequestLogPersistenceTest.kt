package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.data.log.RequestLogEntry
import com.afyzfur.afyzhub.data.log.RequestLogStore
import com.afyzfur.afyzhub.data.log.toPersisted
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 失败请求记录落盘的测试。
 *
 * 这块的价值在于"重启后还能查到报错"，而重启无法在单测里模拟——
 * 用两个 store 实例共享同一目录来近似：第二个实例相当于新进程。
 */
class RequestLogPersistenceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun entry(
        id: Long,
        error: String? = null,
        statusCode: Int? = 200,
        body: String? = null
    ) = RequestLogEntry(
        id = id,
        startedAt = 1_000L + id,
        host = "api.example.com",
        provider = "openai",
        model = "gpt-4o-mini",
        method = "POST",
        url = "https://api.example.com/v1/chat",
        headers = emptyMap(),
        requestBody = null,
        statusCode = statusCode,
        responseBody = body,
        error = error,
        durationMs = 120
    )

    @Test
    fun `失败记录在新实例中可读回`() = runTest {
        val dir = tempFolder.newFolder()
        val first = RequestLogStore(persistDir = dir)
        first.record(entry(1, error = "401 Unauthorized", statusCode = 401))

        // 相当于重启后的新进程
        val second = RequestLogStore(persistDir = dir)
        second.restore()

        assertEquals(1, second.entries.value.size)
        assertEquals("401 Unauthorized", second.entries.value.first().error)
    }

    @Test
    fun `成功记录不落盘`() = runTest {
        val dir = tempFolder.newFolder()
        val first = RequestLogStore(persistDir = dir)
        first.record(entry(1, statusCode = 200))

        val second = RequestLogStore(persistDir = dir)
        second.restore()

        assertTrue("成功的请求没人回头看，不该占用存储", second.entries.value.isEmpty())
    }

    @Test
    fun `读回的记录标记为上次运行`() = runTest {
        val dir = tempFolder.newFolder()
        RequestLogStore(persistDir = dir).record(
            entry(1, error = "timeout", statusCode = null)
        )

        val second = RequestLogStore(persistDir = dir)
        second.restore()

        assertTrue(second.entries.value.first().restored)
    }

    @Test
    fun `本次运行的记录未被标记`() = runTest {
        val store = RequestLogStore(persistDir = tempFolder.newFolder())
        store.record(entry(1, error = "boom", statusCode = 500))

        assertFalse(store.entries.value.first().restored)
    }

    @Test
    fun `清空同时清掉落盘内容`() = runTest {
        val dir = tempFolder.newFolder()
        val first = RequestLogStore(persistDir = dir)
        first.record(entry(1, error = "boom", statusCode = 500))
        first.clear()

        val second = RequestLogStore(persistDir = dir)
        second.restore()

        assertTrue("清空后重进不该又冒出来", second.entries.value.isEmpty())
    }

    @Test
    fun `文件损坏时不抛异常`() = runTest {
        val dir = tempFolder.newFolder()
        java.io.File(dir, RequestLogStore.LOG_FILE_NAME).writeText("{ 不是合法 JSON")

        val store = RequestLogStore(persistDir = dir)
        // 不该抛出——日志读不出来不影响应用启动
        store.restore()

        assertTrue(store.entries.value.isEmpty())
    }

    @Test
    fun `未配置目录时退化为纯内存`() = runTest {
        val store = RequestLogStore(persistDir = null)
        store.record(entry(1, error = "boom", statusCode = 500))
        store.restore()

        // 记录仍在内存里，restore 什么都不做且不崩
        assertEquals(1, store.entries.value.size)
    }

    @Test
    fun `超长响应体落盘时被截断`() {
        val long = "x".repeat(5000)
        val persisted = entry(1, error = "boom", statusCode = 500, body = long)
            .toPersisted()

        assertTrue(
            "落盘体积需要控制",
            (persisted.responseBody?.length ?: 0) < long.length
        )
        assertTrue(persisted.responseBody!!.contains("已截断"))
    }
}
