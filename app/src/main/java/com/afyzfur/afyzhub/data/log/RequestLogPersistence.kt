package com.afyzfur.afyzhub.data.log

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 落盘用的失败记录。
 *
 * 与 [RequestLogEntry] 分开而不是直接序列化后者：落盘只需要排查
 * 所必需的字段，且要控制体积。响应体在这里会被截断，内存中的完整
 * 副本仍保留在 [RequestLogStore] 里供当次会话查看。
 *
 * 只存失败：成功的请求没人回头看，而失败原因常常要隔一阵才想起来查，
 * 那时应用早已重启过。
 */
@Serializable
data class PersistedErrorLog(
    val startedAt: Long,
    val host: String,
    val method: String,
    val url: String,
    val statusCode: Int?,
    /** 已截断的响应体 */
    val responseBody: String?,
    val error: String?,
    val durationMs: Long
)

/** 单条响应体的落盘上限。超出部分截断并标注 */
private const val MAX_BODY_CHARS = 2000

/** 落盘保留的失败记录条数 */
const val MAX_PERSISTED_ERRORS = 30

fun RequestLogEntry.toPersisted(): PersistedErrorLog = PersistedErrorLog(
    startedAt = startedAt,
    host = host,
    method = method,
    url = url,
    statusCode = statusCode,
    responseBody = responseBody?.let { body ->
        if (body.length <= MAX_BODY_CHARS) {
            body
        } else {
            body.take(MAX_BODY_CHARS) + "\n…（已截断，完整内容见本次运行的日志）"
        }
    },
    error = error,
    durationMs = durationMs
)

/**
 * 还原为界面可展示的条目。
 *
 * id 由调用方重新分配：落盘的 id 与当次运行的序列无关，
 * 混用会导致列表里出现重复 key。
 *
 * 请求头与请求体不落盘，还原后为空——它们体积大且对定位
 * "服务端为什么拒绝"帮助有限，关键信息在响应体和错误原因里。
 */
fun PersistedErrorLog.toEntry(id: Long): RequestLogEntry = RequestLogEntry(
    id = id,
    startedAt = startedAt,
    host = host,
    method = method,
    url = url,
    headers = emptyMap(),
    requestBody = null,
    statusCode = statusCode,
    responseBody = responseBody,
    error = error,
    durationMs = durationMs,
    restored = true
)

/** 落盘用的序列化器，容忍字段增删以免版本升级后读不出旧记录 */
val requestLogJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
