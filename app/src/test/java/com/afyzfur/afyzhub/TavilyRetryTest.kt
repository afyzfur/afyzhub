package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.data.remote.provider.Transport
import com.afyzfur.afyzhub.data.remote.provider.WebSearchService
import com.afyzfur.afyzhub.data.log.RequestLogContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TavilyRetryTest {
    @Test fun retriesIrrelevantResultsWithinTheSameSendAndReadsRawPage() = runTest {
        val queries = mutableListOf<String>()
        val transport = object : Transport {
            override suspend fun getForText(baseUrl: String, path: String, headers: Map<String, String>, query: Map<String, String>, logContext: RequestLogContext): String = error("No silent engine fallback")
            override fun postForSse(baseUrl: String, path: String, headers: Map<String, String>, body: String, query: Map<String, String>, logContext: RequestLogContext): Flow<String> = emptyFlow()
            override suspend fun postForText(baseUrl: String, path: String, headers: Map<String, String>, body: String, query: Map<String, String>, logContext: RequestLogContext): String {
                assertEquals("https://api.tavily.com", baseUrl)
                assertTrue(body.contains("advanced"))
                assertTrue(body.contains("\"include_raw_content\":true"))
                queries += body
                return if (queries.size == 1) """{"results":[{"title":"佛山天气","content":"晴天","url":"https://example.test/fs"}]}"""
                else """{"results":[{"title":"重庆天气预报","content":"重庆气温","raw_content":"重庆今日小雨，13至17℃","url":"https://example.test/cq"}]}"""
            }
        }
        val result = WebSearchService(transport).search("重庆今日天气", engineId = "tavily", tavilyApiKey = "test")
        assertEquals(2, queries.size)
        assertTrue(queries.last().contains(java.time.LocalDate.now().toString()))
        assertEquals(1, result.size)
        assertTrue(result.single().snippet.contains("重庆今日小雨"))
    }
}
