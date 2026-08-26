package com.afyzfur.afyzhub.ui.chat

import com.afyzfur.afyzhub.domain.model.parseThinking

/** 思考尚未产出回答时的占位文案 */
const val THINKING_PREVIEW = "正在思考…"

/**
 * 由消息正文生成抽屉里的一行预览。
 *
 * 预览的用途是让人认出这个会话，所以思考过程要剥掉——带 think 标签的
 * 模型会让预览全是标签内容。整条都还在思考中（回答尚未开始）时给一个
 * 状态文案，而不是退回原文：原文正是那堆标签。
 *
 * 顺带把换行折成空格。预览只有一行，换行在单行显示里会变成一个
 * 看不出来的间断，读起来像少了字。
 */
fun previewOf(raw: String): String {
    val parsed = parseThinking(raw)
    val answer = parsed.answer.trim()
    if (answer.isNotEmpty()) return answer.flattenLines()

    // 没有回答：要么在思考中，要么思考完了但回答还没到
    if (parsed.hasReasoning) return THINKING_PREVIEW

    // 既无回答也无思考，说明确实是空内容
    return raw.trim().flattenLines()
}

/** 折叠换行与连续空白为单个空格 */
private fun String.flattenLines(): String =
    replace(WHITESPACE_RUN, " ").trim()

private val WHITESPACE_RUN = Regex("\\s+")
