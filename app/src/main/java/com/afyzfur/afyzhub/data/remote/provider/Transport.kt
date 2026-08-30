package com.afyzfur.afyzhub.data.remote.provider

import com.afyzfur.afyzhub.data.log.RequestLogContext
import kotlinx.coroutines.flow.Flow

/**
 * HTTP 传输抽象。
 *
 * 抽出接口是为了让各 provider 的请求构造与响应解析可以在 JVM 单元测试中
 * 用假实现验证，无需真实网络。
 */
interface Transport {

    /** 发送 GET 并返回完整响应体文本。 */
    suspend fun getForText(
        baseUrl: String,
        path: String,
        headers: Map<String, String>,
        query: Map<String, String> = emptyMap(),
        logContext: RequestLogContext = RequestLogContext.EMPTY
    ): String

    /** 发送 POST 并返回完整响应体文本。 */
    suspend fun postForText(
        baseUrl: String,
        path: String,
        headers: Map<String, String>,
        body: String,
        query: Map<String, String> = emptyMap(),
        logContext: RequestLogContext = RequestLogContext.EMPTY
    ): String

    /**
     * 发送 POST 并逐行产出 SSE 数据行。
     *
     * 产出的是去掉 `data:` 前缀后的原始负载，交由调用方解析。
     */
    fun postForSse(
        baseUrl: String,
        path: String,
        headers: Map<String, String>,
        body: String,
        query: Map<String, String> = emptyMap(),
        logContext: RequestLogContext = RequestLogContext.EMPTY
    ): Flow<String>
}
