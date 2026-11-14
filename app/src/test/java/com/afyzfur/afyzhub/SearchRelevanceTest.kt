package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.data.remote.provider.WebSearchService
import com.afyzfur.afyzhub.data.remote.provider.filterSearchResults
import com.afyzfur.afyzhub.data.remote.provider.isSearchResultRelevant
import com.afyzfur.afyzhub.data.remote.provider.searchTimelinePrefix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRelevanceTest {
    @Test fun cityWeatherSearchRejectsOtherCitiesAndCalendarPages() {
        assertTrue(isSearchResultRelevant("重庆今天的天气", "重庆天气预报", "重庆今天阴有小雨"))
        assertTrue(isSearchResultRelevant("重庆今天天气", "重庆天气预报", "重庆今天阴有小雨"))
        assertFalse(isSearchResultRelevant("重庆今天天气", "佛山禅城区天气预报", "禅城区今天多云"))
        assertFalse(isSearchResultRelevant("重庆今天天气", "今日黄历宜忌查询", "今天老黄历"))
        val items = listOf(
            WebSearchService.Result("重庆天气", "今日小雨", "https://example.test/chongqing"),
            WebSearchService.Result("禅城天气", "降雨", "https://example.test/foshan"),
            WebSearchService.Result("重庆黄历", "吉凶查询", "https://example.test/calendar")
        )
        assertEquals(listOf("重庆天气"), filterSearchResults("重庆今天天气", items).map { it.title })
    }

    @Test fun onlySearchProtocolPrefixIsCarriedIntoSecondRequest() {
        val original = "我暂时没拿到实时天气数据。<web_search>重庆今天天气</web_search>我来继续查。"
        assertEquals("<web_search>重庆今天天气</web_search>", searchTimelinePrefix(original))
        assertEquals("", searchTimelinePrefix("我现在去查一下。"))
        assertEquals("<WEB_SEARCH>天气</WEB_SEARCH>", searchTimelinePrefix("草稿\n<WEB_SEARCH>天气</WEB_SEARCH>后续草稿"))
    }
    @Test fun bingUsesHeadingLinkRatherThanFaviconAndTavilyRequestsAdvanced() {
        val html = """<li class="b_algo"><a href="https://favicon.test">图标</a><h2><a href="https://weather.test/cq">重庆天气预报</a></h2><p>重庆今日降雨</p></li>"""
        val results = com.afyzfur.afyzhub.data.remote.provider.SearchResultParser.parseBing(html, 5, "重庆今日天气")
        assertEquals("https://weather.test/cq", results.single().url)
        assertEquals("重庆天气预报", results.single().title)
        assertEquals("advanced", com.afyzfur.afyzhub.data.remote.provider.TavilyRequest("key", "query", 5).searchDepth)
    }

    @Test fun redirectsDoNotTurnWrongCityIntoARelevantResult() {
        assertFalse(isSearchResultRelevant("重庆今日天气", "佛山天气", "佛山降雨", "https://search.test/?q=重庆今日天气"))
        assertTrue(isSearchResultRelevant("今天重庆天气", "重庆天气预报", "重庆阴天"))
        assertTrue(isSearchResultRelevant("我查一下重庆今天的天气", "重庆天气预报", "重庆阴天"))
        assertTrue(isSearchResultRelevant("weather London today", "London forecast", "cloudy"))
        assertTrue(isSearchResultRelevant("Kotlin coroutines", "Coroutine guide", "Programming"))
        assertTrue(filterSearchResults("重庆天气", listOf(WebSearchService.Result("搜索失败", "重庆天气诊断", ""))).isEmpty())
        assertEquals("<think>分析</think><web_search>重庆天气</web_search>",
            searchTimelinePrefix("<think>分析</think>我猜是晴天<web_search>重庆天气</web_search>又猜一次"))
    }

}