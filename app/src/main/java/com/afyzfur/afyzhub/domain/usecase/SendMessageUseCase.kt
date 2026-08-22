package com.afyzfur.afyzhub.domain.usecase

import com.afyzfur.afyzhub.data.repository.ChatRepository
import com.afyzfur.afyzhub.domain.model.Message

/**
 * 发送消息。
 *
 * 若该会话此前没有有效标题，会用首条用户消息自动命名。
 */
class SendMessageUseCase(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(conversationId: Long, content: String): Result<Message> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("消息内容不能为空"))
        }
        return repository.sendMessage(conversationId, trimmed)
    }

    companion object {
        private const val TITLE_MAX_LENGTH = 20

        /** 用首条消息生成简短标题。 */
        fun generateTitle(firstMessage: String): String {
            val oneLine = firstMessage.trim().replace(Regex("\\s+"), " ")
            if (oneLine.isEmpty()) return "新对话"
            return if (oneLine.length <= TITLE_MAX_LENGTH) {
                oneLine
            } else {
                oneLine.take(TITLE_MAX_LENGTH) + "…"
            }
        }
    }
}
