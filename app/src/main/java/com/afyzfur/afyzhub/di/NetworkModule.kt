package com.afyzfur.afyzhub.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.afyzfur.afyzhub.BuildConfig
import com.afyzfur.afyzhub.data.remote.AuthInterceptor
import com.afyzfur.afyzhub.data.remote.OpenAIApi
import com.afyzfur.afyzhub.data.settings.SettingsProvider
import com.afyzfur.afyzhub.data.settings.SettingsRepository
import com.afyzfur.afyzhub.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
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
            encodeDefaults = false
        }
    }

    single {
        HttpLoggingInterceptor().apply {
            // Release 包必须关闭，否则 Authorization 头与对话内容会写入系统日志。
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    single { AuthInterceptor(get()) }

    single {
        OkHttpClient.Builder()
            .addInterceptor(get<AuthInterceptor>())
            .addInterceptor(get<HttpLoggingInterceptor>())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    single {
        val json: Json = get()
        Retrofit.Builder()
            // 实际地址由 AuthInterceptor 按用户设置改写，这里仅作占位。
            .baseUrl(Constants.DEFAULT_BASE_URL)
            .client(get())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    single { get<Retrofit>().create(OpenAIApi::class.java) }
}
