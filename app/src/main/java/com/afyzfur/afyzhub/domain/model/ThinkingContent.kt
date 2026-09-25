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
    // 收集全部闭合思考块：搜索触发的二次回复会让一条消息里出现
    // 两段思考（首轮 + 二轮），只取第一段会把后面的当成正文丢进
    // 回答里。按出现顺序用空行合并，阅读顺序与产生顺序一致
    val blocks = CLOSED_THINK.findAll(content).toList()
    if (blocks.isNotEmpty()) {
        val reasoning = blocks.joinToString("\n\n") { it.groupValues[2].trim() }
            .trim().ifBlank { null }
        var answer = content
        for (b in blocks.asReversed()) {
            answer = answer.removeRange(b.range)
        }
        return ThinkingContent(
            reasoning = reasoning,
            answer = answer.trim(),
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

/**
 * 联网搜索协议的标签解析。
 *
 * 模型按指令输出搜索标签, 发送流程截获后执行搜索;
 * UI 用它提取搜索词展示搜索块, 并从正文剥除标签。
 */
private val SEARCH_TAG = Regex(
    """<web_search>(.*?)</web_search>""",
    RegexOption.DOT_MATCHES_ALL
)

/** 流式中间态: 只有开标签吃到末尾 */
private val OPEN_SEARCH_ANY = Regex(
    """<web_search>.*""",
    RegexOption.DOT_MATCHES_ALL
)

/** 提取搜索词, 无标签或内容为空时返回 null */
fun parseSearchQuery(content: String): String? =
    SEARCH_TAG.find(content)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

/**
 * 搜索来源块解析。
 *
 * 搜索完成后发送流程把"标题 :: 链接"行包进 sources 标签
 * 追加在回复末尾。UI 渲染成可展开的来源列表，正文里剥除。
 */
private val SOURCES_TAG = Regex(
    """<sources>(.*?)</sources>""",
    RegexOption.DOT_MATCHES_ALL
)

/** 解析 sources 块内的条目(每行 "标题 :: 链接") */
fun parseSearchSources(content: String): List<Pair<String, String>> {
    val block = SOURCES_TAG.find(content)?.groupValues?.get(1) ?: return emptyList()
    return block.lines().mapNotNull { line ->
        val idx = line.lastIndexOf(" :: ")
        if (idx <= 0) null else line.substring(0, idx).trim() to
            line.substring(idx + 4).trim()
    }.filter { it.second.startsWith("http") }
}

/** 剥除正文中的来源块 */
fun stripSearchSources(content: String): String =
    SOURCES_TAG.replace(content, "").trim()
/**
 * 清理模型输出的复读标签。
 *
 * 二轮请求的上下文里带有搜索与来源标签, 模型有时照着样子在自己的输出里
 * 也包一层, 闭合的 sources 会把整段正文当来源剥掉, 正文随之消失。
 * 这里剥除模型输出里复读的标签(保留 sources 内的正文文本),
 * 官方来源块由发送流程统一追加, 不依赖模型自己输出。
 */
fun stripModelEchoTags(content: String): String {
    var out = SEARCH_TAG.replace(content, "")
    // 已闭合的上面剥掉了, 剩下的是未闭合截断态: 从开标签吃到末尾
    out = Regex(""""<web_search>.*"""", RegexOption.DOT_MATCHES_ALL).replace(out, "")
    // sources 只剥标签对本身, 内部文本(往往是正文)保留
    out = out.replace("<sources>", "").replace("</sources>", "")
    return out.trim()
}


/** 剥除正文中的搜索标签, 未闭合的开标签一并清掉 */
fun stripSearchTag(content: String): String = SEARCH_TAG.replace(content, "")
    .replace(Regex("""<(?:web_search|search)>.*""", RegexOption.DOT_MATCHES_ALL), "")
    .trim()


/**
 * 消息内容的顺序化拆分。
 *
 * 搜索场景下一条回复会交替出现多段内容：思考 → 搜索标签 →
 * （搜索后）思考 → 正文。之前的合并式解析会把不同段的思考
 * 揉在一起、搜索块与正文的位置关系丢失。这里按出现顺序切分，
 * UI 依次渲染多个块，段与段之间的先后关系一目了然。
 */
sealed class ContentBlock {
    /** 一段思考过程。[ongoing] 为真表示流式还没收到闭合标签 */
    data class Think(val text: String, val ongoing: Boolean) : ContentBlock()
    /** 一次联网搜索请求 */
    data class Search(val query: String) : ContentBlock()
    /** 正文文本段。多段正文按顺序拼接显示 */
    data class Answer(val text: String) : ContentBlock()
}

/** 顺序化拆分: 思考(闭合/未闭合)、搜索标签、其余正文 */
fun parseContentBlocks(content: String): List<ContentBlock> {
    data class Marker(val start: Int, val end: Int, val block: ContentBlock?)

    val markers = mutableListOf<Marker>()
    val seenQueries = mutableSetOf<String>()

    for (m in CLOSED_THINK.findAll(content)) {
        markers.add(Marker(m.range.first, m.range.last + 1, ContentBlock.Think(m.groupValues[2].trim(), false)))
    }
    // 流式进行中的末尾未闭合思考: 最后一个已闭合块之后若还有开标签,
    // 该开标签到字符串末尾是进行中的思考(搜索二轮流式常见)。
    // 在 markers 非空时也要检测, 否则未闭合段会落进正文
    if (markers.isNotEmpty()) {
        val lastClosedEnd = CLOSED_THINK.findAll(content)
            .lastOrNull()?.range?.last?.plus(1) ?: 0
        val tail = content.substring(lastClosedEnd)
        val tailOpen = OPEN_THINK.find(tail)
        if (tailOpen != null) {
            val text = tailOpen.groupValues[1].trim()
            if (text.isNotEmpty()) {
                markers.add(Marker(
                    lastClosedEnd + tailOpen.range.first,
                    content.length,
                    ContentBlock.Think(text, true)
                ))
            }
        }
    }
    if (markers.isEmpty()) {
        val open = OPEN_THINK.find(content)
        if (open != null) {
            markers.add(Marker(open.range.first, content.length, ContentBlock.Think(open.groupValues[1].trim(), true)))
        }
    }
    for (m in SEARCH_TAG.findAll(content)) {
        val q = m.groupValues[1].trim()
        if (q.isNotEmpty() && !seenQueries.contains(q)) {
            seenQueries.add(q)
            markers.add(Marker(m.range.first, m.range.last + 1, ContentBlock.Search(q)))
        }
    }
    // 搜索标签被截断的流式中间态: 只有开标签
    val openSearch = OPEN_SEARCH_ANY
    if (openSearch.findAll(content).count() > SEARCH_TAG.findAll(content).count()) {
        val m = openSearch.find(content)!!
        val q = m.value.replaceFirst("""<web_search>""", "").trim()
        if (q.isNotEmpty()) markers.add(Marker(m.range.first, content.length, ContentBlock.Search(q)))
    }

    // sources 块不作为内容块输出: 来源由 parseSearchSources 独立解析
    // 渲染成搜索块内的列表, 这里只负责把它从正文范围里剔除
    for (m in SOURCES_TAG.findAll(content)) {
        markers.add(Marker(m.range.first, m.range.last + 1, null))
    }

    if (markers.isEmpty()) {
        return if (content.isBlank()) emptyList() else listOf(ContentBlock.Answer(content.trim()))
    }

    markers.sortBy { it.start }
    val out = mutableListOf<ContentBlock>()
    var cursor = 0
    for (mk in markers) {
        if (mk.start > cursor) {
            val between = content.substring(cursor, mk.start).trim()
            if (between.isNotEmpty()) out.add(ContentBlock.Answer(between))
        }
        // block 为 null 表示 sources 块: 只剔除不产出
        mk.block?.let { out.add(it) }
        cursor = maxOf(cursor, mk.end)
    }
    if (cursor < content.length) {
        val tail = content.substring(cursor).trim()
        if (tail.isNotEmpty()) out.add(ContentBlock.Answer(tail))
    }
    return out
}

/** 所有正文段拼接(用于持久化摘要等场景) */
fun joinAnswerBlocks(blocks: List<ContentBlock>): String =
    blocks.filterIsInstance<ContentBlock.Answer>().joinToString("\n\n") { it.text }.trim()


/**
 * 多轮上下文用的深度清洗。与 stripSearchTagsOnly/stripModelEchoTags
 * 只剥标签符号不同, 这里把标签连同内容一起处理:
 * - web_search 标签整体替换为 [搜索: 查询]
 *   —— 只剥标签会让查询词裸露成正文, 模型语义困惑;
 * - sources 标签整块(含标题::链接)替换为 [已附搜索来源]
 *   —— 若保留明文, 模型认为上一轮已有搜索结果, 新一轮该搜索
 *   时不再输出搜索标签(表现为第二次搜不到)。
 * 仅用于发给模型的历史, 不影响 UI 存储内容。
 */
fun sanitizeHistory(content: String): String {
    // 标签用 unicode 转义书写(避免源码里出现尖括号):
    val O_WS = "\u003Cweb_search\u003E"
    val C_WS = "\u003C/web_search\u003E"
    val O_SR = "\u003Csources\u003E"
    val C_SR = "\u003C/sources\u003E"
    var out = content
    // 1) 搜索标签对 -> [搜索: 查询](查询词不裸露成正文)
    var p = out.indexOf(O_WS)
    while (p >= 0) {
        val e = out.indexOf(C_WS, p + O_WS.length)
        if (e < 0) break
        val q = out.substring(p + O_WS.length, e).trim()
        out = out.substring(0, p) + "[搜索: " + q + "]" + out.substring(e + C_WS.length)
        p = out.indexOf(O_WS, p + 1)
    }
    // 2) 来源块整块(含标题::链接) -> [已附搜索来源]
    //    保留明文会让模型认为已有搜索结果, 新一轮不再发标签
    p = out.indexOf(O_SR)
    while (p >= 0) {
        val e = out.indexOf(C_SR, p + O_SR.length)
        if (e < 0) break
        out = out.substring(0, p) + "[已附搜索来源]" + out.substring(e + C_SR.length)
        p = out.indexOf(O_SR, p + 1)
    }
    // 3) 残段兜底: 单独的开/闭符号
    out = out.replace(O_WS, "[搜索: ").replace(C_WS, "]")
    out = out.replace(O_SR, "").replace(C_SR, "")
    return out
}
