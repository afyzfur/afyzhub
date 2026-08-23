package com.afyzfur.afyzhub.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afyzfur.afyzhub.data.remote.provider.ChatClientRegistry
import com.afyzfur.afyzhub.data.settings.SettingsRepository
import com.afyzfur.afyzhub.domain.model.AiProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    /** 自动保存完成的提示，展示后由界面清除。 */
    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    /**
     * 自动保存的防抖任务。
     *
     * 输入过程中每次按键都写盘没有必要，延迟合并后再落盘；
     * 同时避免打字中途把半截的 Key 存进去。
     */
    private var autoSaveJob: Job? = null

    /** 初始化回填期间不触发自动保存，否则会把默认值当作用户输入写盘。 */
    private var initialized = false

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
            initialized = true
        }
    }

    /**
     * 安排一次延迟保存。
     *
     * 取消上一次未执行的任务，实现防抖；用户停止输入后自动落盘，
     * 不再需要手动点保存。
     */
    private fun scheduleAutoSave() {
        if (!initialized) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DELAY_MS)
            persist()
        }
    }

    /** 立即落盘，用于切换提供商等需要即时生效的场景。 */
    private fun saveNow() {
        if (!initialized) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch { persist() }
    }

    private suspend fun persist() {
        settingsRepository.save(
            provider = _provider.value,
            apiKey = _apiKey.value,
            model = _selectedModel.value,
            baseUrl = _baseUrl.value,
            streamEnabled = _streamEnabled.value
        )
        _saveSuccess.value = true
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
            // 先把当前提供商的未落盘改动存下来，再切换，避免输入丢失。
            autoSaveJob?.cancel()
            if (initialized) persist()

            val config = settingsRepository.configFor(target)
            _provider.value = target
            _apiKey.value = config.apiKey
            _selectedModel.value = config.model
            _baseUrl.value = config.baseUrl
            _availableModels.value = settingsRepository.cachedModels(target)
            _modelsError.value = null
            // 记录当前提供商，聊天时才会走对应的客户端。
            persist()
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
        // 开关类改动无需防抖，立即生效。
        saveNow()
    }

    fun updateApiKey(value: String) {
        _apiKey.value = value
        scheduleAutoSave()
    }

    fun updateModel(value: String) {
        _selectedModel.value = value
        scheduleAutoSave()
    }

    fun updateBaseUrl(value: String) {
        _baseUrl.value = value
        scheduleAutoSave()
    }

    /** 恢复当前提供商的默认 API 地址。 */
    fun resetBaseUrl() {
        _baseUrl.value = _provider.value.defaultBaseUrl
        saveNow()
    }

    /**
     * 离开设置页时调用，确保防抖窗口内的改动不会丢失。
     */
    fun flushPendingChanges() {
        if (!initialized) return
        autoSaveJob?.cancel()
        viewModelScope.launch { persist() }
    }

    fun clearSaveSuccess() {
        _saveSuccess.value = false
    }

    private companion object {
        /** 停止输入后多久落盘，兼顾及时性与写入次数。 */
        const val AUTO_SAVE_DELAY_MS = 600L
    }
}
