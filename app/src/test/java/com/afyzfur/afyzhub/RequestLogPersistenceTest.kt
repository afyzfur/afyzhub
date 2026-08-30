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
    fun `成功记录也落盘`() = runTest {
        // 0.3.2 起改为成功也落盘。此前只存失败，理由是"成功的没人回头看"，
        // 但那让"昨天那次为什么慢""上周用的是哪个模型"在重启后无从查证。
        // 这条断言方向与旧版相反，是有意推翻的。
        val dir = tempFolder.newFolder()
        val first = RequestLogStore(persistDir = dir)
        first.record(entry(1, statusCode = 200))

        val second = RequestLogStore(persistDir = dir)
        second.restore()

        assertEquals(1, second.entries.value.size)
        assertTrue("成功记录读回后仍应判为成功", second.entries.value.first().isSuccess)
    }

    @Test
    fun `落盘保留提供商与模型`() = runTest {
        // 这两个字段是筛选的依据，落盘丢掉就等于重启后筛不了
        val dir = tempFolder.newFolder()
        RequestLogStore(persistDir = dir).record(entry(1, statusCode = 200))

        val second = RequestLogStore(persistDir = dir)
        second.restore()

        val restored = second.entries.value.first()
        assertEquals("openai", restored.provider)
        assertEquals("gpt-4o-mini", restored.model)
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
    fun `旧版本落盘的文件仍能读出而非被当作损坏删掉`() = runTest {
        // 这是本轮改动最危险的一条路径。restore() 遇到解析失败会删掉
        // 整个文件，所以新增字段若没有默认值，升级一次就丢光历史记录，
        // 而且不报错——用户几天后才发现列表空了。
        // 这里模拟 0.3.2 之前写下的内容：没有 provider / model 两个键。
        val dir = tempFolder.newFolder()
        java.io.File(dir, RequestLogStore.LOG_FILE_NAME).writeText(
            """
            [
              {
                "startedAt": 1699999999000,
                "host": "api.example.com",
                "method": "POST",
                "url": "https://api.example.com/v1/chat",
                "statusCode": 401,
                "responseBody": "unauthorized",
                "error": "401 Unauthorized",
                "durationMs": 245
              }
            ]
            """.trimIndent()
        )

        val store = RequestLogStore(persistDir = dir)
        store.restore()

        assertEquals("旧文件必须仍能读出", 1, store.entries.value.size)
        val e = store.entries.value.first()
        assertEquals("401 Unauthorized", e.error)
        // 缺失的字段退化为 null，界面显示为未知即可
        assertEquals(null, e.provider)
        assertEquals(null, e.model)
    }

    @Test
    fun `关闭记录后连内存也不记`() = runTest {
        // 只是不落盘的话，"已关闭"却还能在列表里看到本次运行的记录，
        // 与开关的字面意思不符
        val dir = tempFolder.newFolder()
        val store = RequestLogStore(persistDir = dir)
        store.enabled = false
        store.record(entry(1, error = "boom", statusCode = 500))

        assertTrue("关闭后内存里不该有记录", store.entries.value.isEmpty())

        val second = RequestLogStore(persistDir = dir)
        second.restore()
        assertTrue("关闭后也不该落盘", second.entries.value.isEmpty())
    }

    @Test
    fun `重新开启后恢复记录`() = runTest {
        // 开关要能来回切，而不是关一次就永久失效
        val store = RequestLogStore(persistDir = tempFolder.newFolder())
        store.enabled = false
        store.record(entry(1, error = "dropped", statusCode = 500))
        store.enabled = true
        store.record(entry(2, error = "kept", statusCode = 500))

        assertEquals(1, store.entries.value.size)
        assertEquals("kept", store.entries.value.first().error)
    }

    @Test
    fun `关闭不影响已有记录`() = runTest {
        // 关闭的语义是"不再记新的"，而非"清掉旧的"——后者应当只由
        // 手动删除触发
        val store = RequestLogStore(persistDir = tempFolder.newFolder())
        store.record(entry(1, error = "existing", statusCode = 500))
        store.enabled = false

        assertEquals(1, store.entries.value.size)
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
