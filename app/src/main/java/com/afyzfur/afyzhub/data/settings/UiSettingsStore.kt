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

/** Appearance and image preferences; never owns credential migration. */
internal class UiSettingsStore(private val dataStore: DataStore<Preferences>, scope: CoroutineScope) : UiSettingsAccess {
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
    private val chatAnimationKey = stringPreferencesKey("chat_animation")
    private val transparentTopBarKey =
        booleanPreferencesKey(Constants.KEY_TRANSPARENT_TOP_BAR)
    private val transparentInputBarKey =
        booleanPreferencesKey(Constants.KEY_TRANSPARENT_INPUT_BAR)
    private val backgroundEffectKey = stringPreferencesKey(Constants.KEY_BACKGROUND_EFFECT)
    private val backgroundBlurKey = floatPreferencesKey(Constants.KEY_BACKGROUND_BLUR)
    private val avatarBlurKey = floatPreferencesKey(Constants.KEY_AVATAR_BLUR)
    private val inputBarSeeThroughKey =
        floatPreferencesKey(Constants.KEY_INPUT_BAR_SEE_THROUGH)
    private val inputBarFloatingKey =
        booleanPreferencesKey(Constants.KEY_INPUT_BAR_FLOATING)
    private val inputBarDeepSeeThroughKey =
        booleanPreferencesKey(Constants.KEY_INPUT_BAR_DEEP_SEE_THROUGH)
    override val uiPreferencesFlow: Flow<UiPreferences> = dataStore.data.map { prefs ->
        UiPreferences(
            colorMode = ColorMode.fromId(prefs[colorModeKey]),
            dynamicColor = prefs[dynamicColorKey] ?: true,
            messageDisplay = MessageDisplayOptions(
                showTimestamp = prefs[showTimestampKey] ?: true,
                showActions = prefs[showActionsKey] ?: true,
                showModelName = prefs[showModelNameKey] ?: false,
                showTokenUsage = prefs[showTokenUsageKey] ?: true,
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
                 animation = ChatAnimation.fromId(prefs[chatAnimationKey]),
                // 默认值写在这里而非只改 ChatAppearance 的构造默认：
                // 未设置过该键时用的是这个 fallback，data class 的默认值
                // 只在别处直接构造 ChatAppearance 时才起作用
                userBubble = BubbleStyle.fromId(prefs[userBubbleKey], BubbleStyle.BUBBLE),
                assistantBubble = BubbleStyle.fromId(
                    prefs[assistantBubbleKey],
                    BubbleStyle.BUBBLE
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
                inputBarSeeThrough = prefs[inputBarSeeThroughKey] ?: 0.35f,
                inputBarFloating = prefs[inputBarFloatingKey] ?: false,
                inputBarDeepSeeThrough = prefs[inputBarDeepSeeThroughKey] ?: false,
                backgroundEffect = ChatBackgroundEffect.fromId(prefs[backgroundEffectKey]),
                backgroundBlur = prefs[backgroundBlurKey] ?: 0.4f,
                avatarBlur = prefs[avatarBlurKey] ?: 0f,
                imageVersion = prefs[imageVersionKey] ?: 0L
            )
        )
    }

    /**
     * 界面偏好的常驻缓存。
     *
     * 主题需要在 Activity 内容组装前就拿到值，因此同样用 Eagerly。
     */
    override val uiPreferences: StateFlow<UiPreferences> = uiPreferencesFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = UiPreferences()
    )

