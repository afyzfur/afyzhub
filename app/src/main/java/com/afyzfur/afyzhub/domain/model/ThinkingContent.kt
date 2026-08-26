package com.afyzfur.afyzhub.domain.model

/**
 * 从回复中分离出的思考过程与正式回答。
 *
 * 部分模型把推理过程包在 `<think>` 标签里随正文一起返回，
 * 不拆开的话标签会原样显示在气泡里（实际见过这种情况）。
 */
data class ThinkingContent(
    /** 思考过程，无思考时为 null */
    val reasoning: String?,
    /** 正式回答 */
    val answer: String,
    /**
     * 思考是否仍在进行。
     *
     * 流式输出时 `</think>` 尚未到达，此时已收到的全部内容都属于思考。
     * 界面据此决定是展开还是折叠：进行中展开让用户看到进展，
     * 结束后折叠腾出空间给正式回答。
     */
    val thinking: Boolean
) {
    val hasReasoning: Boolean get() = !reasoning.isNullOrBlank()
}

/**
 * 拆分 `<think>` 标签。
 *
 * 三种情况：
 * - 无标签：全部是回答
 * - 标签已闭合：取标签内为思考，标签外为回答
 * - 标签未闭合（流式进行中）：开标签之后的全部内容都是思考
 *
 * 用正则而非手写状态机：标签格式固定且不嵌套，正则足够且更易读。
 * 参考 RikkaHub 的 ThinkTagTransformer 的处理思路。
 */
fun parseThinking(content: String): ThinkingContent {
    val closed = CLOSED_THINK.find(content)
    if (closed != null) {
        // 组 1 是标签名（反向引用要用它配对闭合标签），组 2 才是内容
        val reasoning = closed.groupValues[2].trim()
        // 移除整段标签后剩下的就是回答。用 removeRange 而非 replace，
        // 避免回答里恰好含有相同文本时被误删
        val answer = content.removeRange(closed.range).trim()
        return ThinkingContent(
            reasoning = reasoning.ifBlank { null },
            answer = answer,
            thinking = false
        )
    }

    val open = OPEN_THINK.find(content)
    if (open != null) {
        // 开标签之前可能有内容（少见但存在），它属于回答
        val before = content.substring(0, open.range.first).trim()
        return ThinkingContent(
            reasoning = open.groupValues[1].trim().ifBlank { null },
            answer = before,
            thinking = true
        )
    }

    return ThinkingContent(reasoning = null, answer = content, thinking = false)
}

/**
 * 标签名。
 *
 * 本项目的网络层统一把独立的推理字段拼成 think 标签，但模型也可能
 * 直接在 content 里内嵌 thinking 或 reasoning 命名的标签——各家用词
 * 不一。认不出来的标签会原样显示在气泡里，所以三种都收。
 *
 * 与 TitleCleanup 的 STRAY_TAG 保持同一组命名：一边能解析、另一边
 * 才清得掉，两处不一致会让标题重新出现残留字符。
 */
private const val TAG_NAMES = "think|thinking|reasoning"

/** DOT_MATCHES_ALL 使 . 能跨行匹配，思考内容通常是多行 */
private val CLOSED_THINK = Regex(
    "<($TAG_NAMES)>(.*?)</\\1>",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
)
private val OPEN_THINK = Regex(
    "<(?:$TAG_NAMES)>(.*)",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
)
