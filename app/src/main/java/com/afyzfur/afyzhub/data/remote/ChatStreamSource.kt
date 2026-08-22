package com.afyzfur.afyzhub.data.remote

import com.afyzfur.afyzhub.data.remote.dto.ChatRequest
import com.afyzfur.afyzhub.data.remote.dto.ChatStreamChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** 流式对话数据源，抽为接口以便替换实现与测试。 */
interface ChatStreamSource {
    /**
     * 发起流式请求，逐段产出增量文本。
     *
     * 失败时抛出异常，由上层转为消息的失败状态。
     */
    fun streamCompletion(request: ChatRequest): Flow<String>
}

/**
 * 基于 OkHttp 的 SSE 实现。
 *
 * Retrofit 的 suspend 接口会等待完整响应体，无法边收边显示，
 * 因此这里直接用 OkHttp 逐行读取 `text/event-stream`。
 * 鉴权与实际地址由 [AuthInterceptor] 注入。
 */
class OkHttpChatStreamSource(
    private val client: OkHttpClient,
    private val json: Json
) : ChatStreamSource {

    override fun streamCompletion(request: ChatRequest): Flow<String> = flow {
        val payload = json.encodeToString(ChatRequest.serializer(), request.copy(stream = true))

        // host 由拦截器改写，这里仅作占位；路径需与非流式接口一致。
        val httpRequest = Request.Builder()
            .url(PLACEHOLDER_URL)
            .header("Accept", "text/event-stream")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string()?.take(200).orEmpty()
                throw IOException(buildString {
                    append("请求失败（HTTP ${response.code}）")
                    if (detail.isNotBlank()) append("：$detail")
                })
            }

            val source = response.body?.source() ?: throw IOException("响应体为空")

            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank() || !line.startsWith(DATA_PREFIX)) continue

                val data = line.removePrefix(DATA_PREFIX).trim()
                if (data == DONE_MARKER) break

                parseDelta(data)?.takeIf { it.isNotEmpty() }?.let { emit(it) }
            }
        }
    }.flowOn(Dispatchers.IO)

    /** 单个数据块解析失败不应中断整段回复，返回 null 表示跳过。 */
    private fun parseDelta(data: String): String? = try {
        json.decodeFromString(ChatStreamChunk.serializer(), data)
            .choices.firstOrNull()?.delta?.content
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val PLACEHOLDER_URL = "https://placeholder.invalid/v1/chat/completions"
        const val DATA_PREFIX = "data:"
        const val DONE_MARKER = "[DONE]"
    }
}
