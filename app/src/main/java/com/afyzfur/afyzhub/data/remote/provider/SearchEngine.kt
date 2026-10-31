package com.afyzfur.afyzhub.data.remote.provider

enum class SearchEngine(val id: String, val label: String, val needsApiKey: Boolean = false) {
    BING("bing", "Bing"),
    BAIDU("baidu", "百度"),
    GOOGLE("google", "Google"),

    /**
     * Tavily 专业搜索 API。
     *
     * 与上面三家不同: 它们靠抓网页 HTML, 对含时间/地点的长查询
     * 会退化成泛搜索; Tavily 是给 AI 用的搜索接口, 直接返回
     * 结构化 JSON(标题/摘要/链接/相关度), 由搜索引擎按完整查询
     * 语义返回, 不会出现"搜重庆天气返回日历"的问题。
     * 需要在设置里填 API Key(https://tavily.com 免费额度)。
     */
    TAVILY("tavily", "Tavily(专业搜索)", needsApiKey = true);
    companion object {
        val DEFAULT = BING
        fun fromId(id: String?): SearchEngine {
            val key = id?.trim()?.lowercase()
            return entries.firstOrNull { it.id == key || it.name.lowercase() == key || it.label == id?.trim() } ?: DEFAULT
        }
    }
}

