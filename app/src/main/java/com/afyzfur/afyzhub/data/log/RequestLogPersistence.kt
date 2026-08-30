package com.afyzfur.afyzhub.data.log

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 落盘用的请求记录。
 *
 * 与 [RequestLogEntry] 分开而不是直接序列化后者：落盘只需要排查
 * 所必需的字段，且要控制体积。响应体在这里会被截断，内存中的完整
 * 副本仍保留在 [RequestLogStore] 里供当次会话查看。
 *
 * 0.3.2 起成功记录也落盘。此前只存失败，理由是"成功的没人回头看"，
 * 但那让"最近这些请求分别用了哪个模型、各花了多久"这类问题在重启后
 * 无从查证。代价是写入频率从偶尔变成每次请求，因此条数上限要控制住。
 *
 * 类名保留 PersistedErrorLog 不改：它出现在落盘 JSON 的类型信息之外，
 * 改名不影响文件格式，但会牵动若干引用，收益不抵风险。
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
    val durationMs: Long,
    /**
     * 提供商与模型。
     *
     * 必须带默认值：0.3.2 之前落盘的文件没有这两个字段，缺省值缺失会让
     * 反序列化抛异常，而 restore() 的兜底是删掉整个文件——那等于升级一次
     * 就丢掉全部历史记录。ignoreUnknownKeys 只管多出来的字段，管不了
     * 少掉的。
     */
    val provider: String? = null,
    val model: String? = null
)

/** 单条响应体的落盘上限。超出部分截断并标注 */
private const val MAX_BODY_CHARS = 2000

/** 落盘保留的失败记录条数 */
const val MAX_PERSISTED_ERRORS = 30

fun RequestLogEntry.toPersisted(): PersistedErrorLog = PersistedErrorLog(
    startedAt = startedAt,
    host = host,
    provider = provider,
    model = model,
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
    provider = provider,
    model = model,
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
