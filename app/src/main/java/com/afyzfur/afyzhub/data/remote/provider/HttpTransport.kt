package com.afyzfur.afyzhub.data.remote.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * 三家提供商共用的 HTTP 传输层。
 *
 * 负责地址拼接、POST 发送和 SSE 逐行读取；
 * 具体的请求体格式与数据块解析由各 provider 决定。
 */
class HttpTransport(private val client: OkHttpClient) : Transport {

    private val jsonMediaType = "application/json".toMediaType()

    override suspend fun getForText(
        baseUrl: String,
        path: String,
        headers: Map<String, String>,
        query: Map<String, String>
    ): String {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, path, query))
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(describeFailure(response.code, text))
            }
            return text
        }
    }

    override suspend fun postForText(
        baseUrl: String,
        path: String,
        headers: Map<String, String>,
        body: String,
        query: Map<String, String>
    ): String {
        val request = buildRequest(baseUrl, path, headers, body, query)
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(describeFailure(response.code, text))
            }
            return text
        }
    }

    /** 空行、注释行和心跳会被过滤。 */
    override fun postForSse(
        baseUrl: String,
        path: String,
        headers: Map<String, String>,
        body: String,
        query: Map<String, String>
    ): Flow<String> = flow {
        val request = buildRequest(
            baseUrl,
            path,
            headers + mapOf("Accept" to "text/event-stream"),
            body,
            query
        )

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string().orEmpty()
                throw IOException(describeFailure(response.code, detail))
            }
            val source = response.body?.source() ?: throw IOException("响应体为空")

            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                if (!line.startsWith(DATA_PREFIX)) continue

                val payload = line.removePrefix(DATA_PREFIX).trim()
                if (payload.isEmpty() || payload == DONE_MARKER) continue
                emit(payload)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequest(
        baseUrl: String,
        path: String,
        headers: Map<String, String>,
        body: String,
        query: Map<String, String>
    ): Request {
        return Request.Builder()
            .url(resolveUrl(baseUrl, path, query))
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .post(body.toRequestBody(jsonMediaType))
            .build()
    }

    /**
     * 拼接完整地址。
     *
     * 保留 baseUrl 中的路径前缀，以支持形如
     * `https://relay.example.com/openai/` 的中转服务。
     */
    private fun resolveUrl(baseUrl: String, path: String, query: Map<String, String>): HttpUrl {
        val base = baseUrl.toHttpUrlOrNull()
            ?: throw IOException("API 地址无效：$baseUrl")

        val builder = base.newBuilder()
        path.trim('/').split('/').filter { it.isNotEmpty() }.forEach {
            builder.addPathSegment(it)
        }
        query.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build()
    }

    private fun describeFailure(code: Int, body: String): String {
        val detail = body.take(300).trim()
        return if (detail.isEmpty()) {
            "请求失败（HTTP $code）"
        } else {
            "请求失败（HTTP $code）：$detail"
        }
    }

    private companion object {
        const val DATA_PREFIX = "data:"
        const val DONE_MARKER = "[DONE]"
    }
}
