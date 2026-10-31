package com.afyzfur.afyzhub.data.settings

import com.afyzfur.afyzhub.ui.theme.ThemePalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Stable public facade; UI preferences are persisted independently of API credentials. */
interface UiSettingsAccess {
    val uiPreferencesFlow: Flow<UiPreferences>
    val uiPreferences: StateFlow<UiPreferences>
    suspend fun setColorMode(mode: ColorMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setMessageDisplay(options: MessageDisplayOptions)
    suspend fun setQuickPrompts(prompts: List<String>)
    suspend fun setShufflePrompts(enabled: Boolean)
    suspend fun setPalette(palette: ThemePalette)
    suspend fun setChatAnimation(animation: ChatAnimation)
    suspend fun setUserBubble(style: BubbleStyle)
    suspend fun setAssistantBubble(style: BubbleStyle)
    suspend fun setAvatarMode(mode: AvatarMode)
    suspend fun setBackgroundMode(mode: ChatBackgroundMode)
    suspend fun setBackgroundDim(value: Float)
    suspend fun setBackgroundEffect(effect: ChatBackgroundEffect)
    suspend fun setBackgroundBlur(value: Float)
    suspend fun setAvatarBlur(value: Float)
    suspend fun setShowUserAvatar(enabled: Boolean)
    suspend fun setShowAssistantAvatar(enabled: Boolean)
    suspend fun setTransparentTopBar(enabled: Boolean)
    suspend fun setTransparentInputBar(enabled: Boolean)
    suspend fun setInputBarSeeThrough(value: Float)
    suspend fun setInputBarFloating(enabled: Boolean)
    suspend fun setInputBarDeepSeeThrough(enabled: Boolean)
    suspend fun setUserAvatarPath(path: String?)
    suspend fun setAssistantAvatarPath(path: String?)
    suspend fun setBackgroundPath(path: String?)
}
