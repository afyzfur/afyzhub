package com.afyzfur.afyzhub.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.afyzfur.afyzhub.data.remote.provider.SearchEngine
import com.afyzfur.afyzhub.data.log.LogRetention
import com.afyzfur.afyzhub.domain.model.AiProvider
import com.afyzfur.afyzhub.domain.model.ApiProfileStore
import com.afyzfur.afyzhub.domain.model.ThinkingEffort
import com.afyzfur.afyzhub.ui.theme.ThemePalette
import kotlinx.serialization.json.Json
import com.afyzfur.afyzhub.util.Constants
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 设置的统一读写入口。
 *
 * API Key、模型和地址按提供商分别存储，切换提供商不会互相覆盖。
 * 对外暴露热流 [settings]，供网络层同步读取，避免在请求线程上读磁盘。
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope
) : SettingsProvider, UiSettingsAccess by UiSettingsStore(dataStore, scope) {
    val credentialsUnavailable: StateFlow<Boolean> =
        (dataStore as? EncryptedPreferencesStore)?.credentialsUnavailable
            ?: kotlinx.coroutines.flow.MutableStateFlow(false)

    suspend fun resetCredentialsAfterConfirmation() {
        (dataStore as? EncryptedPreferencesStore)?.resetCredentialsAfterConfirmation()
    }


    /**
     * 配置组的序列化器。
     *
     * ignoreUnknownKeys 是为了让降级安装不至于把配置清空：
     * 新版加了字段后回退到旧版，严格模式会解析失败。
     */
    private val profileJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        scope.launch {
            // The encrypted boundary migrates legacy values atomically on the first edit.
            // DataStore retains the original file if encryption fails.
            try { dataStore.updateData { it } }
            catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                java.util.logging.Logger.getLogger("AfyzHub.Settings").warning("Credential migration failed; original preferences preserved: ${e.javaClass.simpleName}")
            }
        }
    }

    private val providerKey = stringPreferencesKey(Constants.KEY_PROVIDER)
    private val streamKey = booleanPreferencesKey(Constants.KEY_STREAM_ENABLED)
    private val thinkingEffortKey = stringPreferencesKey(Constants.KEY_THINKING_EFFORT)

    private val webSearchEnabledKey = booleanPreferencesKey(Constants.KEY_WEB_SEARCH_ENABLED)
    private val inAppBrowserKey = booleanPreferencesKey(Constants.KEY_IN_APP_BROWSER)
    private val searchEngineKey = stringPreferencesKey(Constants.KEY_SEARCH_ENGINE)
    private val tavilyApiKeyKey = stringPreferencesKey(Constants.KEY_TAVILY_API_KEY)
    private val geminiSearchKey = booleanPreferencesKey(Constants.KEY_GEMINI_SEARCH)
    private val systemPromptKey = stringPreferencesKey(Constants.KEY_SYSTEM_PROMPT)
    private val logRetentionKey = stringPreferencesKey(Constants.KEY_LOG_RETENTION)
    private val logEnabledKey = booleanPreferencesKey(Constants.KEY_LOG_ENABLED)

    private fun apiKeyKey(p: AiProvider) =
        stringPreferencesKey("${Constants.KEY_PREFIX_API_KEY}_${p.id}")

    private fun modelKey(p: AiProvider) =
        stringPreferencesKey("${Constants.KEY_PREFIX_MODEL}_${p.id}")

    private fun baseUrlKey(p: AiProvider) =
        stringPreferencesKey("${Constants.KEY_PREFIX_BASE_URL}_${p.id}")

    private fun modelListKey(p: AiProvider) =
        stringPreferencesKey("${Constants.KEY_PREFIX_MODEL_LIST}_${p.id}")
    private val apiProfilesKey = stringPreferencesKey(Constants.KEY_API_PROFILES)
    private val profilesMigratedKey =
        booleanPreferencesKey(Constants.KEY_PROFILES_MIGRATED)

    /**
     * 多组 API 配置。
     *
     * 存成一个 JSON 键而非给每组拆键：组数不定且可增删，拆键需要
     * 额外维护一份"有哪些组"的索引，删除时还要逐个清理残留键。
     *
     * 解析失败时返回空 store 而非抛出：DataStore 里的值可能因为
     * 降级安装等原因带上未知字段，让配置页显示为空并可重建，
     * 比整个应用起不来要好。
     */
    val apiProfilesFlow: Flow<ApiProfileStore> = dataStore.data.map { prefs ->
        readProfiles(prefs)
    }

    val apiProfiles: StateFlow<ApiProfileStore> = apiProfilesFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = ApiProfileStore()
    )

    private fun readProfiles(prefs: Preferences): ApiProfileStore {
        val raw = prefs[apiProfilesKey]
        if (raw.isNullOrBlank()) return ApiProfileStore()
        return try {
            profileJson.decodeFromString(ApiProfileStore.serializer(), raw)
        } catch (_: Exception) {
            ApiProfileStore()
        }
    }

    /** 覆盖写入全部配置组。增删改都走这里，调用方先拿到完整 store 再改 */
    suspend fun saveProfiles(store: ApiProfileStore) {
        dataStore.edit { prefs ->
            prefs[apiProfilesKey] = profileJson.encodeToString(ApiProfileStore.serializer(), store)
            // 手工建过组之后就不该再被旧配置迁移覆盖
            prefs[profilesMigratedKey] = true
        }
    }

    suspend fun currentProfiles(): ApiProfileStore = apiProfilesFlow.first()

    /**
     * 首次进入多组配置时，把旧的按提供商单组配置搬过来。
     *
     * 只执行一次，由 [profilesMigratedKey] 标记。已经有组存在时
     * 直接标记完成——这种情况说明用户已经在用新结构了。
     */
    suspend fun migrateProfilesIfNeeded() {
        val prefs = dataStore.data.first()
        if (credentialsUnavailable.value) return
        if (prefs[profilesMigratedKey] == true) return

        val existing = readProfiles(prefs)
        if (existing.profiles.isNotEmpty()) {
            dataStore.edit { it[profilesMigratedKey] = true }
            return
        }

        val legacy = AiProvider.entries.map { provider ->
            LegacyProviderConfig(
                provider = provider,
                apiKey = readApiKey(prefs, provider),
                baseUrl = prefs[baseUrlKey(provider)].orEmpty(),
                // readModel 会补上兜底模型，这里要的是用户实际填过的值，
                // 否则每家都会被当成"配置过"
                model = prefs[modelKey(provider)].orEmpty(),
                cachedModels = prefs[modelListKey(provider)]
                    .orEmpty()
                    .split('\n')
                    .filter { it.isNotBlank() }
            )
        }
        val migrated = migrateLegacyConfigs(
            configs = legacy,
            activeProvider = AiProvider.fromId(prefs[providerKey])
        )

        dataStore.edit { edit ->
            if (migrated.profiles.isNotEmpty()) {
                edit[apiProfilesKey] = profileJson.encodeToString(ApiProfileStore.serializer(), migrated)
            }
            edit[profilesMigratedKey] = true
        }
    }

    /**
     * 当前生效的设置。
     *
     * 有配置组时取激活的那一组，否则回落到旧的按提供商单组读取。
     * 这样网络层与传输层完全不用知道多组的存在——它们拿到的仍是
     * 一份扁平的 [AppSettings]。
     *
     * 回落分支不能删：迁移在设置页首次打开时才执行，此前若用户
     * 直接发消息，仍要能用上旧配置。
     */
    val settingsFlow: Flow<AppSettings> = dataStore.data.map { prefs ->
        val active = readProfiles(prefs).active
        if (active != null) {
            AppSettings(
                provider = active.provider,
                profileLabel = active.displayName,
                apiKey = active.apiKey,
                model = active.effectiveModel,
                baseUrl = normalizeBaseUrl(active.effectiveBaseUrl, active.provider),
                streamEnabled = prefs[streamKey] ?: true,
                thinkingEffort = ThinkingEffort.fromId(prefs[thinkingEffortKey]),
                webSearchEnabled = prefs[webSearchEnabledKey] ?: false,
                geminiSearchEnabled = prefs[geminiSearchKey] ?: false,
                inAppBrowserEnabled = prefs[inAppBrowserKey] ?: true,
                searchEngine = prefs[searchEngineKey] ?: SearchEngine.BING.id,
                tavilyApiKey = prefs[tavilyApiKeyKey].orEmpty(),
                openingPrompt = active.effectiveOpening,
                systemPrompt = active.effectiveSystemPrompt
            )
        } else {
            val provider = AiProvider.fromId(prefs[providerKey])
            AppSettings(
                provider = provider,
                // 迁移前没有配置组，只能用提供商名
                profileLabel = provider.displayName,
                apiKey = readApiKey(prefs, provider),
                model = readModel(prefs, provider),
                baseUrl = readBaseUrl(prefs, provider),
                streamEnabled = prefs[streamKey] ?: true,
                thinkingEffort = ThinkingEffort.fromId(prefs[thinkingEffortKey]),
                webSearchEnabled = prefs[webSearchEnabledKey] ?: false,
                geminiSearchEnabled = prefs[geminiSearchKey] ?: false,
                inAppBrowserEnabled = prefs[inAppBrowserKey] ?: true,
                searchEngine = prefs[searchEngineKey] ?: SearchEngine.BING.id,
                tavilyApiKey = prefs[tavilyApiKeyKey].orEmpty(),
                systemPrompt = prefs[systemPromptKey].orEmpty()
            )
        }
    }

    /** 常驻缓存，供拦截器与传输层同步取值。 */
    val settings: StateFlow<AppSettings> = settingsFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings()
    )

    /**
     * 请求日志的保留时长。
     *
     * 单独一个流而非塞进 ChatAppearance：后者是聊天外观，与日志无关，
     * 合并会让读外观时带上无关字段，也让"改这个值会不会影响聊天渲染"
     * 变成需要求证的问题。
     */
    val logRetention: Flow<LogRetention> = dataStore.data.map { prefs ->
        LogRetention.fromId(prefs[logRetentionKey])
    }

    suspend fun setLogRetention(retention: LogRetention) {
        dataStore.edit { it[logRetentionKey] = retention.id }
    }

    /**
     * 是否记录请求日志，默认开启。
     *
     * 关闭后连内存也不记，而不只是不落盘：否则"已关闭"却还能在列表里
     * 看到本次运行的记录，与开关的字面意思不符。
     */
    val logEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[logEnabledKey] ?: true
    }

    suspend fun setLogEnabled(enabled: Boolean) {
        dataStore.edit { it[logEnabledKey] = enabled }
    }

    suspend fun setWebSearchEnabled(enabled: Boolean) {
        dataStore.edit { it[webSearchEnabledKey] = enabled }
    }
    suspend fun setInAppBrowserEnabled(enabled: Boolean) {
        dataStore.edit { it[inAppBrowserKey] = enabled }
    }
    suspend fun setSearchEngine(id: String) {
        dataStore.edit { it[searchEngineKey] = id }
    }
    suspend fun setTavilyApiKey(key: String) {
        dataStore.edit { it[tavilyApiKeyKey] = key.trim() }
    }
    suspend fun setGeminiSearchEnabled(enabled: Boolean) {
        dataStore.edit { it[geminiSearchKey] = enabled }
    }

    suspend fun setSystemPrompt(prompt: String) {
        dataStore.edit { it[systemPromptKey] = prompt }
    }

    suspend fun currentSystemPrompt(): String =
        dataStore.data.first()[systemPromptKey].orEmpty()

    override suspend fun current(): AppSettings = settingsFlow.first()

    /** 读取指定提供商的配置，用于设置页在切换时回填。 */
    suspend fun configFor(provider: AiProvider): AppSettings {
        val prefs = dataStore.data.first()
        return AppSettings(
            provider = provider,
            apiKey = readApiKey(prefs, provider),
            model = readModel(prefs, provider),
            baseUrl = readBaseUrl(prefs, provider),
            streamEnabled = prefs[streamKey] ?: true
        )
    }

    /**
     * 只写流式开关。
     *
     * 它是全局项，不属于任何配置组。单独提供这个方法是因为 [save]
     * 会连带写入 Key、地址、模型——设置首页只想改开关时用 save
     * 会把界面上那份可能已过时的快照写回去。
     */
    suspend fun setThinkingEffort(effort: ThinkingEffort) {
        dataStore.edit { it[thinkingEffortKey] = effort.id }
    }

    suspend fun setStreamEnabled(enabled: Boolean) {
        dataStore.edit { it[streamKey] = enabled }
    }

    suspend fun save(
        provider: AiProvider,
        apiKey: String,
        model: String,
        baseUrl: String,
        streamEnabled: Boolean
    ) {
        dataStore.edit { prefs ->
            prefs[providerKey] = provider.id
            prefs[apiKeyKey(provider)] = apiKey.trim()
            prefs[modelKey(provider)] = model.trim().ifBlank { provider.fallbackModel }
            prefs[baseUrlKey(provider)] = normalizeBaseUrl(baseUrl, provider)
            prefs[streamKey] = streamEnabled
        }
    }

    /** 读取缓存的模型列表；没有缓存时返回空列表。 */
    suspend fun cachedModels(provider: AiProvider): List<String> {
        val raw = dataStore.data.first()[modelListKey(provider)].orEmpty()
        return raw.split('\n').filter { it.isNotBlank() }
    }

    /** 保存拉取到的模型列表，供下次进入设置页直接展示。 */
    suspend fun saveModels(provider: AiProvider, models: List<String>) {
        dataStore.edit { prefs ->
            prefs[modelListKey(provider)] = models.joinToString("\n")
        }
    }

    /**
     * 读取 API Key。
     *
     * v0.1.3 及之前只有单份 OpenAI 配置，若新键名为空则回退到旧键，
     * 使升级用户不必重新填写。
     */
    private fun readApiKey(prefs: Preferences, provider: AiProvider): String {
        prefs[apiKeyKey(provider)]?.takeIf { it.isNotBlank() }?.let { return it }
        if (provider == AiProvider.OPENAI) {
            prefs[stringPreferencesKey(Constants.LEGACY_KEY_API_KEY)]
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return ""
    }

    private fun readModel(prefs: Preferences, provider: AiProvider): String {
        prefs[modelKey(provider)]?.takeIf { it.isNotBlank() }?.let { return it }
        if (provider == AiProvider.OPENAI) {
            prefs[stringPreferencesKey(Constants.LEGACY_KEY_MODEL)]
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return provider.fallbackModel
    }

    private fun readBaseUrl(prefs: Preferences, provider: AiProvider): String {
        prefs[baseUrlKey(provider)]?.takeIf { it.isNotBlank() }?.let { return it }
        if (provider == AiProvider.OPENAI) {
            prefs[stringPreferencesKey(Constants.LEGACY_KEY_BASE_URL)]
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return provider.defaultBaseUrl
    }

    /** 地址必须以 "/" 结尾，便于后续拼接路径。 */
    private fun normalizeBaseUrl(raw: String, provider: AiProvider): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return provider.defaultBaseUrl
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
