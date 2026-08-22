package com.afyzfur.afyzhub.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.afyzfur.afyzhub.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 应用设置。 */
data class AppSettings(
    val apiKey: String = "",
    val model: String = Constants.DEFAULT_MODEL,
    val baseUrl: String = Constants.DEFAULT_BASE_URL
)

/**
 * 设置的统一读写入口。
 *
 * 对外暴露一个热流 [settings]，请求拦截器可以直接读取内存中的最新值，
 * 不必在 OkHttp 线程上阻塞读磁盘。
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope
) : SettingsProvider {
    private val apiKeyKey = stringPreferencesKey(Constants.KEY_API_KEY)
    private val modelKey = stringPreferencesKey(Constants.KEY_MODEL)
    private val baseUrlKey = stringPreferencesKey(Constants.KEY_BASE_URL)

    val settingsFlow: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            apiKey = prefs[apiKeyKey].orEmpty(),
            model = prefs[modelKey]?.takeIf { it.isNotBlank() } ?: Constants.DEFAULT_MODEL,
            baseUrl = prefs[baseUrlKey]?.takeIf { it.isNotBlank() } ?: Constants.DEFAULT_BASE_URL
        )
    }

    /** 常驻缓存，供拦截器同步读取，避免每次请求都落盘。 */
    val settings: StateFlow<AppSettings> = settingsFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings()
    )

    override suspend fun current(): AppSettings = settingsFlow.first()

    suspend fun save(apiKey: String, model: String, baseUrl: String) {
        dataStore.edit { prefs ->
            prefs[apiKeyKey] = apiKey.trim()
            prefs[modelKey] = model.trim().ifBlank { Constants.DEFAULT_MODEL }
            prefs[baseUrlKey] = normalizeBaseUrl(baseUrl)
        }
    }

    /** Retrofit 要求 baseUrl 以 "/" 结尾，这里统一补齐。 */
    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return Constants.DEFAULT_BASE_URL
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
