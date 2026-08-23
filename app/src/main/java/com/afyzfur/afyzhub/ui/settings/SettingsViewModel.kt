package com.afyzfur.afyzhub.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afyzfur.afyzhub.data.remote.provider.ChatClientRegistry
import com.afyzfur.afyzhub.data.settings.SettingsRepository
import com.afyzfur.afyzhub.domain.model.AiProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val clientRegistry: ChatClientRegistry
) : ViewModel() {

    private val _provider = MutableStateFlow(AiProvider.DEFAULT)
    val provider: StateFlow<AiProvider> = _provider.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(AiProvider.DEFAULT.fallbackModel)
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _baseUrl = MutableStateFlow(AiProvider.DEFAULT.defaultBaseUrl)
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _streamEnabled = MutableStateFlow(true)
    val streamEnabled: StateFlow<Boolean> = _streamEnabled.asStateFlow()

    /** 当前提供商的可用模型，来自缓存或最近一次拉取。 */
    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _loadingModels = MutableStateFlow(false)
    val loadingModels: StateFlow<Boolean> = _loadingModels.asStateFlow()

    /** 模型列表拉取失败的原因，成功或未拉取时为 null。 */
    private val _modelsError = MutableStateFlow<String?>(null)
    val modelsError: StateFlow<String?> = _modelsError.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            _provider.value = settings.provider
            _apiKey.value = settings.apiKey
            _selectedModel.value = settings.model
            _baseUrl.value = settings.baseUrl
            _streamEnabled.value = settings.streamEnabled
            // 直接展示上次拉取的结果，避免每次进入设置页都要重新获取。
            _availableModels.value = settingsRepository.cachedModels(settings.provider)
        }
    }

    /**
     * 切换提供商。
     *
     * 各提供商的 Key、模型和地址独立存储，切换时回填该提供商自己的配置，
     * 不会把当前输入串到另一家去。
     */
    fun selectProvider(target: AiProvider) {
        if (target == _provider.value) return
        viewModelScope.launch {
            val config = settingsRepository.configFor(target)
            _provider.value = target
            _apiKey.value = config.apiKey
            _selectedModel.value = config.model
            _baseUrl.value = config.baseUrl
            _availableModels.value = settingsRepository.cachedModels(target)
            _modelsError.value = null
        }
    }

    /**
     * 拉取当前提供商的可用模型。
     *
     * 由用户主动触发而非进入页面自动执行，因为未填 Key 时拉取必然失败。
     * 成功结果会缓存，重进页面或重启应用后依然可见。
     */
    fun refreshModels() {
        if (_loadingModels.value) return
        val key = _apiKey.value.trim()
        if (key.isBlank()) {
            _modelsError.value = "请先填写 API Key"
            return
        }
        viewModelScope.launch {
            _loadingModels.value = true
            _modelsError.value = null
            try {
                val provider = _provider.value
                // 用界面上的当前输入去拉取，而不是已保存的值，
                // 这样用户改完 Key 或地址可以立刻验证，无需先保存。
                val probe = settingsRepository.configFor(provider).copy(
                    apiKey = key,
                    baseUrl = _baseUrl.value.trim().ifBlank { provider.defaultBaseUrl }
                )
                val models = clientRegistry.clientFor(provider).listModels(probe)
                if (models.isEmpty()) {
                    _modelsError.value = "接口未返回任何模型"
                } else {
                    _availableModels.value = models
                    settingsRepository.saveModels(provider, models)
                }
            } catch (e: Exception) {
                _modelsError.value = e.message ?: "获取模型列表失败"
            } finally {
                _loadingModels.value = false
            }
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

    /** 恢复当前提供商的默认 API 地址。 */
    fun resetBaseUrl() {
        _baseUrl.value = _provider.value.defaultBaseUrl
    }

    fun saveSettings() {
        viewModelScope.launch {
            settingsRepository.save(
                provider = _provider.value,
                apiKey = _apiKey.value,
                model = _selectedModel.value,
                baseUrl = _baseUrl.value,
                streamEnabled = _streamEnabled.value
            )
            // 保存时会规范化地址与模型，回读以保持界面与实际生效值一致。
            val saved = settingsRepository.settingsFlow.first()
            _baseUrl.value = saved.baseUrl
            _selectedModel.value = saved.model
            _saveSuccess.value = true
        }
    }

    fun clearSaveSuccess() {
        _saveSuccess.value = false
    }
}
