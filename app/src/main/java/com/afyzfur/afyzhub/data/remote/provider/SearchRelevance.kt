package com.afyzfur.afyzhub.data.remote.provider

import com.afyzfur.afyzhub.data.remote.provider.WebSearchService.Result

private val weatherIntent = Regex("天气预报|天气|气温|降雨|降水|weather|temperature", RegexOption.IGNORE_CASE)

/** Guard explicitly named Chinese weather locations. Do not character-filter general or English queries. */
internal fun isSearchResultRelevant(query: String, title: String, snippet: String, url: String = ""): Boolean {
    val normalized = query.replace(Regex("\\s+"), "")
    if (normalized.isBlank()) return false
    val intent = weatherIntent.find(normalized) ?: return true
    val location = normalized.substring(0, intent.range.first)
        .replace(Regex("^(请|帮我|我想|我查一下|查一下|查询|搜索|看看|现在|实时|今日|今天)+"), "")
        .replace(Regex("(今天|今日|明天|明日|后天|本周|这周|现在|实时|当前|最新).*$"), "")
        .removeSuffix("的").removeSuffix("市")
    if (location.length < 2 || location.any { it !in '一'..'鿿' }) return true
    // A redirect URL can contain the requested query even when the result itself belongs to another city.
    val text = title + snippet
    return text.contains(location) && weatherIntent.containsMatchIn(text) &&
        (url.isEmpty() || url.startsWith("https://") || url.startsWith("http://"))
}

internal fun filterSearchResults(query: String, results: List<Result>): List<Result> =
    results.filter { (it.url.startsWith("https://") || it.url.startsWith("http://")) &&
        isSearchResultRelevant(query, it.title, it.snippet, it.url) }

/** Preserve thinking and the executed search tag; drop speculative answer text before and after the tag. */
internal fun searchTimelinePrefix(reply: String): String {
    val open = Regex("<web_search\\b[^>]*>", RegexOption.IGNORE_CASE).find(reply) ?: return ""
    val close = Regex("</web_search\\s*>", RegexOption.IGNORE_CASE).find(reply, open.range.last + 1) ?: return ""
    val thinking = Regex("<think>.*?</think>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .findAll(reply.substring(0, open.range.first)).joinToString("\n") { it.value }
    return (thinking + reply.substring(open.range.first, close.range.last + 1)).trim()
}
