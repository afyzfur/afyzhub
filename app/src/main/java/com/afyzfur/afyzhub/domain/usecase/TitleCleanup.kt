package com.afyzfur.afyzhub.domain.usecase

import com.afyzfur.afyzhub.domain.model.parseThinking

/** 标题长度上限 */
const val TITLE_MAX_LENGTH = 20

/** 总结长度上限。比标题宽松，一句话概括需要更多空间 */
const val SUMMARY_MAX_LENGTH = 40

/**
 * 标题与总结的纯文本处理。
 *
 * 单独成文件而不放进 [GenerateTitleUseCase]：那个类依赖网络客户端与
 * 设置仓库，间接牵进 DataStore 与 Compose，导致这些纯字符串逻辑
 * 无法在 JVM 单元测试里直接验证。
 */

/** 本地兜底标题：折叠空白后截断首句 */
fun fallbackTitle(firstMessage: String): String {
    val oneLine = firstMessage.trim().replace(WHITESPACE, " ")
    if (oneLine.isEmpty()) return DEFAULT_TITLE
    return if (oneLine.length <= TITLE_MAX_LENGTH) {
        oneLine
    } else {
        oneLine.take(TITLE_MAX_LENGTH) + "…"
    }
}

/**
 * 清洗模型返回的标题。
 *
 * 模型常无视"不要引号""不超过 12 字"这类要求，也可能带上思考过程
 * 或"标题："这样的前缀。提示词里的约束是请求而非保证，必须在这里
 * 兜住。
 */
fun cleanTitle(raw: String): String = clean(raw, TITLE_MAX_LENGTH)

fun cleanSummary(raw: String): String = clean(raw, SUMMARY_MAX_LENGTH)

private fun clean(raw: String, maxLength: Int): String {
    // 思考过程可能混在返回里，即使是这种小任务
    var text = parseThinking(raw).answer.ifBlank { raw }

    // parseThinking 只认成对标签。模型偶尔输出落单的开/闭标签
    // （被 token 上限截断，或干脆写错），残留下来就会显示在预览里
    text = text.replace(STRAY_TAG, " ")

    text = text.trim().replace(WHITESPACE, " ")

    // 前缀、引号、标点、Markdown 标记可能叠加，如 `**标题：「x」**`，
    // 循环剥到不再变化为止
    var changed = true
    while (changed) {
        val before = text
        PREFIXES.forEach { text = text.removePrefix(it) }
        text = text.trim()
            // Markdown 强调标记：提示词没要求 Markdown，但模型习惯加
            .trim('*', '#', '`', '_', '-')
            .trim()
            .trim(*QUOTES)
            // 列表序号，如 "1. " "1、"
            .replace(LEADING_INDEX, "")
            .trimEnd(*TAIL_PUNCTUATION)
            .trim()
        changed = text != before
    }

    return if (text.length <= maxLength) text else text.take(maxLength) + "…"
}

private const val DEFAULT_TITLE = "新对话"

private val WHITESPACE = Regex("\\s+")

/**
 * 落单的思考标签。
 *
 * 覆盖 think / thinking / reasoning 三种命名的开闭标签：
 * 各家用词不一，而这里只是要把它清掉，不需要区分。
 */
private val STRAY_TAG = Regex("</?(?:think|thinking|reasoning)>", RegexOption.IGNORE_CASE)

/** 开头的列表序号，如 "1. " "2、" */
private val LEADING_INDEX = Regex("^\\d+\\s*[.、）)]\\s*")

private val PREFIXES = listOf(
    "标题：", "标题:", "摘要：", "摘要:", "总结：", "总结:"
)

/** 各种成对引号，模型选哪种都有可能 */
private val QUOTES = charArrayOf(
    '"', '\'', '「', '」', '『', '』', '\u201C', '\u201D', '《', '》'
)

/** 标题与总结不需要末尾标点 */
private val TAIL_PUNCTUATION = charArrayOf(
    '。', '，', '!', '！', '?', '？', '.', ','
)
