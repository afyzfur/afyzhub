package com.afyzfur.afyzhub.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.afyzfur.afyzhub.BuildConfig
import com.afyzfur.afyzhub.data.remote.provider.AnthropicChatClient
import com.afyzfur.afyzhub.data.remote.provider.ChatClientRegistry
import com.afyzfur.afyzhub.data.remote.provider.GeminiChatClient
import com.afyzfur.afyzhub.data.log.RequestLogStore
import com.afyzfur.afyzhub.data.remote.provider.HttpTransport
import com.afyzfur.afyzhub.data.remote.provider.OpenAiChatClient
import com.afyzfur.afyzhub.data.remote.provider.Transport
import com.afyzfur.afyzhub.data.settings.SettingsProvider
import com.afyzfur.afyzhub.data.settings.SettingsRepository
import com.afyzfur.afyzhub.domain.model.AiProvider
import com.afyzfur.afyzhub.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.PREFS_NAME)

val networkModule = module {
    single { androidContext().dataStore }

    /** 应用级作用域，用于常驻缓存设置。 */
    single { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    single { SettingsRepository(get(), get()) }

    // 数据层依赖的是只读接口，这里把同一个实例按接口再暴露一次。
    single<SettingsProvider> { get<SettingsRepository>() }

    single {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            // stream 等布尔字段必须随请求发出，因此保留默认值。
            encodeDefaults = true
            explicitNulls = false
        }
    }

    single {
        HttpLoggingInterceptor().apply {
            // Release 包必须关闭，否则鉴权头与对话内容会写入系统日志。
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    single {
        // 鉴权头与地址拼接已移入各 provider 客户端，这里不再需要拦截器。
        OkHttpClient.Builder()
            .addInterceptor(get<HttpLoggingInterceptor>())
            .connectTimeout(30, TimeUnit.SECONDS)
            // 流式响应期间连接会长时间保持，读超时不能过短。
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // 日志存储须为单例：请求在网络层写入，日志页在另一处读取
    single { RequestLogStore() }
    single<Transport> { HttpTransport(get(), get()) }

    single {
        ChatClientRegistry(
            mapOf(
                AiProvider.OPENAI to OpenAiChatClient(get(), get()),
                AiProvider.ANTHROPIC to AnthropicChatClient(get(), get()),
                AiProvider.GEMINI to GeminiChatClient(get(), get())
            )
        )
    }
}
