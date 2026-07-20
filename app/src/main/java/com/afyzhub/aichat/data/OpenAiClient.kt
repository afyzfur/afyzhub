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

        // 根据上下文模式裁剪历史消息
        val trimmedHistory = trimHistory(config, messages)

        val requestMessages = JSONArray()
        if (config.systemPrompt.isNotBlank()) {
            requestMessages.put(
                JSONObject()
                    .put("role", "system")
                    .put("content", config.systemPrompt)
            )
        }
        trimmedHistory.forEach { message ->
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

                val contentType = response.header("Content-Type").orEmpty()
                val isSse = contentType.contains("event-stream", ignoreCase = true)

                if (config.useStream && isSse) {
                    // 真流式：逐行读取 SSE，边读边回调，实现逐字显示
                    var received = false
                    val reader = response.body?.charStream()?.buffered()
                        ?: throw IOException("服务返回了空响应")
                    reader.useLines { lines ->
                        for (line in lines) {
                            coroutineContext.ensureActive()
                            if (!line.startsWith("data:")) continue
                            val data = line.removePrefix("data:").trim()
                            if (data == "[DONE]" || data.contains("[DONE]")) break
                            if (data.isBlank()) continue
                            try {
                                val delta = parseDelta(JSONObject(data))
                                if (delta.isNotEmpty()) {
                                    received = true
                                    onDelta(delta)
                                }
                            } catch (_: Exception) {
                                // 忽略单个损坏事件，继续读取后续 SSE 数据
                            }
                        }
                    }
                    if (!received) throw IOException("服务未返回文本内容")
                } else {
                    // 非流式或服务返回普通 JSON：读完整体后解析
                    val body = response.body?.string() ?: throw IOException("服务返回了空响应")
                    val trimmed = body.trimStart()

                    // 服务虽非 event-stream，但仍可能逐行返回 SSE 文本
                    if (trimmed.startsWith("data:")) {
                        var received = false
                        trimmed.lines().forEach { line ->
                            if (!line.startsWith("data:")) return@forEach
                            val data = line.removePrefix("data:").trim()
                            if (data == "[DONE]" || data.contains("[DONE]")) return@forEach
                            if (data.isBlank()) return@forEach
                            try {
                                val delta = parseDelta(JSONObject(data))
                                if (delta.isNotEmpty()) {
                                    received = true
                                    onDelta(delta)
                                }
                            } catch (_: Exception) {}
                        }
                        if (!received) throw IOException("服务未返回文本内容")
                        return@use
                    }

                    try {
                        val json = JSONObject(trimmed)
                        val errMsg = json.optString("error")
                            .takeIf { it.isNotBlank() && it != "null" }
                            ?: json.optJSONObject("error")?.optString("message")
                                ?.takeIf { it.isNotBlank() && it != "null" }
                        if (errMsg != null) throw IOException("API 错误：$errMsg")

                        val content = parseFullContent(json)
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
                }
            }
        } finally {
            activeCall = null
        }
    }

    /**
     * 拉取 /models 模型列表。返回模型 id 列表（含可识别的上下文窗口）。
     * 失败时抛出带可读信息的 IOException。
     */
    suspend fun fetchModels(config: ApiConfig, apiKey: String): List<ModelInfo> =
        withContext(Dispatchers.IO) {
            require(config.baseUrl.startsWith("https://")) {
                "为保护 API Key，仅允许使用 HTTPS 地址"
            }
            val request = Request.Builder()
                .url(config.baseUrl.trim().trimEnd('/') + "/models")
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .get()
                .build()

            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()
                .use { response ->
                    if (!response.isSuccessful) {
                        throw IOException(
                            when (response.code) {
                                401 -> "认证失败，请检查 API Key"
                                403 -> "服务拒绝访问（403）"
                                404 -> "该服务不支持 /models 接口"
                                429 -> "请求过于频繁（429）"
                                in 500..599 -> "服务暂时不可用（HTTP ${response.code}）"
                                else -> "获取模型失败（HTTP ${response.code}）"
                            }
                        )
                    }
                    val body = response.body?.string()
                        ?: throw IOException("服务返回了空响应")
                    parseModels(body)
                }
        }

    /** 解析 /models 响应，兼容 {data:[{id,...}]} 与直接数组两种格式。 */
    private fun parseModels(body: String): List<ModelInfo> {
        val trimmed = body.trimStart()
        val array: JSONArray = try {
            if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                JSONObject(trimmed).optJSONArray("data") ?: JSONArray()
            }
        } catch (e: Exception) {
            throw IOException("模型列表解析失败：${e.message}")
        }

        val models = ArrayList<ModelInfo>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() }
                ?: item.optString("model").takeIf { it.isNotBlank() }
                ?: continue
            // 部分服务会直接给出 context_length / context_window 字段
            val ctx = item.optInt("context_length", 0)
                .takeIf { it > 0 }
                ?: item.optInt("context_window", 0).takeIf { it > 0 }
                ?: ModelCatalog.contextWindowFor(id)
            models.add(ModelInfo(id = id, contextWindow = ctx))
        }
        return models.sortedBy { it.id }
    }

    fun cancel() {
        activeCall?.cancel()
        activeCall = null
    }

    /** 解析流式 chunk 的增量文本，兼容 delta.content / message.content / text。 */
    private fun parseDelta(json: JSONObject): String {
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
        choice?.optJSONObject("delta")?.optString("content")
            ?.takeIf { it.isNotEmpty() && it != "null" }?.let { return it }
        choice?.optJSONObject("message")?.optString("content")
            ?.takeIf { it.isNotEmpty() && it != "null" }?.let { return it }
        return json.optString("text").takeIf { it.isNotEmpty() && it != "null" }.orEmpty()
    }

    /** 解析非流式完整响应的文本内容。 */
    private fun parseFullContent(json: JSONObject): String {
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
        choice?.optJSONObject("message")?.optString("content")
            ?.takeIf { it.isNotBlank() && it != "null" }?.let { return it }
        return choice?.optString("text").orEmpty()
    }

    /**
     * 按上下文模式裁剪历史消息。
     * - LIMITED：保留最近 contextLimit 条。
     * - MAX：按模型上下文窗口的 token 预算，从最新往旧尽量多带。
     * 始终保留最后一条（当前用户消息）。
     */
    private fun trimHistory(
        config: ApiConfig,
        messages: List<ChatMessage>
    ): List<ChatMessage> {
        if (messages.isEmpty()) return messages

        return when (config.contextMode) {
            ContextMode.LIMITED -> {
                val limit = config.contextLimit.coerceAtLeast(1)
                messages.takeLast(limit)
            }
            ContextMode.MAX -> {
                val window = ModelCatalog.contextWindowFor(config.model)
                // 预留 system prompt 与输出 token 的余量
                val systemTokens = ModelCatalog.estimateTokens(config.systemPrompt)
                val budget = (window - config.maxTokens - systemTokens - 256)
                    .coerceAtLeast(1_000)
                val selected = ArrayList<ChatMessage>()
                var used = 0
                for (message in messages.asReversed()) {
                    val cost = ModelCatalog.estimateTokens(message.content) + 8
                    if (used + cost > budget && selected.isNotEmpty()) break
                    selected.add(message)
                    used += cost
                }
                selected.asReversed()
            }
        }
    }
}