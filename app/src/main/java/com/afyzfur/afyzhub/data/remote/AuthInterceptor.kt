package com.afyzfur.afyzhub.data.remote

import com.afyzfur.afyzhub.data.settings.SettingsRepository
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 注入鉴权头，并把请求重定向到用户配置的 API 地址。
 *
 * Retrofit 的 baseUrl 在构建时固定，因此这里在拦截器中改写 host，
 * 以支持运行时切换中转地址而无需重建 Retrofit 实例。
 */
class AuthInterceptor(
    private val settingsRepository: SettingsRepository
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val settings = settingsRepository.settings.value
        val original = chain.request()

        val builder = original.newBuilder()
        if (settings.apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${settings.apiKey}")
        }

        val configured = settings.baseUrl.toHttpUrlOrNull()
        if (configured != null) {
            // 保留原始的路径与查询串，仅替换协议、主机、端口和路径前缀。
            val newUrl = original.url.newBuilder()
                .scheme(configured.scheme)
                .host(configured.host)
                .port(configured.port)
                .build()
            builder.url(newUrl)
        }

        return chain.proceed(builder.build())
    }
}
