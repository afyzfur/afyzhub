package com.afyzfur.afyzhub.data.remote.provider

import com.afyzfur.afyzhub.data.log.RequestLogEntry
import com.afyzfur.afyzhub.data.log.RequestLogStore
import com.afyzfur.afyzhub.data.log.redactHeaders
import com.afyzfur.afyzhub.data.log.redactUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/**
 * 三家提供商共用的 HTTP 传输层。
 *
 * 负责地址拼接、POST 发送和 SSE 逐行读取；
 * 具体的请求体格式与数据块解析由各 provider 决定。
 */
class HttpTransport(
    private val client: OkHttpClient,
    private val logStore: RequestLogStore
) : Transport {

    private val jsonMediaType = "application/json".toMediaType()

    /**
     * OkHttp 的 execute() 是阻塞调用，必须切到 IO 线程执行。
     * 否则从主线程发起会抛 NetworkOnMainThreadException。
     */
    override suspend fun getForText(
        baseUrl: String,
        path: String,
        headers: Map<String, String>,
        query: Map<String, String>
    ): String = withContext(Dispatchers.IO) {
        val url = resolveUrl(baseUrl, path, query)
        val startedAt = System.currentTimeMillis()
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .get()
            .build()

        // 标记而非比对错误文案：拿到响应后无论成败都已记过日志，
        // catch 里只需处理"请求根本没发出去"的情况
        var logged = false
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val failure = if (response.isSuccessful) {
                    null
                } else {
                    describeFailure(response.code, text)
                }
                log(startedAt, "GET", url, headers, null, response.code, text, failure)
                logged = true
                if (failure != null) throw IOException(failure)
                text
            }
        } catch (e: IOException) {
            // DNS、超时、连接重置等没有状态码可记
            if (!logged) {
                log(startedAt, "GET", url, headers, null, null, null, e.message ?: "网络错误")
            }
            throw e
        }
    }

    override suspend fun postForText(
        baseUrl: String,
        path: String,
        headers: Map<String, String>,
        body: String,
        query: Map<String, String>
    ): String = withContext(Dispatchers.IO) {
        val url = resolveUrl(baseUrl, path, query)
        val startedAt = System.currentTimeMillis()
        val request = buildRequest(url, headers, body)

        var logged = false
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val failure = if (response.isSuccessful) {
                    null
                } else {
                    describeFailure(response.code, text)
                }
                log(startedAt, "POST", url, headers, body, response.code, text, failure)
                logged = true
                if (failure != null) throw IOException(failure)
                text
            }
        } catch (e: IOException) {
            if (!logged) {
                log(startedAt, "POST", url, headers, body, null, null, e.message ?: "网络错误")
            }
            throw e
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
        val url = resolveUrl(baseUrl, path, query)
        val sseHeaders = headers + mapOf("Accept" to "text/event-stream")
        val startedAt = System.currentTimeMillis()
        val request = buildRequest(url, sseHeaders, body)

        // 流式响应体逐行到达，累积起来供日志记录。
        // 只留前 MAX_BODY_CHARS 字符，长回复不必全存
        val collected = StringBuilder()
        var statusCode: Int? = null
        var logged = false

        try {
            client.newCall(request).execute().use { response ->
                statusCode = response.code
                if (!response.isSuccessful) {
                    val detail = response.body?.string().orEmpty()
                    val failure = describeFailure(response.code, detail)
                    log(startedAt, "POST", url, sseHeaders, body, response.code, detail, failure)
                    logged = true
                    throw IOException(failure)
                }
                val source = response.body?.source() ?: throw IOException("响应体为空")

                // 已收到过数据后，把读超时缩短为空闲超时。
                //
                // 部分中转服务在回复结束后既不发 [DONE] 也不关闭连接，
                // readUtf8Line() 会一直阻塞到 OkHttp 的 readTimeout（5 分钟），
                // 期间消息状态停在「发送中」。首字节前不能用这个短超时——
                // 模型思考阶段本身可能几十秒无输出。
                var receivedAny = false

                while (true) {
                    if (receivedAny) {
                        source.timeout().timeout(IDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    }

                    val line = try {
                        source.readUtf8Line() ?: break
                    } catch (e: InterruptedIOException) {
                        // 空闲超时视为流正常结束，而非错误——
                        // 已收到的内容是完整回复，只是服务端没发结束标记
                        break
                    }

                    if (line.isBlank()) continue
                    if (!line.startsWith(DATA_PREFIX)) continue

                    val payload = line.removePrefix(DATA_PREFIX).trim()
                    if (payload.isEmpty()) continue
                    // 收到结束标记即主动退出，不必等连接关闭
                    if (payload == DONE_MARKER) break

                    receivedAny = true
                    if (collected.length < MAX_BODY_CHARS) {
                        collected.append(payload).append('\n')
                    }
                    emit(payload)
                }
            }
            log(startedAt, "POST", url, sseHeaders, body, statusCode, collected.toString(), null)
            logged = true
        } catch (e: Exception) {
            // 也捕获非 IOException：流式过程中的解析异常同样需要留痕
            if (!logged) {
                log(
                    startedAt,
                    "POST",
                    url,
                    sseHeaders,
                    body,
                    statusCode,
                    collected.toString().takeIf { it.isNotEmpty() },
                    e.message ?: "流式中断"
                )
            }
            throw e
        }
    }.flowOn(Dispatchers.IO)

    /** 接收已解析的 URL，因为调用方还要拿它写日志。 */
    private fun buildRequest(
        url: HttpUrl,
        headers: Map<String, String>,
        body: String
    ): Request {
        return Request.Builder()
            .url(url)
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

    /**
     * 写入一条请求日志。
     *
     * 密钥在此处脱敏，[RequestLogStore] 里存的已是安全内容——
     * 脱敏放在入口而非展示层，避免今后新增展示路径时漏掉。
     */
    private suspend fun log(
        startedAt: Long,
        method: String,
        url: HttpUrl,
        headers: Map<String, String>,
        requestBody: String?,
        statusCode: Int?,
        responseBody: String?,
        error: String?
    ) {
        logStore.record(
            RequestLogEntry(
                id = logStore.newId(),
                startedAt = startedAt,
                host = url.host,
                method = method,
                url = redactUrl(url.toString()),
                headers = redactHeaders(headers),
                requestBody = requestBody?.take(MAX_BODY_CHARS),
                statusCode = statusCode,
                responseBody = responseBody?.take(MAX_BODY_CHARS),
                error = error,
                durationMs = System.currentTimeMillis() - startedAt
            )
        )
    }

    private companion object {
        const val DATA_PREFIX = "data:"
        const val DONE_MARKER = "[DONE]"

        /**
         * 流式响应的空闲超时（秒）。
         *
         * 仅在已收到至少一个数据块后生效，用于兜住不发结束标记也不关闭
         * 连接的服务端。取 20 秒：正常输出的块间隔在百毫秒级，
         * 而模型中途长时间停顿的情况少见。
         */
        const val IDLE_TIMEOUT_SECONDS = 20L

        /**
         * 单个请求体/响应体的保留字符数。
         *
         * 长对话的请求体可达数十 KB，全量保留会让 100 条日志占用过多内存。
         * 排查配置问题时开头部分已足够——错误信息通常在响应体前部。
         */
        const val MAX_BODY_CHARS = 4000
    }
}
