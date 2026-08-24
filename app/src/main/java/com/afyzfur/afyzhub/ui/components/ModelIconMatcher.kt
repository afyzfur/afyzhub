package com.afyzfur.afyzhub.ui.components

/**
 * 模型名到厂商图标的匹配。
 *
 * 匹配模型名而非提供商配置，因此中转服务提供的模型也能显示正确图标——
 * `[限时福利]claude-opus-5-thinking` 会匹配到 Claude，尽管它来自第三方。
 *
 * 单独成文件而非放进 ModelIcon.kt：那个文件依赖 Compose 与 Coil，
 * 在纯 JVM 测试环境无法编译，而这段匹配逻辑正是最需要测试的部分。
 *
 * 思路参考 RikkaHub 的 AIIconMatcher，图标资源亦取自该项目。
 */

/**
 * 查找与 [modelName] 匹配的图标文件名，无匹配返回 null。
 *
 * 按 [RULES] 的顺序逐条尝试，首个命中即返回。顺序有意义：
 * 更具体的规则必须排在更宽泛的之前。
 */
internal fun matchModelIcon(modelName: String): String? {
    val lower = modelName.lowercase()
    return RULES.firstOrNull { it.pattern.containsMatchIn(lower) }?.icon
}

private class IconRule(val pattern: Regex, val icon: String)

private infix fun String.to(icon: String) = IconRule(Regex(this), icon)

/**
 * 匹配规则，按优先级从高到低。
 *
 * 几处刻意的写法：
 * - `gemma` 在 `gemini` 之前：两者同属 Google 但图标不同，
 *   虽然正则不重叠，靠顺序表达意图更清楚
 * - `(?<!o)llama` 排除 `ollama`——后者是本地推理工具，不是 Meta 的模型
 * - `meta\b` 加词界，否则会命中 `metaso`（一个搜索服务）
 * - `\bo\d\b` 而非 `o\d`：OpenAI 的 o 系列模型名很短，
 *   不加词界会命中任何含 o 接数字的串，例如中转常见的 `pro-o3-plus` 之类
 *   本意可能并非 OpenAI。这一点与 RikkaHub 的原实现不同
 */
private val RULES: List<IconRule> = listOf(
    // 具体模型系列优先于厂商名
    "gemma" to "gemma-color.svg",
    "(gemini|nano-banana)" to "gemini-color.svg",
    "(gpt|\\bo\\d\\b|openai)" to "openai.svg",
    "claude" to "claude-color.svg",
    "anthropic" to "anthropic.svg",
    "deepseek" to "deepseek-color.svg",
    "(grok|xai)" to "xai.svg",
    "(qwen|qwq|qvq|通义)" to "qwen-color.svg",
    "(kimi|moonshot|月之暗面)" to "kimi-color.svg",
    "(zhipu|glm|智谱)" to "zhipu-color.svg",
    "(minimax|abab)" to "minimax-color.svg",
    "(doubao|豆包)" to "doubao-color.svg",
    "(hunyuan|tencent|腾讯|混元)" to "hunyuan-color.svg",
    // mistral 与 mixtral 是同一家的两个系列
    "mi[sx]tral" to "mistral-color.svg",
    "(meta\\b|(?<!o)llama)" to "meta-color.svg",
    "(stepfun|阶跃)" to "stepfun-color.svg",
    "(internlm|书生)" to "internlm-color.svg",
    "(cohere|command-)" to "cohere-color.svg",
    "nvidia" to "nvidia-color.svg",
    "(bytedance|字节|火山)" to "bytedance-color.svg",
    "(aliyun|阿里云|百炼|dashscope)" to "alibabacloud-color.svg",
    "google" to "google-color.svg",
    // 聚合与中转平台
    "openrouter" to "openrouter.svg",
    "aihubmix" to "aihubmix-color.svg",
    "302" to "302ai.svg",
    "groq" to "groq.svg",
    "cerebras" to "cerebras-color.svg",
    "ollama" to "ollama.svg"
)

/**
 * 规则引用到的全部图标文件，供测试核对 assets 是否齐全。
 *
 * 漏放文件时匹配会成功但显示空白，这种错误在界面上只表现为
 * "图标位空着"，不易归因，所以用测试卡住。
 */
internal val referencedIconFiles: Set<String> = RULES.map { it.icon }.toSet()
