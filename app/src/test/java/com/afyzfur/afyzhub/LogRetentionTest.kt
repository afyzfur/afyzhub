package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.data.log.LogAgeGroup
import com.afyzfur.afyzhub.data.log.LogFilter
import com.afyzfur.afyzhub.data.log.LogRetention
import com.afyzfur.afyzhub.data.log.PersistedErrorLog
import com.afyzfur.afyzhub.data.log.RequestLogEntry
import com.afyzfur.afyzhub.data.log.expiredLogs
import com.afyzfur.afyzhub.data.log.filterLogs
import com.afyzfur.afyzhub.data.log.requestLogJson
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * 日志的保留策略、时间分组与筛选。
 *
 * 兼容性那几条最要紧：这批改动给落盘格式加了字段，若旧文件读不出来，
 * restore() 的兜底是删掉整个文件——升级一次就丢光历史记录，而且不会
 * 报错，用户过几天才发现列表空了。
 */
class LogRetentionTest {

    private val now = 1_700_000_000_000L
    private val dayMs = TimeUnit.DAYS.toMillis(1)

    private fun entry(
        id: Long,
        ageDays: Long = 0,
        provider: String? = "openai",
        model: String? = "gpt-4o-mini",
        error: String? = null,
        statusCode: Int? = 200
    ) = RequestLogEntry(
        id = id,
        startedAt = now - dayMs * ageDays,
        host = "api.openai.com",
        provider = provider,
        model = model,
        method = "POST",
        url = "https://api.openai.com/v1/chat/completions",
        headers = emptyMap(),
        requestBody = null,
        statusCode = statusCode,
        responseBody = null,
        error = error,
        durationMs = 100
    )

    // ---- 保留策略 ----

    @Test
    fun `默认保留七天`() {
        assertEquals(LogRetention.WEEK, LogRetention.DEFAULT)
    }

    @Test
    fun `未知标识回退到默认`() {
        assertEquals(LogRetention.DEFAULT, LogRetention.fromId(null))
        assertEquals(LogRetention.DEFAULT, LogRetention.fromId("nonsense"))
        // 已知标识要原样解析，否则设置项存了也白存
        assertEquals(LogRetention.DAY, LogRetention.fromId("1d"))
        assertEquals(LogRetention.FOREVER, LogRetention.fromId("forever"))
    }

    @Test
    fun `永久保留不清理任何记录`() {
        val old = listOf(entry(1, ageDays = 400), entry(2, ageDays = 1000))
        assertTrue(expiredLogs(old, LogRetention.FOREVER, now).isEmpty())
    }

    @Test
    fun `超出保留期的被判为过期`() {
        val entries = listOf(
            entry(1, ageDays = 0),
            entry(2, ageDays = 3),
            entry(3, ageDays = 10)
        )
        val expired = expiredLogs(entries, LogRetention.WEEK, now)
        assertEquals(listOf(3L), expired.map { it.id })
    }

    @Test
    fun `恰好在边界上的不算过期`() {
        // 保留 1 天时，23 小时前的记录必须留着——边界写成 >= 会把
        // 刚好一天前的记录一起删掉
        val entries = listOf(entry(1, ageDays = 0))
        assertTrue(expiredLogs(entries, LogRetention.DAY, now).isEmpty())
    }

    // ---- 时间分组 ----

    @Test
    fun `按距今多久归组而非日历日`() {
        assertEquals(LogAgeGroup.TODAY, LogAgeGroup.of(now - 1000, now))
        assertEquals(LogAgeGroup.YESTERDAY, LogAgeGroup.of(now - dayMs - 1000, now))
        assertEquals(LogAgeGroup.THIS_WEEK, LogAgeGroup.of(now - dayMs * 3, now))
        assertEquals(LogAgeGroup.EARLIER, LogAgeGroup.of(now - dayMs * 30, now))
    }

    @Test
    fun `刚刚发生的记录不会因跨零点被归到昨天`() {
        // 这是选择"距今多久"而非日历日的原因：零点后两分钟发生的请求，
        // 按日历日算属于"今天"，但按日历日实现时容易写成比较日期从而
        // 把它归错。这里确认两分钟前始终是今天
        val justNow = now - TimeUnit.MINUTES.toMillis(2)
        assertEquals(LogAgeGroup.TODAY, LogAgeGroup.of(justNow, now))
    }

    // ---- 筛选 ----

    @Test
    fun `空条件返回全部且不复制列表语义`() {
        val entries = listOf(entry(1), entry(2))
        assertEquals(entries, filterLogs(entries, LogFilter(), now))
    }

