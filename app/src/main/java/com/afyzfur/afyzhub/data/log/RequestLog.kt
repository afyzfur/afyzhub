package com.afyzfur.afyzhub.data.log

/**
 * 一次 HTTP 请求的记录。
 *
 * 用于排查配置问题（地址写错、密钥无效、模型不存在），
 * 这类问题的报错往往被包装成一句"请求失败"，看不到服务端原文。
 */
data class RequestLogEntry(
    val id: Long,
    val startedAt: Long,
    /**
     * 请求目标主机名。
     *
     * 记主机名而非提供商名：配置出错时最需要确认的正是
     * "请求实际打到了哪里"，中转服务地址写错一眼就能看出。
     */
    val host: String,
    val method: String,
    val url: String,
    /** 已脱敏的请求头 */
    val headers: Map<String, String>,
    val requestBody: String?,
    /** HTTP 状态码；请求未能发出（如 DNS 失败）时为 null */
    val statusCode: Int?,
    val responseBody: String?,
    /** 失败原因，成功时为 null */
    val error: String?,
    val durationMs: Long,
    /**
     * 是否来自上次运行的落盘记录。
     *
     * 界面据此标注，否则重启后旧的失败看起来像刚刚发生的，
     * 会误导排查方向。
     */
    val restored: Boolean = false
) {
    val isSuccess: Boolean get() = error == null && statusCode != null && statusCode in 200..299
}

/** 密钥脱敏后的替代文本 */
private const val REDACTED = "***"

/**
 * 可能携带凭证的请求头名（小写比较）。
 *
 * 采用白名单之外一律脱敏的思路会更安全，但请求头里大部分字段
 * （Content-Type、Accept）对排查有用，全遮掉反而没法看。
 * 这里列举三家实际用到的鉴权头，新增提供商时需要同步补充。
 */
private val SENSITIVE_HEADERS = setOf(
    "authorization",
    "x-api-key",
    "x-goog-api-key",
    "api-key",
    "cookie",
    "set-cookie"
)

/**
 * 脱敏请求头。
 *
 * 保留前 4 位便于确认"用的是哪个 key"，其余替换。
 * 长度不足 8 位时整体替换——短 key 保留前缀等于泄露大半。
 */
fun redactHeaders(headers: Map<String, String>): Map<String, String> =
    headers.mapValues { (name, value) ->
        if (name.lowercase() in SENSITIVE_HEADERS) redactValue(value) else value
    }

internal fun redactValue(value: String): String {
    // Bearer 前缀本身不敏感，保留它使日志更易读
    val prefix = "Bearer "
    if (value.startsWith(prefix, ignoreCase = true)) {
        val token = value.substring(prefix.length)
        return prefix + maskToken(token)
    }
    return maskToken(value)
}

/** 保留前 4 位，短于 8 位则整体遮蔽 */
private fun maskToken(token: String): String =
    if (token.length < 8) REDACTED else token.take(4) + REDACTED

/**
 * 脱敏 URL 中的查询参数。
 *
 * Gemini 官方支持 `?key=` 传密钥，本应用只用请求头，但中转服务
 * 的地址可能自带 token 参数，一并处理。
 */
fun redactUrl(url: String): String {
    val queryStart = url.indexOf('?')
    if (queryStart < 0) return url

    val base = url.take(queryStart)
    val query = url.substring(queryStart + 1)
        .split('&')
        .joinToString("&") { param ->
            val eq = param.indexOf('=')
            if (eq < 0) return@joinToString param
            val name = param.take(eq)
            if (name.lowercase() in setOf("key", "token", "access_token", "api_key")) {
                "$name=$REDACTED"
            } else {
                param
            }
        }
    return "$base?$query"
}
