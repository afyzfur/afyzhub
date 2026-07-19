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
            .put("stream", config.useStream)
            .put("temperature", config.temperature.toDouble())
            .put("max_tokens", config.maxTokens)

        val accept = if (config.useStream) "text/event-stream" else "application/json"
        val request = Request.Builder()
            .url(config.baseUrl.trim().trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", accept)
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

                                val body = response.body?.string() ?: throw IOException("服务返回了空响应")

                // 自动识别响应格式：无论 stream 参数如何，按实际内容决定解析方式
                val trimmed = body.trimStart()
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    // 响应是 JSON（非流式或API忽略了 stream 参数）
                    try {
                        val json = JSONObject(trimmed)
                        // 先检查是否是错误响应
                                                val errMsg = json.optString("error").takeIf { it.isNotBlank() && it != "null" }
                            ?: json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() && it != "null" }
                        if (errMsg != null) throw IOException("API 错误：$errMsg")
                        val choice = json.optJSONArray("choices")?.optJSONObject(0)
                        val content = choice?.optJSONObject("message")?.optString("content").orEmpty()
                            .takeIf { it.isNotBlank() && it != "null" }
                            ?: choice?.optString("text").orEmpty()
                        if (content.isNotBlank()) {
                            onDelta(content)
                        } else {
                            throw IOException("服务未返回文本内容（响应：${trimmed.take(120)}）")
                        }
                    } catch (e: IOException) {
                        throw e
                    } catch (e: Exception) {
                        throw IOException("响应解析失败：${e.message}（响应：${trimmed.take(120)}）")
                    }
                } else {
                    // 响应是 SSE 流（逐行解析）
                    body.lines().forEach { line ->
                        coroutineContext.ensureActive()
                        if (!line.startsWith("data:")) return@forEach
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]" || data.contains("[DONE]")) return@forEach
                        if (data.isBlank()) return@forEach
                        try {
                            val json = JSONObject(data)
                            val choice = json.optJSONArray("choices")?.optJSONObject(0)
                            var delta = choice?.optJSONObject("delta")?.optString("content").orEmpty()
                            if (delta.isEmpty()) delta = choice?.optJSONObject("message")?.optString("content").orEmpty()
                            if (delta.isEmpty()) delta = json.optString("text").orEmpty()
                            if (delta.isNotEmpty() && delta != "null") onDelta(delta)
                        } catch (_: Exception) {}
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