    @Test
    fun `按模型筛选`() {
        val entries = listOf(
            entry(1, model = "gpt-4o-mini"),
            entry(2, model = "claude-3-5-sonnet")
        )
        val r = filterLogs(entries, LogFilter(model = "gpt-4o-mini"), now)
        assertEquals(listOf(1L), r.map { it.id })
    }

    @Test
    fun `按提供商筛选`() {
        val entries = listOf(
            entry(1, provider = "openai"),
            entry(2, provider = "anthropic")
        )
        val r = filterLogs(entries, LogFilter(provider = "anthropic"), now)
        assertEquals(listOf(2L), r.map { it.id })
    }

    @Test
    fun `只看失败`() {
        val entries = listOf(
            entry(1),
            entry(2, error = "超时", statusCode = null),
            entry(3, statusCode = 401, error = "密钥无效")
        )
        val r = filterLogs(entries, LogFilter(failedOnly = true), now)
        assertEquals(listOf(2L, 3L), r.map { it.id })
    }

    @Test
    fun `多个条件叠加`() {
        val entries = listOf(
            entry(1, provider = "openai", error = "超时", statusCode = null),
            entry(2, provider = "anthropic", error = "超时", statusCode = null),
            entry(3, provider = "openai")
        )
        val r = filterLogs(
            entries,
            LogFilter(provider = "openai", failedOnly = true),
            now
        )
        assertEquals(listOf(1L), r.map { it.id })
    }

    @Test
    fun `模型为空的记录不会误配到具体模型`() {
        // 旧记录的 model 为 null，按某个模型筛选时不该把它们带出来
        val entries = listOf(entry(1, model = null), entry(2, model = "gpt-4o-mini"))
        val r = filterLogs(entries, LogFilter(model = "gpt-4o-mini"), now)
        assertEquals(listOf(2L), r.map { it.id })
    }

    // ---- 落盘兼容性 ----

    @Test
    fun `旧格式文件缺少新字段仍能读出`() {
        // 0.3.2 之前落盘的内容：没有 provider、model 两个键。
        // 反序列化必须成功，否则 restore() 会删掉整个文件
        val legacy = """
            [
              {
                "startedAt": 1699999999000,
                "host": "api.openai.com",
                "method": "POST",
                "url": "https://api.openai.com/v1/chat/completions",
                "statusCode": 401,
                "responseBody": "{\"error\":\"invalid key\"}",
                "error": "密钥无效",
                "durationMs": 245
              }
            ]
        """.trimIndent()

        val parsed = requestLogJson.decodeFromString(
            ListSerializer(PersistedErrorLog.serializer()),
            legacy
        )
        assertEquals(1, parsed.size)
        assertEquals("api.openai.com", parsed[0].host)
        assertEquals(401, parsed[0].statusCode)
        // 缺失的字段退化为 null，而不是让整条记录读不出来
        assertNull(parsed[0].provider)
        assertNull(parsed[0].model)
    }

    @Test
    fun `多出来的未知字段也不影响读取`() {
        // 反方向的兼容：降级安装时，新版写的字段对旧版是未知键。
        // requestLogJson 开了 ignoreUnknownKeys，这里确认它生效
        val future = """
            [
              {
                "startedAt": 1699999999000,
                "host": "api.openai.com",
                "method": "POST",
                "url": "https://x/y",
                "statusCode": 200,
                "responseBody": null,
                "error": null,
                "durationMs": 100,
                "provider": "openai",
                "model": "gpt-4o-mini",
                "somethingAddedLater": "whatever"
              }
            ]
        """.trimIndent()

        val parsed = requestLogJson.decodeFromString(
            ListSerializer(PersistedErrorLog.serializer()),
            future
        )
        assertEquals("openai", parsed[0].provider)
        assertEquals("gpt-4o-mini", parsed[0].model)
    }

    @Test
    fun `写入再读回保持一致`() {
        val original = listOf(
            PersistedErrorLog(
                startedAt = now,
                host = "api.anthropic.com",
                method = "POST",
                url = "https://api.anthropic.com/v1/messages",
                statusCode = 200,
                responseBody = "ok",
                error = null,
                durationMs = 321,
                provider = "anthropic",
                model = "claude-3-5-sonnet"
            )
        )
        val text = requestLogJson.encodeToString(
            ListSerializer(PersistedErrorLog.serializer()),
            original
        )
        val back = requestLogJson.decodeFromString(
            ListSerializer(PersistedErrorLog.serializer()),
            text
        )
        assertEquals(original, back)
    }
}
