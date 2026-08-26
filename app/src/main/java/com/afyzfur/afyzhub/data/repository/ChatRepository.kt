package com.afyzfur.afyzhub.data.repository

import com.afyzfur.afyzhub.domain.model.Conversation
import com.afyzfur.afyzhub.domain.model.ConversationItem
import com.afyzfur.afyzhub.domain.model.Message
import com.afyzfur.afyzhub.domain.model.SendPhase
import kotlinx.coroutines.flow.Flow

interface ChatRepository {

    fun getAllConversations(): Flow<List<Conversation>>

    /** 会话列表附带末条消息摘要，供抽屉列表使用。 */
    fun getConversationItems(): Flow<List<ConversationItem>>

    fun getMessagesByConversationId(conversationId: Long): Flow<List<Message>>

    suspend fun createConversation(title: String): Long

    /**
     * 发送一条用户消息并等待回复。
     *
     * 鉴权与 API 地址由网络层根据设置自动注入，调用方无需传入。
     *
     * [onPhase] 用于上报进行到哪一阶段，界面据此显示具体状态。
     * 用回调而非让仓库持有状态流：阶段是界面关心的瞬时信息，
     * 不属于仓库需要长期维护的状态。
     */
    suspend fun sendMessage(
        conversationId: Long,
        content: String,
        onPhase: (SendPhase) -> Unit = {}
    ): Result<Message>

    /** 重新发送一条此前失败的用户消息。 */
    suspend fun retryMessage(
        messageId: Long,
        onPhase: (SendPhase) -> Unit = {}
    ): Result<Message>

    /**
     * 结算该会话中所有仍在「发送中」的消息。
     *
     * 暂停后由界面层主动调用，不依赖取消异常的传播——那条链路要穿过
     * flow、NonCancellable 与 Room 三层，任一环节没走到就会留下
     * 永久停在「发送中」的消息。这里以数据库当前状态为准直接归位，
     * 与取消时序无关，也能清理历史遗留的卡住消息。
     */
    suspend fun settleInterrupted(conversationId: Long)

    /** 删除单条消息。 */
    suspend fun deleteMessage(messageId: Long)

    /**
     * 删除这条消息及其之后的全部消息。
     *
     * 「回滚到此处」用它把对话退回到该消息之前的状态。
     */
    suspend fun rollbackTo(messageId: Long)

    /**
     * 重新生成某条助手回复。
     *
     * 实现上是删掉这条回复，再以它前面那条用户消息重新请求。
     * 不复用 [retryMessage]：那个针对的是发送失败的用户消息，
     * 而这里的用户消息本身是成功的，只是要换一个回答。
     */
    suspend fun regenerate(
        assistantMessageId: Long,
        onPhase: (SendPhase) -> Unit = {}
    ): Result<Message>

    suspend fun renameConversation(conversationId: Long, title: String)

    /**
     * 写入模型生成的会话总结。
     *
     * 不改 updatedAt：总结是回复完成后异步生成的，刷新时间会让
     * 会话在抽屉里跳到"今天"，破坏时间分组。
     */
    suspend fun updateSummary(conversationId: Long, summary: String)

    suspend fun deleteConversation(conversationId: Long)
}
