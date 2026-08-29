package com.afyzfur.afyzhub.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afyzfur.afyzhub.data.repository.ChatRepository
import com.afyzfur.afyzhub.data.settings.AppSettings
import com.afyzfur.afyzhub.data.settings.SettingsRepository
import com.afyzfur.afyzhub.data.settings.UiPreferences
import com.afyzfur.afyzhub.domain.model.ConversationItem
import com.afyzfur.afyzhub.domain.model.ThinkingEffort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
    private val settingsRepository: SettingsRepository
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

    /** 界面偏好，当前用于首屏提示词与消息元信息显示 */
    val uiPreferences: StateFlow<UiPreferences> = settingsRepository.uiPreferences

    /**
     * 当前激活配置组的显示名。
     *
     * 界面上要显示的是用户给这组起的名字，不是提供商的固定名称。
     * 建了多组同一提供商时（例如两个不同中转），全都显示「OpenAI」
     * 就分不出用的是哪一组。
     *
     * 组名为空时 displayName 会退回提供商名，所以这里不必再兜底。
     */
    val activeProfileName: StateFlow<String> = settingsRepository.apiProfilesFlow
        .map { it.active?.displayName.orEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ""
        )

    /**
     * 循环切换思考程度。
     *
     * 存进设置而非留在界面状态：切到别的会话或重启应用后，
     * 用户预期这个选择还在。
     */
    fun cycleThinkingEffort() {
        viewModelScope.launch {
            val current = settingsRepository.settings.value.thinkingEffort
            settingsRepository.setThinkingEffort(ThinkingEffort.next(current))
        }
    }

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

    fun setPinned(conversationId: Long, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(conversationId, pinned) }
    }

    fun setStarred(conversationId: Long, starred: Boolean) {
        viewModelScope.launch { repository.setStarred(conversationId, starred) }
    }

    fun updateNote(conversationId: Long, note: String) {
        viewModelScope.launch { repository.updateNote(conversationId, note) }
    }

    fun moveToGroup(conversationId: Long, group: String) {
        viewModelScope.launch { repository.updateGroup(conversationId, group) }
    }

    /**
     * 已存在的分组名。
     *
     * 常驻订阅而非用时再查：移动分组的对话框要立刻显示可选项，
     * 打开时才发起查询会先闪一下空列表。
     */
    val groups: StateFlow<List<String>> = repository.observeGroups()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
}
