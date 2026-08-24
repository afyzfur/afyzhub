package com.afyzfur.afyzhub.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afyzfur.afyzhub.data.settings.ColorMode
import com.afyzfur.afyzhub.data.settings.MessageDisplayOptions
import com.afyzfur.afyzhub.data.settings.SettingsRepository
import com.afyzfur.afyzhub.data.settings.UiPreferences
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
    private val repository: SettingsRepository
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
}
