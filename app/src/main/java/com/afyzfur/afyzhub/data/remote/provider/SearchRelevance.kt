package com.afyzfur.afyzhub.data.remote.provider

import com.afyzfur.afyzhub.data.remote.provider.WebSearchService.Result

/** Keep location-bound results local to the requested place; character overlap is not enough. */
internal fun isSearchResultRelevant(query: String, title: String, snippet: String, url: String = ""): Boolean {
    val normalizedQuery = query.lowercase().replace(Regex("\\s+"), "")
    val result = (title + snippet + url).lowercase().replace(Regex("\\s+"), "")
    if (normalizedQuery.isBlank()) return false

    val weatherTerms = listOf("天气预报", "天气", "气温", "降雨", "降水", "weather", "temperature")
    val weatherIndex = weatherTerms.map { normalizedQuery.indexOf(it) }.filter { it >= 0 }.minOrNull()
    if (weatherIndex != null) {
        // The part before the weather intent is the location phrase, not generic time words.
        val location = normalizedQuery.substring(0, weatherIndex)
            .replace(Regex("(今天|今日|明天|明日|后天|本周|这周|现在|实时|当前|最新).*$"), "")
            .removeSuffix("的")
        if (location.length >= 2 && location.any { it in '\u4e00'..'\u9fff' }) {
            return result.contains(location)
        }
        return false
    }
    val queryChars = normalizedQuery.filter { !it.isWhitespace() && it !in "的地了吗呢吧呀啊今天今日明天明日现在查询一下天气" }.toSet()
    if (queryChars.isEmpty()) return true
    return queryChars.count { it in result }.toDouble() / queryChars.size >= 0.5
}

internal fun filterSearchResults(query: String, results: List<Result>): List<Result> =
    results.filter { isSearchResultRelevant(query, it.title, it.snippet, it.url) }

/** Keep the actual search event, not speculative prose emitted before the model asked to search. */
internal fun searchTimelinePrefix(reply: String): String {
    val open = Regex("<web_search\\b[^>]*>", RegexOption.IGNORE_CASE).find(reply) ?: return ""
    val close = Regex("</web_search\\s*>", RegexOption.IGNORE_CASE).find(reply, open.range.last + 1) ?: return ""
    return reply.substring(0, close.range.last + 1).trim()
}