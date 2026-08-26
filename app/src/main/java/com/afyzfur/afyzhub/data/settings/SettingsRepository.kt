package com.afyzfur.afyzhub.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.afyzfur.afyzhub.domain.model.AiProvider
import com.afyzfur.afyzhub.domain.model.ApiProfileStore
import com.afyzfur.afyzhub.domain.model.ThinkingEffort
import com.afyzfur.afyzhub.ui.theme.ThemePalette
import kotlinx.serialization.json.Json
import com.afyzfur.afyzhub.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 当前生效的设置。 */
data class AppSettings(
    val provider: AiProvider = AiProvider.DEFAULT,
    val apiKey: String = "",
    val model: String = AiProvider.DEFAULT.fallbackModel,
    val baseUrl: String = AiProvider.DEFAULT.defaultBaseUrl,
    /** 是否使用流式输出，默认开启。 */
    val streamEnabled: Boolean = true,
    /**
     * 思考程度。全局项，不随配置组切换。
     *
     * 放在 AppSettings 而非 ApiProfile：它更像"这次想让模型想多久"的
     * 临时选择，用户会频繁在输入栏切换，而不是某组配置的固定属性。
     */
    val thinkingEffort: ThinkingEffort = ThinkingEffort.DEFAULT
)

/**
 * 设置的统一读写入口。
 *
 * API Key、模型和地址按提供商分别存储，切换提供商不会互相覆盖。
 * 对外暴露热流 [settings]，供网络层同步读取，避免在请求线程上读磁盘。
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope
) : SettingsProvider {

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

    private val providerKey = stringPreferencesKey(Constants.KEY_PROVIDER)
    private val streamKey = booleanPreferencesKey(Constants.KEY_STREAM_ENABLED)
    private val thinkingEffortKey = stringPreferencesKey(Constants.KEY_THINKING_EFFORT)

    // 界面偏好，全局项
    private val colorModeKey = stringPreferencesKey(Constants.KEY_COLOR_MODE)
    private val dynamicColorKey = booleanPreferencesKey(Constants.KEY_DYNAMIC_COLOR)
    private val showTimestampKey = booleanPreferencesKey(Constants.KEY_SHOW_TIMESTAMP)
    private val showTokenUsageKey = booleanPreferencesKey(Constants.KEY_SHOW_TOKEN_USAGE)
    private val showSpeedKey = booleanPreferencesKey(Constants.KEY_SHOW_SPEED)
    private val showLatencyKey = booleanPreferencesKey(Constants.KEY_SHOW_LATENCY)
    private val showActionsKey = booleanPreferencesKey(Constants.KEY_SHOW_ACTIONS)
    private val showModelNameKey = booleanPreferencesKey(Constants.KEY_SHOW_MODEL_NAME)
    private val quickPromptsKey = stringPreferencesKey(Constants.KEY_QUICK_PROMPTS)
    private val shufflePromptsKey = booleanPreferencesKey(Constants.KEY_SHUFFLE_PROMPTS)
    private val paletteKey = stringPreferencesKey(Constants.KEY_PALETTE)
    private val userBubbleKey = stringPreferencesKey(Constants.KEY_USER_BUBBLE)
    private val assistantBubbleKey = stringPreferencesKey(Constants.KEY_ASSISTANT_BUBBLE)
    private val avatarModeKey = stringPreferencesKey(Constants.KEY_AVATAR_MODE)
    private val userAvatarPathKey = stringPreferencesKey(Constants.KEY_USER_AVATAR_PATH)
    private val assistantAvatarPathKey = stringPreferencesKey(Constants.KEY_ASSISTANT_AVATAR_PATH)
    private val backgroundModeKey = stringPreferencesKey(Constants.KEY_BACKGROUND_MODE)
    private val backgroundPathKey = stringPreferencesKey(Constants.KEY_BACKGROUND_PATH)
    private val backgroundDimKey = floatPreferencesKey(Constants.KEY_BACKGROUND_DIM)
    private val imageVersionKey = longPreferencesKey(Constants.KEY_IMAGE_VERSION)
    private val showUserAvatarKey =
        booleanPreferencesKey(Constants.KEY_SHOW_USER_AVATAR)
    private val showAssistantAvatarKey =
        booleanPreferencesKey(Constants.KEY_SHOW_ASSISTANT_AVATAR)
    private val transparentTopBarKey =
        booleanPreferencesKey(Constants.KEY_TRANSPARENT_TOP_BAR)
    private val transparentInputBarKey =
        booleanPreferencesKey(Constants.KEY_TRANSPARENT_INPUT_BAR)

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
                apiKey = active.apiKey,
                model = active.effectiveModel,
                baseUrl = normalizeBaseUrl(active.effectiveBaseUrl, active.provider),
                streamEnabled = prefs[streamKey] ?: true,
                thinkingEffort = ThinkingEffort.fromId(prefs[thinkingEffortKey])
            )
        } else {
            val provider = AiProvider.fromId(prefs[providerKey])
            AppSettings(
                provider = provider,
                apiKey = readApiKey(prefs, provider),
                model = readModel(prefs, provider),
                baseUrl = readBaseUrl(prefs, provider),
                streamEnabled = prefs[streamKey] ?: true,
                thinkingEffort = ThinkingEffort.fromId(prefs[thinkingEffortKey])
            )
        }
    }

    /** 常驻缓存，供拦截器与传输层同步取值。 */
    val settings: StateFlow<AppSettings> = settingsFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings()
    )

    val uiPreferencesFlow: Flow<UiPreferences> = dataStore.data.map { prefs ->
        UiPreferences(
            colorMode = ColorMode.fromId(prefs[colorModeKey]),
            dynamicColor = prefs[dynamicColorKey] ?: true,
            messageDisplay = MessageDisplayOptions(
                showTimestamp = prefs[showTimestampKey] ?: true,
                showActions = prefs[showActionsKey] ?: true,
                showModelName = prefs[showModelNameKey] ?: false,
                showTokenUsage = prefs[showTokenUsageKey] ?: false,
                showSpeed = prefs[showSpeedKey] ?: false,
                showLatency = prefs[showLatencyKey] ?: false
            ),
            // 未设置过时给内置默认值；用户清空后存入空串，此时应保持为空
            quickPrompts = prefs[quickPromptsKey]
                ?.split('\n')
                ?.filter { it.isNotBlank() }
                ?: DefaultQuickPrompts,
            shufflePrompts = prefs[shufflePromptsKey] ?: true,
            palette = ThemePalette.fromId(prefs[paletteKey]),
            chatAppearance = ChatAppearance(
                userBubble = BubbleStyle.fromId(prefs[userBubbleKey], BubbleStyle.PLAIN),
                assistantBubble = BubbleStyle.fromId(
                    prefs[assistantBubbleKey],
                    BubbleStyle.PLAIN
                ),
                // 旧版默认值 NONE 会写进 DataStore，仅改默认值对已装设备无效。
                // 未迁移过的设备把存下的 NONE 视作未设置一次，
                // 迁移标记由 migrateAvatarModeIfNeeded 落盘，之后正常读取
                avatarMode = resolveAvatarMode(prefs),
                userAvatarPath = prefs[userAvatarPathKey]?.takeIf { it.isNotBlank() },
                assistantAvatarPath = prefs[assistantAvatarPathKey]?.takeIf { it.isNotBlank() },
                backgroundMode = ChatBackgroundMode.fromId(prefs[backgroundModeKey]),
                backgroundPath = prefs[backgroundPathKey]?.takeIf { it.isNotBlank() },
                backgroundDim = prefs[backgroundDimKey] ?: 0.35f,
                showUserAvatar = prefs[showUserAvatarKey] ?: true,
                showAssistantAvatar = prefs[showAssistantAvatarKey] ?: true,
                transparentTopBar = prefs[transparentTopBarKey] ?: true,
                transparentInputBar = prefs[transparentInputBarKey] ?: false,
                imageVersion = prefs[imageVersionKey] ?: 0L
            )
        )
    }

    /**
     * 界面偏好的常驻缓存。
     *
     * 主题需要在 Activity 内容组装前就拿到值，因此同样用 Eagerly。
     */
    val uiPreferences: StateFlow<UiPreferences> = uiPreferencesFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = UiPreferences()
    )

    suspend fun setColorMode(mode: ColorMode) {
        dataStore.edit { it[colorModeKey] = mode.id }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[dynamicColorKey] = enabled }
    }

    suspend fun setMessageDisplay(options: MessageDisplayOptions) {
        dataStore.edit { prefs ->
            prefs[showTimestampKey] = options.showTimestamp
            prefs[showActionsKey] = options.showActions
            prefs[showModelNameKey] = options.showModelName
            prefs[showTokenUsageKey] = options.showTokenUsage
            prefs[showSpeedKey] = options.showSpeed
            prefs[showLatencyKey] = options.showLatency
        }
    }

    suspend fun setQuickPrompts(prompts: List<String>) {
        dataStore.edit { prefs ->
            prefs[quickPromptsKey] = prompts
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
        }
    }

    suspend fun setShufflePrompts(enabled: Boolean) {
        dataStore.edit { it[shufflePromptsKey] = enabled }
    }

    suspend fun setPalette(palette: ThemePalette) {
        dataStore.edit { it[paletteKey] = palette.id }
    }

    suspend fun setUserBubble(style: BubbleStyle) {
        dataStore.edit { it[userBubbleKey] = style.id }
    }

    suspend fun setAssistantBubble(style: BubbleStyle) {
        dataStore.edit { it[assistantBubbleKey] = style.id }
    }

    suspend fun setAvatarMode(mode: AvatarMode) {
        dataStore.edit { it[avatarModeKey] = mode.id }
    }

    suspend fun setBackgroundMode(mode: ChatBackgroundMode) {
        dataStore.edit { it[backgroundModeKey] = mode.id }
    }

    suspend fun setBackgroundDim(value: Float) {
        dataStore.edit { it[backgroundDimKey] = value.coerceIn(0f, 1f) }
    }

    /**
     * 解析头像模式。
     *
     * 旧存值里的 NONE 规整为 BUILTIN：该项已不作为可选项，
     * 保留它会让 avatarMode == CUSTOM 的判断落空，自定义图片用不上。
     * 是否显示头像现在由两个独立开关决定，与这里无关。
     *
     * 不再依赖迁移标记——那个方案在"标记已置而值未改"的状态下
     * 无法自愈，而这种状态确实会出现。这里每次读取都规整，无状态可言。
     */
    private fun resolveAvatarMode(prefs: Preferences): AvatarMode {
        val mode = AvatarMode.fromId(prefs[avatarModeKey])
        return if (mode == AvatarMode.NONE) AvatarMode.BUILTIN else mode
    }

    suspend fun setShowUserAvatar(enabled: Boolean) {
        dataStore.edit { it[showUserAvatarKey] = enabled }
    }

    suspend fun setShowAssistantAvatar(enabled: Boolean) {
        dataStore.edit { it[showAssistantAvatarKey] = enabled }
    }

    suspend fun setTransparentTopBar(enabled: Boolean) {
        dataStore.edit { it[transparentTopBarKey] = enabled }
    }

    suspend fun setTransparentInputBar(enabled: Boolean) {
        dataStore.edit { it[transparentInputBarKey] = enabled }
    }

    /**
     * 记录图片路径并递增版本号。
     *
     * 路径与版本号必须在同一次 edit 内更新，否则渲染层可能读到
     * 新路径配旧版本号（或反之），导致图片不刷新。
     *
     * [path] 为 null 表示清除该图片。
     */
    suspend fun setUserAvatarPath(path: String?) {
        dataStore.edit { prefs ->
            prefs[userAvatarPathKey] = path.orEmpty()
            prefs[imageVersionKey] = (prefs[imageVersionKey] ?: 0L) + 1
        }
    }

    suspend fun setAssistantAvatarPath(path: String?) {
        dataStore.edit { prefs ->
            prefs[assistantAvatarPathKey] = path.orEmpty()
            prefs[imageVersionKey] = (prefs[imageVersionKey] ?: 0L) + 1
        }
    }

    suspend fun setBackgroundPath(path: String?) {
        dataStore.edit { prefs ->
            prefs[backgroundPathKey] = path.orEmpty()
            prefs[imageVersionKey] = (prefs[imageVersionKey] ?: 0L) + 1
        }
    }

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
