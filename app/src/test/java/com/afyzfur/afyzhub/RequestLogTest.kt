package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.data.log.RequestLogEntry
import com.afyzfur.afyzhub.data.log.RequestLogStore
import com.afyzfur.afyzhub.data.log.redactHeaders
import com.afyzfur.afyzhub.data.log.redactUrl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 脱敏逻辑的测试。
 *
 * 这里的每条断言都对应一种凭证泄露途径，回归成本远低于事故成本。
 */
class RedactionTest {

    @Test
    fun `Authorization 头保留 Bearer 前缀并遮蔽密钥`() {
        val result = redactHeaders(
            mapOf("Authorization" to "Bearer sk-proj-abcdefghijklmnop")
        )

        val value = result["Authorization"]!!
        assertTrue("应保留 Bearer 前缀", value.startsWith("Bearer "))
        assertFalse("不应包含完整密钥", value.contains("abcdefghijklmnop"))
        assertEquals("Bearer sk-p***", value)
    }

    @Test
    fun `三家的鉴权头都被覆盖`() {
        val result = redactHeaders(
            mapOf(
                "x-api-key" to "sk-ant-1234567890",
                "x-goog-api-key" to "AIzaSyABCDEFGH",
                "api-key" to "azure-key-value"
            )
        )

        result.forEach { (name, value) ->
            assertTrue("$name 未被遮蔽：$value", value.endsWith("***"))
        }
    }

    @Test
    fun `非敏感头原样保留`() {
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Accept" to "text/event-stream"
        )

        assertEquals(headers, redactHeaders(headers))
    }

    @Test
    fun `过短的密钥整体遮蔽而非保留前缀`() {
        // 保留前 4 位对 6 位密钥等于泄露大半
        val result = redactHeaders(mapOf("x-api-key" to "abc123"))
        assertEquals("***", result["x-api-key"])
    }

    @Test
    fun `URL 查询参数中的密钥被遮蔽`() {
        val result = redactUrl(
            "https://generativelanguage.googleapis.com/v1beta/models?key=AIzaSecret&alt=sse"
        )

        assertFalse(result.contains("AIzaSecret"))
        assertTrue("非敏感参数应保留", result.contains("alt=sse"))
    }

    @Test
    fun `无查询参数的 URL 原样返回`() {
        val url = "https://api.openai.com/v1/chat/completions"
        assertEquals(url, redactUrl(url))
    }
}

class RequestLogStoreTest {

    private fun entry(id: Long) = RequestLogEntry(
        id = id,
        startedAt = id,
        host = "api.example.com",
        provider = "openai",
        model = "gpt-4o-mini",
        method = "POST",
        url = "https://api.example.com/v1/chat",
        headers = emptyMap(),
        requestBody = null,
        statusCode = 200,
        responseBody = null,
        error = null,
        durationMs = 10
    )

    @Test
    fun `最新记录排在最前`() = runTest {
        val store = RequestLogStore()
        store.record(entry(1))
        store.record(entry(2))

        assertEquals(listOf(2L, 1L), store.entries.value.map { it.id })
    }

    @Test
    fun `超出上限时丢弃最旧的记录`() = runTest {
        val store = RequestLogStore()
        // 上限为 100，写入 105 条后应只剩最新的 100 条
        repeat(105) { store.record(entry(it.toLong() + 1)) }

        val ids = store.entries.value.map { it.id }
        assertEquals(100, ids.size)
        assertEquals(105L, ids.first())
        assertEquals(6L, ids.last())
    }

    @Test
    fun `清空后列表为空`() = runTest {
        val store = RequestLogStore()
        store.record(entry(1))
        store.clear()

        assertTrue(store.entries.value.isEmpty())
    }

    @Test
    fun `id 递增不重复`() {
        val store = RequestLogStore()
        val ids = (1..50).map { store.newId() }
        assertEquals(ids.size, ids.toSet().size)
    }
}
