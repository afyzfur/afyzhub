package com.afyzfur.afyzhub.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afyzfur.afyzhub.data.remote.provider.ChatClientRegistry
import com.afyzfur.afyzhub.data.settings.AppSettings
import com.afyzfur.afyzhub.domain.model.ApiProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 按配置组拉取模型列表。
 *
 * 与 [ApiProfilesViewModel] 分开：那个只管配置的读写，不需要依赖
 * 网络层。拉取是个带加载态和错误态的独立过程，混进去会让一个类
 * 同时承担存储与网络两件事。
 *
 * 结果通过回调交回调用方写入对应的组，而不是在这里直接改配置——
 * 拉取者不该关心结果存到哪。
 */
class ProfileModelsViewModel(
    private val clientRegistry: ChatClientRegistry
) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * 用 [profile] 当前的值去拉取，而不是已保存的配置。
     *
     * 这样用户改完 Key 或地址可以立刻验证，不必先返回再进来。
     */
    fun fetchModels(profile: ApiProfile, onSuccess: (List<String>) -> Unit) {
        if (_loading.value) return
        if (profile.apiKey.isBlank()) {
            _error.value = "请先填写 API Key"
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val probe = AppSettings(
                    provider = profile.provider,
                    apiKey = profile.apiKey,
                    model = profile.effectiveModel,
                    baseUrl = normalizeUrl(profile.effectiveBaseUrl)
                )
                val models = clientRegistry.clientFor(profile.provider).listModels(probe)
                if (models.isEmpty()) {
                    _error.value = "接口未返回任何模型"
                } else {
                    onSuccess(models)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "获取模型列表失败"
            } finally {
                _loading.value = false
            }
        }
    }

    /** 地址必须以 / 结尾，后续拼接路径依赖这一点 */
    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
