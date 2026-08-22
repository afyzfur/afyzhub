package com.afyzfur.afyzhub.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afyzfur.afyzhub.data.settings.SettingsRepository
import com.afyzfur.afyzhub.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(Constants.DEFAULT_MODEL)
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _baseUrl = MutableStateFlow(Constants.DEFAULT_BASE_URL)
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _streamEnabled = MutableStateFlow(true)
    val streamEnabled: StateFlow<Boolean> = _streamEnabled.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            _apiKey.value = settings.apiKey
            _selectedModel.value = settings.model
            _baseUrl.value = settings.baseUrl
            _streamEnabled.value = settings.streamEnabled
        }
    }

    fun updateStreamEnabled(value: Boolean) {
        _streamEnabled.value = value
    }

    fun updateApiKey(value: String) {
        _apiKey.value = value
    }

    fun updateModel(value: String) {
        _selectedModel.value = value
    }

    fun updateBaseUrl(value: String) {
        _baseUrl.value = value
    }

    /** 恢复默认 API 地址。 */
    fun resetBaseUrl() {
        _baseUrl.value = Constants.DEFAULT_BASE_URL
    }

    fun saveSettings() {
        viewModelScope.launch {
            settingsRepository.save(
                apiKey = _apiKey.value,
                model = _selectedModel.value,
                baseUrl = _baseUrl.value,
                streamEnabled = _streamEnabled.value
            )
            // 保存时会规范化地址，回读以保持界面与实际生效值一致。
            _baseUrl.value = settingsRepository.settingsFlow.first().baseUrl
            _saveSuccess.value = true
        }
    }

    fun clearSaveSuccess() {
        _saveSuccess.value = false
    }
}
