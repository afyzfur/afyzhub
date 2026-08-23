package com.afyzfur.afyzhub.domain.model

/**
 * 支持的 AI 服务提供商。
 *
 * 各家接口在鉴权方式、请求体结构和流式事件格式上都不同，
 * 因此每个提供商需要独立的适配实现，而不是仅替换 base URL。
 *
 * 可用模型不在此硬编码，改为运行时从各家的模型列表接口获取，
 * 避免模型更新后应用内的名单过期。
 */
enum class AiProvider(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    /** 拉取模型列表失败时的兜底模型，仅用于首次配置。 */
    val fallbackModel: String
) {
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/",
        fallbackModel = "gpt-4o-mini"
    ),
    ANTHROPIC(
        id = "anthropic",
        displayName = "Anthropic Claude",
        defaultBaseUrl = "https://api.anthropic.com/",
        fallbackModel = "claude-sonnet-4-20250514"
    ),
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/",
        fallbackModel = "gemini-2.0-flash"
    );

    companion object {
        val DEFAULT = OPENAI

        fun fromId(id: String?): AiProvider =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
