package com.afyzhub.aichat.data

/**
 * 官方厂商模型的上下文窗口（token）对照表。
 * 仅收录 OpenAI、Anthropic、DeepSeek、Google、阿里、月之暗面、智谱等官方模型，
 * 数值取自各厂商公开文档。第三方中转自定义命名不保证识别，未命中时回退默认值，
 * 用户可在设置里手动指定上下文长度。
 */
object ModelCatalog {

    // 仅收录官方厂商模型，采用各厂商公开文档的上下文窗口（token）。
    // 采用「子串包含 + 最长上下文优先」匹配，故更细分的型号写在前、通用兜底写在后。
    private val CONTEXT_WINDOWS: List<Pair<String, Int>> = listOf(
        // ── OpenAI ──
        "gpt-4.1" to 1_000_000,          // gpt-4.1 / gpt-4.1-mini / gpt-4.1-nano
        "gpt-4o" to 128_000,             // gpt-4o / gpt-4o-mini
        "gpt-4-turbo" to 128_000,
        "gpt-4-32k" to 32_000,
        "gpt-4" to 8_000,
        "gpt-3.5-turbo" to 16_000,
        "chatgpt-4o" to 128_000,
        "o4-mini" to 200_000,
        "o3-mini" to 200_000,
        "o3" to 200_000,
        "o1-mini" to 128_000,
        "o1" to 200_000,

        // ── Anthropic Claude ──
        "claude-opus-4" to 200_000,
        "claude-sonnet-4" to 200_000,
        "claude-3-7-sonnet" to 200_000,
        "claude-3-5-sonnet" to 200_000,
        "claude-3-5-haiku" to 200_000,
        "claude-3-opus" to 200_000,
        "claude-3-sonnet" to 200_000,
        "claude-3-haiku" to 200_000,
        "claude-3" to 200_000,
        "claude-2" to 100_000,
        "claude" to 200_000,

        // ── DeepSeek ──（官方 deepseek-chat / deepseek-reasoner 上下文 64K）
        "deepseek-v3" to 64_000,
        "deepseek-r1" to 64_000,
        "deepseek-chat" to 64_000,
        "deepseek-reasoner" to 64_000,
        "deepseek-coder" to 128_000,
        "deepseek" to 64_000,

        // ── Google Gemini ──
        "gemini-2.5-pro" to 1_000_000,
        "gemini-2.5-flash" to 1_000_000,
        "gemini-2.0-flash" to 1_000_000,
        "gemini-1.5-pro" to 2_000_000,
        "gemini-1.5-flash" to 1_000_000,
        "gemini" to 1_000_000,

        // ── 阿里 Qwen 通义千问 ──
        "qwen-long" to 10_000_000,
        "qwen-turbo" to 1_000_000,
        "qwen-plus" to 131_000,
        "qwen-max" to 32_000,
        "qwen2.5" to 131_000,
        "qwen" to 131_000,

        // ── 月之暗面 Kimi / Moonshot ──
        "moonshot-v1-128k" to 128_000,
        "moonshot-v1-32k" to 32_000,
        "moonshot-v1-8k" to 8_000,
        "kimi" to 128_000,
        "moonshot" to 128_000,

        // ── 智谱 GLM ──
        "glm-4-long" to 1_000_000,
        "glm-4.6" to 200_000,
        "glm-4.5" to 128_000,
        "glm-4-plus" to 128_000,
        "glm-4" to 128_000,
        "glm" to 128_000
    )

    /** 默认上下文窗口：无法识别的模型采用一个保守但通用的值。 */
    const val DEFAULT_CONTEXT_WINDOW = 32_768

    /**
     * 按模型名称匹配上下文窗口，未知返回默认值。
     * 不区分服务商：忽略厂商前缀（如 openai/、anthropic/），只看模型名本身。
     * 采用「子串包含 + 最长上下文优先」策略——只要模型名包含某个已知模型关键字即命中，
     * 命中多个时取其中上下文窗口最大的一个（即该模型支持的最长上下文）。
     */
    fun contextWindowFor(modelId: String): Int {
        // 去掉厂商前缀，只保留模型名部分
        val name = modelId.trim().lowercase().substringAfterLast('/')
        var best = 0
        for ((key, window) in CONTEXT_WINDOWS) {
            if (name.contains(key) && window > best) {
                best = window
            }
        }
        return if (best > 0) best else DEFAULT_CONTEXT_WINDOW
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