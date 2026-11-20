package com.afyzfur.afyzhub.domain.model

/** Five-step reasoning strength. The master switch is stored independently. */
enum class ThinkingEffort(
    val id: String,
    val label: String,
    val tokenBudget: Int
) {
    LOW("low", "低", 1_024),
    MEDIUM("medium", "中", 4_096),
    HIGH("high", "高", 8_192),
    XHIGH("xhigh", "极高", 16_384),
    MAX("max", "最大", 24_576);

    /** Claude needs output allowance above the thinking budget. */
    fun anthropicMaxTokens(default: Int): Int = maxOf(default, tokenBudget + default)

    companion object {
        val DEFAULT = MEDIUM

        fun openAiEffort(effort: ThinkingEffort, model: String = ""): String = when (effort) {
            LOW -> "low"
            MEDIUM -> "medium"
            HIGH -> "high"
            XHIGH -> if (supportsExtendedOpenAiEffort(model)) "xhigh" else "high"
            MAX -> if (supportsExtendedOpenAiEffort(model)) "max" else "high"
        }

        private fun supportsExtendedOpenAiEffort(model: String): Boolean {
            val normalized = model.trim().lowercase()
            return normalized.startsWith("gpt-6") || normalized.contains("xhigh") || normalized.contains("max")
        }

        fun supportsNativeThinking(provider: AiProvider, model: String): Boolean {
            val normalized = model.trim().lowercase()
            return when (provider) {
                AiProvider.OPENAI -> normalized.startsWith("o1") || normalized.startsWith("o3") ||
                    normalized.startsWith("o4") || normalized.startsWith("gpt-5") || normalized.startsWith("gpt-6")
                AiProvider.ANTHROPIC -> normalized.startsWith("claude-3-7") ||
                    normalized.startsWith("claude-4") ||
                    normalized.startsWith("claude-opus-4") ||
                    normalized.startsWith("claude-sonnet-4") ||
                    normalized.startsWith("claude-haiku-4") || normalized.startsWith("claude-5")
                AiProvider.GEMINI -> normalized.contains("gemini-2.5") ||
                    normalized.contains("gemini-3") || normalized.contains("gemini-4")
            }
        }

        fun fromId(id: String?): ThinkingEffort = when (id) {
            "low" -> LOW
            "medium" -> MEDIUM
            "high" -> HIGH
            "xhigh" -> XHIGH
            "max" -> MAX
            // The 0.3.8 enum stored OFF here. It is migrated as a disabled master switch.
            else -> DEFAULT
        }

        fun legacyThinkingEnabled(id: String?): Boolean? = id?.let { it != "off" }
    }
}
