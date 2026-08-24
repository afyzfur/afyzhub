package com.afyzfur.afyzhub.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afyzfur.afyzhub.data.repository.ChatRepository
import com.afyzfur.afyzhub.data.settings.AppSettings
import com.afyzfur.afyzhub.data.settings.SettingsRepository
import com.afyzfur.afyzhub.domain.model.ConversationItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 聊天页宿主的状态持有者，负责"当前打开哪个会话"以及会话列表。
 *
 * 为什么与 [ChatViewModel] 分开：
 * ChatViewModel 管的是单个会话内的消息收发与流式状态，生命周期上应随会话切换而重置；
 * 会话列表和当前选中项则要跨会话存活。混在一起会让"切换会话"变成既要清空消息状态、
 * 又要保留列表状态的矛盾操作。
 *
 * 会话创建时机：**不在打开应用时创建**，而是延迟到用户首次发送消息。
 * 否则每次启动都会留下一条空会话记录，抽屉里迅速堆满"新对话"。
 * 因此 [currentConversationId] 允许为 null，表示"尚未落库的新会话"。
 */
class ChatHostViewModel(
    private val repository: ChatRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val _conversations = MutableStateFlow<List<ConversationItem>>(emptyList())
    val conversations: StateFlow<List<ConversationItem>> = _conversations.asStateFlow()

    /**
     * 当前生效的提供商与模型配置，供顶栏显示。
     *
     * 直接复用 SettingsRepository 已有的 StateFlow，不另建缓存——
     * 设置页改动后顶栏需要立即反映。
     */
    val settings: StateFlow<AppSettings> = settingsRepository.settings

    /** null 表示当前是尚未落库的空白新会话 */
    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getConversationItems().collect { list ->
                _conversations.value = list
            }
        }
    }

    /** 打开已有会话 */
    fun openConversation(conversationId: Long) {
        _currentConversationId.value = conversationId
    }

    /**
     * 开启新会话。仅重置当前选中项，不写库。
     * 真正的记录在用户发送首条消息时由 [ensureConversation] 创建。
     */
    fun startNewConversation() {
        _currentConversationId.value = null
    }

    /**
     * 确保当前会话已落库，返回其 id。
     *
     * 供发送消息前调用：若当前是空白新会话则创建记录，否则直接返回已有 id。
     * 标题先用占位值，由 ChatViewModel 在首条消息发送成功后按内容改名。
     */
    suspend fun ensureConversation(): Long {
        _currentConversationId.value?.let { return it }
        val id = repository.createConversation("新对话")
        _currentConversationId.value = id
        return id
    }

    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            repository.deleteConversation(conversationId)
            // 删掉的正是当前会话时退回空白新会话，避免停留在已不存在的会话上
            if (_currentConversationId.value == conversationId) {
                _currentConversationId.value = null
            }
        }
    }

    fun renameConversation(conversationId: Long, title: String) {
        viewModelScope.launch {
            repository.renameConversation(conversationId, title)
        }
    }
}
