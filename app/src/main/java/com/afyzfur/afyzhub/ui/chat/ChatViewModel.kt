package com.afyzfur.afyzhub.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.afyzfur.afyzhub.data.repository.ChatRepository
import com.afyzfur.afyzhub.domain.model.Message
import com.afyzfur.afyzhub.domain.usecase.SendMessageUseCase
import com.afyzfur.afyzhub.util.Constants
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadMessages(conversationId: Long) {
        viewModelScope.launch {
            repository.getMessagesByConversationId(conversationId).collect { list ->
                _messages.value = list
            }
        }
    }

    fun sendMessage(conversationId: Long, content: String) {
        if (content.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val apiKey = dataStore.data.first()[stringPreferencesKey(Constants.KEY_API_KEY)] ?: ""
                if (apiKey.isEmpty()) {
                    _error.value = "请先在设置中配置 API Key"
                    _isLoading.value = false
                    return@launch
                }

                val result = sendMessageUseCase(conversationId, content, apiKey)
                result.onFailure { e ->
                    _error.value = e.message ?: "发送失败"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "未知错误"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}