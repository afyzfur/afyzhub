package com.afyzhub.aichat.data

/**
 * 常见模型的上下文窗口（token）对照表，用于 MAX 上下文模式自动匹配。
 * 采用前缀匹配：模型 id 以某个键开头即命中，便于兼容带日期后缀的变体。
 */
object ModelCatalog {

    private val CONTEXT_WINDOWS: List<Pair<String, Int>> = listOf(
        // OpenAI
        "gpt-4o-mini" to 128_000,
        "gpt-4o" to 128_000,
        "gpt-4.1-mini" to 1_000_000,
        "gpt-4.1" to 1_000_000,
        "gpt-4-turbo" to 128_000,
        "gpt-4-32k" to 32_768,
        "gpt-4" to 8_192,
        "gpt-3.5-turbo-16k" to 16_385,
        "gpt-3.5-turbo" to 16_385,
        "o1-mini" to 128_000,
        "o1" to 200_000,
        "o3-mini" to 200_000,
        "o3" to 200_000,
        // Anthropic Claude
        "claude-3-5-sonnet" to 200_000,
        "claude-3-5-haiku" to 200_000,
        "claude-3-opus" to 200_000,
        "claude-3-sonnet" to 200_000,
        "claude-3-haiku" to 200_000,
        "claude-3" to 200_000,
        "claude-2" to 100_000,
        // DeepSeek
        "deepseek-chat" to 64_000,
        "deepseek-reasoner" to 64_000,
        "deepseek" to 64_000,
        // Qwen 通义千问
        "qwen-max" to 32_768,
        "qwen-plus" to 131_072,
        "qwen-turbo" to 1_000_000,
        "qwen2.5" to 131_072,
        "qwen" to 32_768,
        // Google Gemini
        "gemini-1.5-pro" to 2_000_000,
        "gemini-1.5-flash" to 1_000_000,
        "gemini-2.0-flash" to 1_000_000,
        "gemini" to 1_000_000,
        // Moonshot Kimi
        "moonshot-v1-128k" to 128_000,
        "moonshot-v1-32k" to 32_768,
        "moonshot-v1-8k" to 8_192,
        "moonshot" to 128_000,
        // GLM 智谱
        "glm-4-long" to 1_000_000,
        "glm-4" to 128_000,
        "glm" to 128_000
    )

    /** 默认上下文窗口：无法识别的模型采用一个保守但通用的值。 */
    const val DEFAULT_CONTEXT_WINDOW = 32_768

    /**
     * 按模型 id 匹配上下文窗口，未知返回默认值。
     * 兼容常见变体：
     * - 厂商前缀：openai/gpt-4o、anthropic/claude-3-5-sonnet
     * - 免费/量化后缀：...:free、...-8bit
     * 匹配策略：先用完整 id，再用去掉厂商前缀（最后一个 '/' 之后）的部分，
     * 取所有命中前缀中最长的一个（最长匹配更精确）。
     */
    fun contextWindowFor(modelId: String): Int {
        val raw = modelId.trim().lowercase()
        val candidates = buildList {
            add(raw)
            if (raw.contains('/')) add(raw.substringAfterLast('/'))
        }
        var best: Pair<String, Int>? = null
        for (id in candidates) {
            for (entry in CONTEXT_WINDOWS) {
                if (id.startsWith(entry.first)) {
                    if (best == null || entry.first.length > best!!.first.length) {
                        best = entry
                    }
                }
            }
        }
        return best?.second ?: DEFAULT_CONTEXT_WINDOW
    }

    /**
     * 估算一段文本的 token 数。
     * 采用简单启发式：CJK 字符约 1 token/字，其余约 1 token/4 字符。
     * 不追求精确，仅用于 MAX 模式下的历史裁剪。
     */
    fun estimateTokens(text: String): Int {
        var cjk = 0
        var other = 0
        for (ch in text) {
            if (ch.code in 0x4E00..0x9FFF || ch.code in 0x3040..0x30FF) cjk++ else other++
        }
        return cjk + (other / 4) + 1
    }
}