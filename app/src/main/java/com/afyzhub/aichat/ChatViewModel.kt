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

    fun saveConfig(config: ApiConfig, newApiKey: String) {
        val normalized = config.copy(
            baseUrl = config.baseUrl.trim().trimEnd('/'),
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

    fun stopGenerating() {
        openAiClient.cancel()
        _state.value = _state.value.copy(
            isGenerating = false,
            notice = "已停止生成"
        )
    }

    fun sendMessage(input: String) {
        val prompt = input.trim()
        if (prompt.isBlank() || _state.value.isGenerating) return

        val apiKey = apiKeyStore.load()
        if (apiKey.isBlank()) {
            _state.value = _state.value.copy(notice = "请先配置 API Key")
            return
        }

        var conversationId = _state.value.selectedConversationId
        if (conversationId == null) {
            newConversation()
            conversationId = _state.value.selectedConversationId
        }
        val targetId = conversationId ?: return
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
                val cleaned = _state.value.conversations.map { conversation ->
                    if (conversation.id != targetId) conversation
                    else conversation.copy(
                        messages = conversation.messages.filterNot {
                            it.id == assistantMessage.id && it.content.isBlank()
                        }
                    )
                }
                _state.value = _state.value.copy(
                    conversations = cleaned,
                    notice = "请求失败：${error.message ?: "网络异常"}"
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