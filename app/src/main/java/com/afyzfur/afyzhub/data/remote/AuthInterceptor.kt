package com.afyzfur.afyzhub.data.remote

import com.afyzfur.afyzhub.data.settings.AppSettings
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 注入鉴权头，并把请求重定向到用户配置的 API 地址。
 *
 * Retrofit 的 baseUrl 在构建时固定，因此这里在拦截器中改写地址，
 * 以支持运行时切换中转服务而无需重建客户端。
 *
 * [snapshot] 从内存缓存同步取值，不在网络线程上读磁盘。
 */
class AuthInterceptor(
    private val snapshot: () -> AppSettings
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val settings = snapshot()
        val original = chain.request()

        val builder = original.newBuilder()
        if (settings.apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${settings.apiKey}")
        }

        settings.baseUrl.toHttpUrlOrNull()?.let { configured ->
            builder.url(rewriteUrl(original.url, configured))
        }

        return chain.proceed(builder.build())
    }

    /**
     * 用配置地址替换主机，并保留其路径前缀。
     *
     * 例如配置为 `https://relay.example.com/openai/`、原请求路径为
     * `/v1/chat/completions` 时，结果是
     * `https://relay.example.com/openai/v1/chat/completions`。
     */
    private fun rewriteUrl(original: HttpUrl, configured: HttpUrl): HttpUrl {
        val prefix = configured.pathSegments.filter { it.isNotEmpty() }
        val builder = original.newBuilder()
            .scheme(configured.scheme)
            .host(configured.host)
            .port(configured.port)

        if (prefix.isNotEmpty()) {
            val originalSegments = original.pathSegments.filter { it.isNotEmpty() }
            // 重建路径：先清空原有分段，再依次写入前缀与原路径。
            repeat(original.pathSegments.size) { builder.removePathSegment(0) }
            (prefix + originalSegments).forEach { builder.addPathSegment(it) }
        }

        return builder.build()
    }
}
