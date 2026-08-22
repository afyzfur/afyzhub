package com.afyzfur.afyzhub.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afyzfur.afyzhub.data.repository.ChatRepository
import com.afyzfur.afyzhub.domain.model.Message
import com.afyzfur.afyzhub.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository,
    private val sendMessageUseCase: SendMessageUseCase
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var currentConversationId: Long = -1L

    fun loadMessages(conversationId: Long) {
        currentConversationId = conversationId
        viewModelScope.launch {
            repository.getMessagesByConversationId(conversationId).collect { list ->
                _messages.value = list
            }
        }
    }

    fun sendMessage(conversationId: Long, content: String) {
        val text = content.trim()
        if (text.isEmpty() || _isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            // 首条消息用于自动命名会话。
            val isFirstMessage = _messages.value.isEmpty()

            sendMessageUseCase(conversationId, text)
                .onSuccess {
                    if (isFirstMessage) {
                        repository.renameConversation(
                            conversationId,
                            SendMessageUseCase.generateTitle(text)
                        )
                    }
                }
                .onFailure { e ->
                    _error.value = e.message ?: "发送失败"
                }

            _isLoading.value = false
        }
    }

    /** 重发一条失败的用户消息。 */
    fun retryMessage(messageId: Long) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            repository.retryMessage(messageId).onFailure { e ->
                _error.value = e.message ?: "重试失败"
            }
            _isLoading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }
}
