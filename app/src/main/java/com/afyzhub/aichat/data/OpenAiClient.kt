package com.afyzhub.aichat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class OpenAiClient {
        private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var activeCall: Call? = null

    suspend fun streamChat(
        config: ApiConfig,
        apiKey: String,
        messages: List<ChatMessage>,
        onDelta: suspend (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        require(config.baseUrl.startsWith("https://")) {
            "为保护 API Key，仅允许使用 HTTPS 地址"
        }
        require(config.model.isNotBlank()) { "模型 ID 不能为空" }

        val requestMessages = JSONArray()
        if (config.systemPrompt.isNotBlank()) {
            requestMessages.put(
                JSONObject()
                    .put("role", "system")
                    .put("content", config.systemPrompt)
            )
        }
        messages.forEach { message ->
            requestMessages.put(
                JSONObject()
                    .put("role", message.role)
                    .put("content", message.content)
            )
        }

        val payload = JSONObject()
            .put("model", config.model)
            .put("messages", requestMessages)
            .put("stream", true)
            .put("temperature", config.temperature.toDouble())
            .put("max_tokens", config.maxTokens)

        val request = Request.Builder()
            .url(config.baseUrl.trim().trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "text/event-stream")
            .post(
                payload.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
            )
            .build()

        val call = httpClient.newCall(request)
        activeCall = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(
                        when (response.code) {
                            401 -> "认证失败，请检查 API Key"
                            403 -> "服务拒绝访问（403）"
                            404 -> "接口不存在，请检查 Base URL"
                            429 -> "请求过于频繁或账户额度不足（429）"
                            in 500..599 -> "服务暂时不可用（HTTP ${response.code}）"
                            else -> "请求失败（HTTP ${response.code}）"
                        }
                    )
                }

                val reader = response.body?.charStream()?.buffered()
                    ?: throw IOException("服务返回了空响应")
                                while (true) {
                    coroutineContext.ensureActive()
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]" || data.contains("[DONE]")) break
                    if (data.isBlank()) continue

                    try {
                        val json = JSONObject(data)
                val choice = json.optJSONArray("choices")?.optJSONObject(0)

                        // 尝试 delta.content（标准 SSE 流式格式）
                        var delta = choice?.optJSONObject("delta")?.optString("content").orEmpty()

                        // 兼容部分非标准服务：message.content 或顶层 text 字段
                        if (delta.isEmpty()) {
                            delta = choice?.optJSONObject("message")?.optString("content").orEmpty()
                        }
                        if (delta.isEmpty()) {
                            delta = json.optString("text").orEmpty()
                        }

                        if (delta.isNotEmpty() && delta != "null") {
                            onDelta(delta)
                        }
                    } catch (_: Exception) {
                        // 忽略单个损坏事件，继续读取后续 SSE 数据。
                    }
                }
            }
        } finally {
            activeCall = null
        }
    }

    fun cancel() {
        activeCall?.cancel()
        activeCall = null
    }
}