    override suspend fun setColorMode(mode: ColorMode) {
        dataStore.edit { it[colorModeKey] = mode.id }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[dynamicColorKey] = enabled }
    }

    override suspend fun setMessageDisplay(options: MessageDisplayOptions) {
        dataStore.edit { prefs ->
            prefs[showTimestampKey] = options.showTimestamp
            prefs[showActionsKey] = options.showActions
            prefs[showModelNameKey] = options.showModelName
            prefs[showTokenUsageKey] = options.showTokenUsage
            prefs[showSpeedKey] = options.showSpeed
            prefs[showLatencyKey] = options.showLatency
        }
    }

    override suspend fun setQuickPrompts(prompts: List<String>) {
        dataStore.edit { prefs ->
            prefs[quickPromptsKey] = prompts
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
        }
    }

    override suspend fun setShufflePrompts(enabled: Boolean) {
        dataStore.edit { it[shufflePromptsKey] = enabled }
    }

    override suspend fun setPalette(palette: ThemePalette) {
        dataStore.edit { it[paletteKey] = palette.id }
    }

    override suspend fun setChatAnimation(animation: ChatAnimation) { dataStore.edit { it[chatAnimationKey] = animation.id } }
    override suspend fun setUserBubble(style: BubbleStyle) {
        dataStore.edit { it[userBubbleKey] = style.id }
    }

    override suspend fun setAssistantBubble(style: BubbleStyle) {
        dataStore.edit { it[assistantBubbleKey] = style.id }
    }

    override suspend fun setAvatarMode(mode: AvatarMode) {
        dataStore.edit { it[avatarModeKey] = mode.id }
    }

    override suspend fun setBackgroundMode(mode: ChatBackgroundMode) {
        dataStore.edit { it[backgroundModeKey] = mode.id }
    }

    override suspend fun setBackgroundDim(value: Float) {
        dataStore.edit { it[backgroundDimKey] = value.coerceIn(0f, 1f) }
    }

    override suspend fun setBackgroundEffect(effect: ChatBackgroundEffect) {
        dataStore.edit { it[backgroundEffectKey] = effect.id }
    }

    override suspend fun setBackgroundBlur(value: Float) {
        dataStore.edit { it[backgroundBlurKey] = value.coerceIn(0f, 1f) }
    }

    override suspend fun setAvatarBlur(value: Float) {
        dataStore.edit { it[avatarBlurKey] = value.coerceIn(0f, 1f) }
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

    override suspend fun setShowUserAvatar(enabled: Boolean) {
        dataStore.edit { it[showUserAvatarKey] = enabled }
    }

    override suspend fun setShowAssistantAvatar(enabled: Boolean) {
        dataStore.edit { it[showAssistantAvatarKey] = enabled }
    }

    override suspend fun setTransparentTopBar(enabled: Boolean) {
        dataStore.edit { it[transparentTopBarKey] = enabled }
    }

    override suspend fun setTransparentInputBar(enabled: Boolean) {
        dataStore.edit { it[transparentInputBarKey] = enabled }
    }

    override suspend fun setInputBarSeeThrough(value: Float) {
        dataStore.edit { it[inputBarSeeThroughKey] = value.coerceIn(0f, 1f) }
    }

    override suspend fun setInputBarFloating(enabled: Boolean) {
        dataStore.edit { it[inputBarFloatingKey] = enabled }
    }

    override suspend fun setInputBarDeepSeeThrough(enabled: Boolean) {
        dataStore.edit { it[inputBarDeepSeeThroughKey] = enabled }
    }

    /**
     * 记录图片路径并递增版本号。
     *
     * 路径与版本号必须在同一次 edit 内更新，否则渲染层可能读到
     * 新路径配旧版本号（或反之），导致图片不刷新。
     *
     * [path] 为 null 表示清除该图片。
     */
    override suspend fun setUserAvatarPath(path: String?) {
        dataStore.edit { prefs ->
            prefs[userAvatarPathKey] = path.orEmpty()
            prefs[imageVersionKey] = (prefs[imageVersionKey] ?: 0L) + 1
        }
    }

    override suspend fun setAssistantAvatarPath(path: String?) {
        dataStore.edit { prefs ->
            prefs[assistantAvatarPathKey] = path.orEmpty()
            prefs[imageVersionKey] = (prefs[imageVersionKey] ?: 0L) + 1
        }
    }

    override suspend fun setBackgroundPath(path: String?) {
        dataStore.edit { prefs ->
            prefs[backgroundPathKey] = path.orEmpty()
            prefs[imageVersionKey] = (prefs[imageVersionKey] ?: 0L) + 1
        }
    }

}
