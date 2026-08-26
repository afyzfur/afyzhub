package com.afyzfur.afyzhub.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afyzfur.afyzhub.data.remote.provider.ChatClientRegistry
import com.afyzfur.afyzhub.data.remote.provider.ChatTurn
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
/**
 * 连接测试的结果。
 *
 * 成功时带上实际用到的模型与耗时：中转的响应速度差异很大，
 * 这个数字能帮用户判断是否值得继续用这家。
 */
sealed interface TestResult {
    data class Success(val model: String, val elapsedMs: Long) : TestResult
    data class Failure(val reason: String) : TestResult
}

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

    /**
     * 连接测试的结果。null 表示尚未测试过。
     *
     * 与 [error] 分开：那个是拉取模型列表的失败，两者可能同时存在
     * 且原因不同，合用一个字段会互相覆盖。
     */
    private val _testResult = MutableStateFlow<TestResult?>(null)
    val testResult: StateFlow<TestResult?> = _testResult.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    /**
     * 发一次最小的对话请求，验证这组配置能否真正用起来。
     *
     * 用 complete 而非 listModels：能列出模型不代表能对话——中转常有
     * 模型列表可读但对话额度已耗尽、或该模型未授权的情况，
     * 而后者才是用户实际会遇到的失败。
     */
    fun testConnection(profile: ApiProfile) {
        if (_testing.value) return
        if (profile.apiKey.isBlank()) {
            _testResult.value = TestResult.Failure("请先填写 API Key")
            return
        }
        viewModelScope.launch {
            _testing.value = true
            _testResult.value = null
            val startedAt = System.currentTimeMillis()
            try {
                val probe = AppSettings(
                    provider = profile.provider,
                    apiKey = profile.apiKey,
                    model = profile.effectiveModel,
                    baseUrl = normalizeUrl(profile.effectiveBaseUrl)
                )
                val result = clientRegistry.clientFor(profile.provider).complete(
                    // 内容尽量短，只为确认链路通畅，不浪费额度
                    turns = listOf(ChatTurn(role = "user", content = "hi")),
                    settings = probe
                )
                val elapsed = System.currentTimeMillis() - startedAt
                _testResult.value = if (result.content.isBlank()) {
                    // 请求成功但没有内容，多见于模型名不被服务端接受
                    TestResult.Failure("请求成功但未返回内容，请检查模型名是否正确")
                } else {
                    TestResult.Success(
                        model = probe.model,
                        elapsedMs = elapsed
                    )
                }
            } catch (e: Exception) {
                _testResult.value = TestResult.Failure(
                    e.message ?: "请求失败，原因未知"
                )
            } finally {
                _testing.value = false
            }
        }
    }

    fun clearTestResult() {
        _testResult.value = null
    }

    /** 地址必须以 / 结尾，后续拼接路径依赖这一点 */
    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
