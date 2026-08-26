package com.afyzfur.afyzhub.domain.usecase

import com.afyzfur.afyzhub.data.repository.ChatRepository
import com.afyzfur.afyzhub.domain.model.Message
import com.afyzfur.afyzhub.domain.model.SendPhase

/**
 * 发送消息。
 *
 * 标题生成已移到 [GenerateTitleUseCase]：它需要发一次模型请求，
 * 与发送消息是两件独立的事，混在一起会让这里也依赖网络客户端。
 */
class SendMessageUseCase(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(
        conversationId: Long,
        content: String,
        onPhase: (SendPhase) -> Unit = {}
    ): Result<Message> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("消息内容不能为空"))
        }
        return repository.sendMessage(conversationId, trimmed, onPhase)
    }
}
