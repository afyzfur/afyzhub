package com.afyzfur.afyzhub.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import com.afyzfur.afyzhub.data.remote.OpenAIApi
import com.afyzfur.afyzhub.util.Constants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
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
    
    single {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        json
    }
    
    single {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }
    
    single {
        val dataStore: DataStore<Preferences> = get()
        Interceptor { chain ->
            val apiKey = runBlocking {
                val key = stringPreferencesKey(Constants.KEY_API_KEY)
                dataStore.data.first()[key] ?: ""
            }
            
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $apiKey")
                .build()
            chain.proceed(request)
        }
    }
    
    single {
        OkHttpClient.Builder()
            .addInterceptor(get<Interceptor>())
            .addInterceptor(get<HttpLoggingInterceptor>())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    single {
        val json: Json = get()
        Retrofit.Builder()
            .baseUrl(Constants.OPENAI_BASE_URL)
            .client(get())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
    
    single {
        get<Retrofit>().create(OpenAIApi::class.java)
    }
}
