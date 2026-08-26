package com.afyzfur.afyzhub.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afyzfur.afyzhub.data.repository.ChatRepository
import com.afyzfur.afyzhub.domain.model.Message
import com.afyzfur.afyzhub.domain.model.SendPhase
import com.afyzfur.afyzhub.domain.usecase.GenerateTitleUseCase
import com.afyzfur.afyzhub.domain.usecase.fallbackTitle
import com.afyzfur.afyzhub.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val generateTitleUseCase: GenerateTitleUseCase
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * 当前发送阶段。界面据此显示具体状态文字，并决定发送按钮是否切成暂停。
     *
     * 与 [isLoading] 并存而非取代：后者已被多处用于禁用输入，
     * 而两者语义一致（isActive 等价于 isLoading），改造牵动面过大。
     */
    private val _sendPhase = MutableStateFlow(SendPhase.IDLE)
    val sendPhase: StateFlow<SendPhase> = _sendPhase.asStateFlow()

    private var currentConversationId: Long = -1L

    /**
     * 进行中的发送任务，用于暂停。
     *
     * 与 [messagesJob] 分开持有：切换会话要取消消息订阅但不该中断
     * 正在进行的请求，两者生命周期不同。
     */
    private var sendJob: Job? = null

    /**
     * 当前的消息订阅任务。
     *
     * 抽屉切换会话后会再次调用 [loadMessages]，若不取消上一次的 collect，
     * 旧会话的 Flow 仍在向 [_messages] 写入，两个会话的消息会互相覆盖。
     */
    private var messagesJob: Job? = null

    fun loadMessages(conversationId: Long) {
        currentConversationId = conversationId
        messagesJob?.cancel()

        // 打开会话时先结算残留的「发送中」。
        //
        // 进程被杀或异常退出时没有任何代码能收尾，那些消息会永久卡住。
        // 放在这里是因为打开会话必然经过此处，且此时不可能有进行中的请求——
        // 发送时 isLoading 会阻止切换会话
        if (!_isLoading.value) {
            viewModelScope.launch {
                repository.settleInterrupted(conversationId)
            }
        }

        messagesJob = viewModelScope.launch {
            repository.getMessagesByConversationId(conversationId).collect { list ->
                _messages.value = list
            }
        }
    }

    /**
     * 清空消息列表并停止订阅，用于切到尚未落库的空白新会话。
     */
    fun clearMessages() {
        messagesJob?.cancel()
        messagesJob = null
        currentConversationId = -1L
        _messages.value = emptyList()
        _error.value = null
    }

    /**
     * 发送消息。
     *
     * [resolveConversationId] 用于取得目标会话 id，允许是挂起函数——
     * 空白新会话需要先落库才有 id（见 ChatHostViewModel.ensureConversation）。
     *
     * 之所以由本方法负责调用而不是让调用方先取好 id 再传进来：
     * 会话创建会触发 currentConversationId 变化，进而触发 loadMessages 重新订阅，
     * 与发送流程并发。把两者收在同一个协程里可避免"是否首条消息"的判断
     * 读到被订阅回填后的列表。
     */
    fun sendMessage(content: String, resolveConversationId: suspend () -> Long) {
        val text = content.trim()
        if (text.isEmpty() || _isLoading.value) return

        // 立即置位，防止连续点击发送在协程启动前穿透上面的判断
        _isLoading.value = true
        _sendPhase.value = SendPhase.CONNECTING

        sendJob = viewModelScope.launch {
            _error.value = null

            // 必须在创建会话之前取，否则新会话的订阅可能已回填列表
            val isFirstMessage = _messages.value.isEmpty()

            val conversationId = resolveConversationId()

            // 收尾放在 finally：暂停时取消异常会直接穿出这个协程，
            // 写在末尾的复位执行不到，输入框会永久停在禁用状态
            try {
                // 先用本地规则命名，避免抽屉里短暂显示"新对话"。
                // 模型生成的标题稍后覆盖它
                if (isFirstMessage) {
                    repository.renameConversation(
                        conversationId,
                        fallbackTitle(text)
                    )
                }

                sendMessageUseCase(conversationId, text) { phase ->
                    _sendPhase.value = phase
                }
                    .onSuccess { reply ->
                        // 标题与总结各发一次请求，放到独立协程里：
                        // 它们失败或慢都不该影响这次对话，也不该让
                        // 输入框继续处于禁用状态
                        launchMetadataUpdate(
                            conversationId = conversationId,
                            userText = text,
                            replyContent = reply.content,
                            needTitle = isFirstMessage
                        )
                    }
                    .onFailure { e ->
                        _error.value = e.message ?: "发送失败"
                    }
            } finally {
                _isLoading.value = false
                _sendPhase.value = SendPhase.IDLE
            }
        }
    }

    /**
     * 生成标题与总结。
     *
     * 用独立协程而非跟在发送后面：这两件事各要发一次请求，串在
     * 主流程里会让输入框多禁用几秒。它们失败也不提示——附加信息
     * 缺失时界面有兜底，为此弹错误反而打扰。
     *
     * 不放进 sendJob：暂停回复不该连带取消已完成回复的总结。
     */
    private fun launchMetadataUpdate(
        conversationId: Long,
        userText: String,
        replyContent: String,
        needTitle: Boolean
    ) {
        viewModelScope.launch {
            if (needTitle) {
                val title = generateTitleUseCase.title(userText)
                repository.renameConversation(conversationId, title)
            }
            generateTitleUseCase.summary(userText, replyContent)?.let { summary ->
                repository.updateSummary(conversationId, summary)
            }
        }
    }

    /**
     * 暂停当前回复。
     *
     * 取消发送协程即可——仓库层捕获取消异常后会保留已生成的内容
     * 并把消息置为终态，不会留下停在"发送中"的残留。
     */
    fun stopGenerating() {
        val job = sendJob ?: return
        sendJob = null
        job.cancel()

        // 立即复位而不等协程的 finally：取消是异步的，
        // 期间按钮若仍是暂停态，用户会以为没点中而重复点击
        _isLoading.value = false
        _sendPhase.value = SendPhase.IDLE

        val conversationId = currentConversationId
        if (conversationId <= 0) return

        // 主动结算，不依赖取消异常在 flow / NonCancellable / Room 之间的传播。
        // 那条链路任一环节没走到，消息就永久停在「发送中」——实测确实如此。
        //
        // 用 viewModelScope 新起协程而非复用 sendJob：后者已被取消，
        // 其子协程里的挂起调用会立即再次抛出取消异常，写库根本执行不到
        viewModelScope.launch {
            repository.settleInterrupted(conversationId)
        }
    }

    /** 重发一条失败的用户消息。 */
    fun retryMessage(messageId: Long) {
        if (_isLoading.value) return
        sendJob = viewModelScope.launch {
            _isLoading.value = true
            _sendPhase.value = SendPhase.CONNECTING
            _error.value = null
            try {
                repository.retryMessage(messageId) { phase ->
                    _sendPhase.value = phase
                }.onFailure { e ->
                    _error.value = e.message ?: "重试失败"
                }
            } finally {
                _isLoading.value = false
                _sendPhase.value = SendPhase.IDLE
            }
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch { repository.deleteMessage(messageId) }
    }

    fun rollbackTo(messageId: Long) {
        viewModelScope.launch { repository.rollbackTo(messageId) }
    }

    /** 重新生成某条助手回复。与发送共用忙碌状态与暂停能力。 */
    fun regenerate(messageId: Long) {
        if (_isLoading.value) return
        _isLoading.value = true
        _sendPhase.value = SendPhase.CONNECTING

        sendJob = viewModelScope.launch {
            _error.value = null
            try {
                repository.regenerate(messageId) { phase ->
                    _sendPhase.value = phase
                }.onFailure { e ->
                    _error.value = e.message ?: "重新生成失败"
                }
            } finally {
                _isLoading.value = false
                _sendPhase.value = SendPhase.IDLE
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
