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
            WebSearchService.Result("重庆今日天气", "重庆温度查询", "https://example.test/chongqing-weather")
        )
        assertEquals(listOf("重庆天气", "重庆今日天气"), filterSearchResults("重庆今天天气", items).map { it.title })
    }

    @Test fun onlySearchProtocolPrefixIsCarriedIntoSecondRequest() {
        val original = "我暂时没拿到实时天气数据。<web_search>重庆今天天气</web_search>我来继续查。"
        assertEquals("<web_search>重庆今天天气</web_search>", searchTimelinePrefix(original))
        assertEquals("", searchTimelinePrefix("我现在去查一下。"))
        assertEquals("<WEB_SEARCH>天气</WEB_SEARCH>", searchTimelinePrefix("草稿\n<WEB_SEARCH>天气</WEB_SEARCH>后续草稿"))
    }
}