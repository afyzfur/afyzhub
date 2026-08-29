package com.afyzfur.afyzhub.domain.model

/**
 * 思考程度。
 *
 * 各家的参数名与取值都不同：OpenAI 系用 `reasoning_effort`
 * 取 low/medium/high，Anthropic 用 `thinking.budget_tokens` 给
 * token 预算，Gemini 用 `thinkingConfig.thinkingBudget`。这里用一个
 * 与厂商无关的档位，由各 client 自行翻译成对应参数。
 *
 * [OFF] 不是"设为最低"而是完全不发这个参数：不支持思考的模型收到
 * 未知字段可能直接报错，而多数中转对未知参数的处理并不宽容。
 */
enum class ThinkingEffort(
    val id: String,
    val label: String,
    /** OpenAI 系的 reasoning_effort 取值，OFF 时为 null */
    val openAiEffort: String?,
    /** Anthropic 与 Gemini 的 token 预算，OFF 时为 null */
    val tokenBudget: Int?
) {
    OFF(id = "off", label = "关闭", openAiEffort = null, tokenBudget = null),
    LOW(id = "low", label = "低", openAiEffort = "low", tokenBudget = 1024),
    MEDIUM(id = "medium", label = "中", openAiEffort = "medium", tokenBudget = 4096),
    HIGH(id = "high", label = "高", openAiEffort = "high", tokenBudget = 16384);

    /** 是否需要在请求里带上思考参数 */
    val enabled: Boolean get() = this != OFF

    /**
     * Anthropic 在开启思考时所需的 max_tokens 下限。
     *
     * Claude 要求 `max_tokens` 严格大于 `thinking.budget_tokens`，
     * 否则直接返回 400。而 budget 是从 max_tokens 里扣的额度，
     * 不是额外配给——如果只把 max_tokens 抬到刚好超过 budget，
     * 留给正文的空间就只剩几个 token，回答会被立刻截断。
     * 因此在预算之外再留出一份正文空间。
     */
    fun anthropicMaxTokens(default: Int): Int {
        val budget = tokenBudget ?: return default
        return maxOf(default, budget + default)
    }

    companion object {
        /**
         * 默认不开。
         *
         * 思考会显著增加耗时与费用，且不是所有模型都支持——
         * 让用户主动开启比默认打开再让人困惑于"为什么变慢了"更好。
         */
        val DEFAULT = OFF

        fun fromId(id: String?): ThinkingEffort =
            entries.firstOrNull { it.id == id } ?: DEFAULT

        /** 按档位循环切换，用于输入栏那个一键轮换的按钮 */
        fun next(current: ThinkingEffort): ThinkingEffort =
            entries[(entries.indexOf(current) + 1) % entries.size]
    }
}
