package com.afyzfur.afyzhub.domain.usecase

import com.afyzfur.afyzhub.data.remote.provider.ChatClientRegistry
import com.afyzfur.afyzhub.data.remote.provider.ChatTurn
import com.afyzfur.afyzhub.data.settings.SettingsProvider
import com.afyzfur.afyzhub.domain.model.parseThinking

/**
 * 用模型生成会话标题与一句话总结。
 *
 * 走用户当前配置的那组 API，不额外要求配置——多一处配置就多一处
 * 可能忘填的地方，而这个功能失败时有本地兜底，不值得。
 *
 * 所有失败都退回本地规则（截断首句），不向界面报错：标题和总结
 * 是附加信息，为它们弹一个错误提示反而打扰。
 */
class GenerateTitleUseCase(
    private val clientRegistry: ChatClientRegistry,
    private val settingsProvider: SettingsProvider
) {

    /**
     * 由首条用户消息生成标题。
     *
     * 失败时回退到 [fallbackTitle]。
     */
    suspend fun title(firstMessage: String): String {
        val local = fallbackTitle(firstMessage)
        val trimmed = firstMessage.trim()
        if (trimmed.isEmpty()) return local

        return runCatching {
            val settings = settingsProvider.current()
            if (settings.apiKey.isBlank()) return local

            val result = clientRegistry.clientFor(settings.provider).complete(
                turns = listOf(
                    ChatTurn(role = "system", content = TITLE_PROMPT),
                    ChatTurn(role = "user", content = trimmed.take(PROMPT_INPUT_LIMIT))
                ),
                settings = settings
            )
            cleanTitle(result.content).ifBlank { local }
        }.getOrDefault(local)
    }

    /**
     * 由一轮问答生成一句话总结。
     *
     * 返回 null 表示生成失败，调用方保持原样即可——总结缺失时
     * 界面会退回显示末条消息，不需要兜底文案。
     */
    suspend fun summary(userMessage: String, assistantReply: String): String? {
        if (userMessage.isBlank() || assistantReply.isBlank()) return null

        return runCatching {
            val settings = settingsProvider.current()
            if (settings.apiKey.isBlank()) return null

            // 助手回复可能含思考过程，只取正式回答部分去总结
            val answer = parseThinking(assistantReply).answer.ifBlank { assistantReply }

            val result = clientRegistry.clientFor(settings.provider).complete(
                turns = listOf(
                    ChatTurn(role = "system", content = SUMMARY_PROMPT),
                    ChatTurn(
                        role = "user",
                        content = buildString {
                            append("问：")
                            append(userMessage.take(PROMPT_INPUT_LIMIT))
                            append("\n答：")
                            append(answer.take(PROMPT_INPUT_LIMIT))
                        }
                    )
                ),
                settings = settings
            )
            cleanSummary(result.content).takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    companion object {
        /**
         * 送进提示词的原文长度上限。
         *
         * 截断而非全量发送：长对话会让这次附加请求的开销超过主请求，
         * 而标题和总结只需要开头就够判断话题。
         */
        private const val PROMPT_INPUT_LIMIT = 500

        private val TITLE_PROMPT = """
            你是一个会话标题生成器。根据用户的问题，生成一个简短的中文标题。
            要求：
            1. 不超过 12 个字
            2. 只输出标题本身，不要引号、句号、前缀或任何解释
            3. 概括主题，不要复述原句
        """.trimIndent()

        private val SUMMARY_PROMPT = """
            你是一个对话摘要生成器。根据一轮问答，生成一句话中文摘要。
            要求：
            1. 不超过 25 个字
            2. 只输出摘要本身，不要引号、句号、前缀或任何解释
            3. 说明这轮问答解决了什么，而不是复述内容
        """.trimIndent()

    }
}
