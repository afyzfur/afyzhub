package com.afyzhub.aichat

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.afyzhub.aichat.data.ApiConfig
import com.afyzhub.aichat.data.ApiKeyStore
import com.afyzhub.aichat.data.AppState
import com.afyzhub.aichat.data.ChatMessage
import com.afyzhub.aichat.data.Conversation
import com.afyzhub.aichat.data.LocalStore
import com.afyzhub.aichat.data.OpenAiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val localStore = LocalStore(application)
    private val apiKeyStore = ApiKeyStore(application)
    private val openAiClient = OpenAiClient()

    private val initialConversations = localStore.loadConversations()
    private val _state = mutableStateOf(
        AppState(
            conversations = initialConversations,
            selectedConversationId = initialConversations.firstOrNull()?.id,
            config = localStore.loadConfig(),
            hasApiKey = apiKeyStore.load().isNotBlank()
        )
    )
    val state: State<AppState> = _state

    fun dismissNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    fun newConversation() {
        val conversation = Conversation()
        val updated = listOf(conversation) + _state.value.conversations
        _state.value = _state.value.copy(
            conversations = updated,
            selectedConversationId = conversation.id
        )
        persist(updated)
    }

    fun selectConversation(id: String) {
        _state.value = _state.value.copy(selectedConversationId = id)
    }

    fun renameConversation(id: String, title: String) {
        val updated = _state.value.conversations.map {
            if (it.id == id) it.copy(title = title.trim().ifBlank { "新对话" }) else it
        }
        _state.value = _state.value.copy(conversations = updated)
        persist(updated)
    }

    fun deleteConversation(id: String) {
        val updated = _state.value.conversations.filterNot { it.id == id }
        _state.value = _state.value.copy(
            conversations = updated,
            selectedConversationId = updated.firstOrNull()?.id
        )
        persist(updated)
    }

    /**
     * 即时更新外观相关配置（主题色 / 输入栏样式），无需点保存、不做 URL 校验。
     * 用于设置页里切换选项时的实时预览与持久化。
     */
    fun updateAppearance(themeSeedColor: Long?, inputBarStyle: com.afyzhub.aichat.data.InputBarStyle) {
        val updated = _state.value.config.copy(
            themeSeedColor = themeSeedColor,
            inputBarStyle = inputBarStyle
        )
        localStore.saveConfig(updated)
        _state.value = _state.value.copy(config = updated)
    }

    fun saveConfig(config: ApiConfig, newApiKey: String) {
        var baseUrl = config.baseUrl.trim().trimEnd('/')
        // 自动补全版本路径：若末尾没有 /v1、/v2 等，自动追加 /v1
        if (!baseUrl.matches(Regex(".*(/v\\d+)$"))) {
            baseUrl += "/v1"
        }
        val normalized = config.copy(
            baseUrl = baseUrl,
            model = config.model.trim(),
            maxTokens = config.maxTokens.coerceAtLeast(1)
        )
        if (!normalized.baseUrl.startsWith("https://")) {
            _state.value = _state.value.copy(
                notice = "为保护 API Key，仅允许使用 HTTPS 地址"
            )
            return
        }
        localStore.saveConfig(normalized)
        if (newApiKey.isNotBlank()) apiKeyStore.save(newApiKey.trim())
        _state.value = _state.value.copy(
            config = normalized,
            hasApiKey = apiKeyStore.load().isNotBlank(),
            notice = "配置已保存"
        )
    }

    fun clearConversations() {
        openAiClient.cancel()
        localStore.clearConversations()
        _state.value = _state.value.copy(
            conversations = emptyList(),
            selectedConversationId = null,
            isGenerating = false,
            notice = "本地会话已清除"
        )
    }

    /** 清除全部数据：会话、配置与加密保存的 API Key。 */
    fun clearAllData() {
        openAiClient.cancel()
        localStore.clearConversations()
        apiKeyStore.clear()
        _state.value = _state.value.copy(
            conversations = emptyList(),
            selectedConversationId = null,
            isGenerating = false,
            hasApiKey = false,
            notice = "全部数据已清除"
        )
    }

    /** 消费 lastFailedInput（供输入框回填后清空）。 */
    fun consumeFailedInput() {
        if (_state.value.lastFailedInput != null) {
            _state.value = _state.value.copy(lastFailedInput = null)
        }
    }

    /**
     * 拉取模型列表。无需先保存配置：
     * overrideConfig 传入设置页当前编辑中的配置，overrideApiKey 传入当前输入框的 key。
     * 若输入框 key 为空，则回退到已保存的 key。
     */
    fun loadModels(overrideConfig: ApiConfig? = null, overrideApiKey: String = "") {
        if (_state.value.isLoadingModels) return
        val apiKey = overrideApiKey.trim().ifBlank { apiKeyStore.load() }
        if (apiKey.isBlank()) {
            _state.value = _state.value.copy(notice = "请先填写 API Key")
            return
        }
        val config = (overrideConfig ?: _state.value.config).let {
            var url = it.baseUrl.trim().trimEnd('/')
            if (!url.matches(Regex(".*(/v\\d+)$"))) url += "/v1"
            it.copy(baseUrl = url)
        }
        if (!config.baseUrl.startsWith("https://")) {
            _state.value = _state.value.copy(notice = "为保护 API Key，仅允许使用 HTTPS 地址")
            return
        }

        _state.value = _state.value.copy(isLoadingModels = true)
        viewModelScope.launch {
            try {
                val models = openAiClient.fetchModels(config, apiKey)
                _state.value = _state.value.copy(
                    availableModels = models,
                    notice = if (models.isEmpty()) "未获取到模型" else "已获取 ${models.size} 个模型"
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    notice = "获取模型失败：${error.message ?: "网络异常"}"
                )
            } finally {
                _state.value = _state.value.copy(isLoadingModels = false)
            }
        }
    }

    fun stopGenerating() {
        openAiClient.cancel()
        // 清理仍为空的助手消息，避免留下空气泡
        val cleaned = _state.value.conversations.map { conversation ->
            conversation.copy(
                messages = conversation.messages.filterNot {
                    it.role == "assistant" && it.content.isBlank()
                }
            )
        }
        _state.value = _state.value.copy(
            conversations = cleaned,
            isGenerating = false,
            notice = "已停止生成"
        )
        persist(cleaned)
    }

    fun sendMessage(input: String) {
        val prompt = input.trim()
        if (prompt.isBlank() || _state.value.isGenerating) return

        val apiKey = apiKeyStore.load()
        if (apiKey.isBlank()) {
            _state.value = _state.value.copy(notice = "请先配置 API Key")
            return
        }

        // 确保存在目标会话：若无则新建并直接使用其 id，避免读取状态的竞态
        val targetId: String
        val existingId = _state.value.selectedConversationId
        if (existingId == null || _state.value.conversations.none { it.id == existingId }) {
            val conversation = Conversation()
            val updated = listOf(conversation) + _state.value.conversations
            _state.value = _state.value.copy(
                conversations = updated,
                selectedConversationId = conversation.id
            )
            persist(updated)
            targetId = conversation.id
        } else {
            targetId = existingId
        }

        val userMessage = ChatMessage(role = "user", content = prompt)
        val assistantMessage = ChatMessage(role = "assistant", content = "")

        val updated = _state.value.conversations.map { conversation ->
            if (conversation.id != targetId) conversation
            else conversation.copy(
                title = if (conversation.messages.isEmpty()) {
                    prompt.take(28)
                } else {
                    conversation.title
                },
                messages = conversation.messages + userMessage + assistantMessage
            )
        }
        _state.value = _state.value.copy(
            conversations = updated,
            isGenerating = true,
            notice = null
        )
        persist(updated)

        viewModelScope.launch {
            try {
                val requestMessages = updated.first { it.id == targetId }
                    .messages
                    .dropLast(1)

                openAiClient.streamChat(
                    config = _state.value.config,
                    apiKey = apiKey,
                    messages = requestMessages
                ) { delta ->
                    val streamed = _state.value.conversations.map { conversation ->
                        if (conversation.id != targetId) conversation
                        else conversation.copy(
                            messages = conversation.messages.map { message ->
                                if (message.id == assistantMessage.id) {
                                    message.copy(content = message.content + delta)
                                } else {
                                    message
                                }
                            }
                        )
                    }
                    _state.value = _state.value.copy(conversations = streamed)
                }
                persist(_state.value.conversations)
            } catch (_: CancellationException) {
                // 用户主动停止时保留已经生成的内容。
            } catch (error: Exception) {
                // 请求失败：移除空助手消息与本轮用户消息，并把输入还给输入框便于重发
                val cleaned = _state.value.conversations.map { conversation ->
                    if (conversation.id != targetId) conversation
                    else conversation.copy(
                        messages = conversation.messages.filterNot {
                            it.id == assistantMessage.id || it.id == userMessage.id
                        }
                    )
                }
                _state.value = _state.value.copy(
                    conversations = cleaned,
                    notice = "请求失败：${error.message ?: "网络异常"}",
                    lastFailedInput = prompt
                )
                persist(cleaned)
            } finally {
                _state.value = _state.value.copy(isGenerating = false)
            }
        }
    }

    private fun persist(conversations: List<Conversation>) {
        localStore.saveConversations(conversations)
    }

    override fun onCleared() {
        openAiClient.cancel()
        super.onCleared()
    }
}