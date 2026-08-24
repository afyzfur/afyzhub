package com.afyzfur.afyzhub.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afyzfur.afyzhub.data.image.ImageStore
import com.afyzfur.afyzhub.data.settings.AvatarMode
import com.afyzfur.afyzhub.data.settings.BubbleStyle
import com.afyzfur.afyzhub.data.settings.ChatBackgroundMode
import com.afyzfur.afyzhub.data.settings.ColorMode
import com.afyzfur.afyzhub.data.settings.MessageDisplayOptions
import com.afyzfur.afyzhub.data.settings.SettingsRepository
import com.afyzfur.afyzhub.data.settings.UiPreferences
import com.afyzfur.afyzhub.ui.theme.ThemePalette
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 界面偏好的读写。
 *
 * 与 [SettingsViewModel] 分开：那边管的是提供商凭证与地址，输入过程中需要
 * 600ms 防抖再落盘；这里全是开关与选项，点一下就该立即生效，
 * 混在一起会让防抖逻辑的适用范围变得含糊。
 */
class UiPreferencesViewModel(
    private val repository: SettingsRepository,
    private val imageStore: ImageStore
) : ViewModel() {

    val preferences: StateFlow<UiPreferences> = repository.uiPreferences

    fun setColorMode(mode: ColorMode) {
        viewModelScope.launch { repository.setColorMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColor(enabled) }
    }

    /**
     * 更新消息元信息开关。
     *
     * 接收整个 options 而非单个字段：DataStore 的一次 edit 写入全部键，
     * 逐字段方法会导致同一次交互产生多次磁盘写入。
     */
    fun setMessageDisplay(options: MessageDisplayOptions) {
        viewModelScope.launch { repository.setMessageDisplay(options) }
    }

    fun setShufflePrompts(enabled: Boolean) {
        viewModelScope.launch { repository.setShufflePrompts(enabled) }
    }

    fun setQuickPrompts(prompts: List<String>) {
        viewModelScope.launch { repository.setQuickPrompts(prompts) }
    }

    fun setPalette(palette: ThemePalette) {
        viewModelScope.launch { repository.setPalette(palette) }
    }

    fun setUserBubble(style: BubbleStyle) {
        viewModelScope.launch { repository.setUserBubble(style) }
    }

    fun setAssistantBubble(style: BubbleStyle) {
        viewModelScope.launch { repository.setAssistantBubble(style) }
    }

    fun setAvatarMode(mode: AvatarMode) {
        viewModelScope.launch { repository.setAvatarMode(mode) }
    }

    fun setBackgroundMode(mode: ChatBackgroundMode) {
        viewModelScope.launch { repository.setBackgroundMode(mode) }
    }

    fun setBackgroundDim(value: Float) {
        viewModelScope.launch { repository.setBackgroundDim(value) }
    }

    /**
     * 保存用户选择的图片。
     *
     * 先复制到私有目录再记录路径——两步都在同一个协程内顺序完成，
     * 避免路径已写入但文件尚未落盘的中间状态。
     * 复制失败时不改动配置，界面保持原样。
     *
     * 选择自定义图片后自动把对应模式切到 CUSTOM / IMAGE，
     * 否则用户选完图却看不到变化，还得再点一次模式。
     */
    fun pickUserAvatar(uri: Uri) {
        viewModelScope.launch {
            imageStore.save(uri, ImageStore.Purpose.USER_AVATAR)?.let { path ->
                repository.setUserAvatarPath(path)
                repository.setAvatarMode(AvatarMode.CUSTOM)
            }
        }
    }

    fun pickAssistantAvatar(uri: Uri) {
        viewModelScope.launch {
            imageStore.save(uri, ImageStore.Purpose.ASSISTANT_AVATAR)?.let { path ->
                repository.setAssistantAvatarPath(path)
                repository.setAvatarMode(AvatarMode.CUSTOM)
            }
        }
    }

    fun pickBackground(uri: Uri) {
        viewModelScope.launch {
            imageStore.save(uri, ImageStore.Purpose.CHAT_BACKGROUND)?.let { path ->
                repository.setBackgroundPath(path)
                repository.setBackgroundMode(ChatBackgroundMode.IMAGE)
            }
        }
    }

    fun clearUserAvatar() {
        viewModelScope.launch {
            imageStore.delete(ImageStore.Purpose.USER_AVATAR)
            repository.setUserAvatarPath(null)
        }
    }

    fun clearAssistantAvatar() {
        viewModelScope.launch {
            imageStore.delete(ImageStore.Purpose.ASSISTANT_AVATAR)
            repository.setAssistantAvatarPath(null)
        }
    }

    /** 清除背景图并切回跟随主题，否则会停在图片模式但无图可显示 */
    fun clearBackground() {
        viewModelScope.launch {
            imageStore.delete(ImageStore.Purpose.CHAT_BACKGROUND)
            repository.setBackgroundPath(null)
            repository.setBackgroundMode(ChatBackgroundMode.NONE)
        }
    }
}
