package com.afyzfur.afyzhub.data.remote.provider

import com.afyzfur.afyzhub.data.log.RequestLogContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.afyzfur.afyzhub.data.settings.AppSettings
import com.afyzfur.afyzhub.data.remote.provider.WebSearchService.Result

/** Pure result decoding, separated from transport retries and model instructions. */
internal object SearchResultParser {
    /** 解析 Tavily 返回的 JSON: results[].title/url/content */
    fun parseTavily(json: String, maxResults: Int): List<Result> {
        return try {
            val root = Json.parseToJsonElement(json).jsonObject
            val arr = root["results"]?.jsonArray ?: return emptyList()
            arr.mapNotNull { e ->
                val o = e.jsonObject
                val url = o["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val title = o["title"]?.jsonPrimitive?.contentOrNull ?: url
                val content = o["content"]?.jsonPrimitive?.contentOrNull ?: ""
                Result(title, content, url, siteOf(url))
            }.take(maxResults)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            ParseDiagnostics.failed("WebSearch", e)
            emptyList()
        }
    }
    /**
     * 解析 DuckDuckGo HTML 版结果页。
     *
     * 结果条目的结构稳定多年：
     * - 标题与链接在 `<a class="result__a" href="...">标题</a>`
     * - 摘要在 `<a class="result__snippet">摘要</a>`
     * 两者按出现顺序一一对应，各自正则提取后 zip。
     */
    fun parseResults(html: String, maxResults: Int): List<Result> {
        val linkPattern = SearchPatterns.P1
        val snippetPattern = SearchPatterns.P2

        val links = linkPattern.findAll(html)
            .map { m ->
                Result(
                    title = stripTags(m.groupValues[2]),
                    snippet = "",
                    url = cleanUrl(m.groupValues[1]),
                    site = siteOf(cleanUrl(m.groupValues[1]))
                )
            }
            .toList()

        val snippets = snippetPattern.findAll(html)
            .map { stripTags(it.groupValues[1]) }
            .toList()

        return links.zip(snippets) { link, snippet ->
            link.copy(snippet = snippet)
        }.take(maxResults)
    }

    /**
     * 解析百度 PC 版结果页。
     *
     * 结果标题与链接在 h3 > a 内，链接多为百度跳转格式
     * (/link?url=)，点击后由百度 302 到真址——直接存跳转
     * 链接即可，内置浏览器会跟随重定向。
     */
    fun parseBaidu(html: String, maxResults: Int, query: String): List<Result> {
        val out = mutableListOf<Result>()
        val blockPattern = SearchPatterns.P3
        // 相关性过滤: 抓错区块(推荐卡片/广告)时标题与查询词往往零重合。
        // 查询词去掉常见虚词后的实义字符集, 与标题至少重合 1/4 才采纳。
        val queryChars = meaningfulChars(query)
        for (m in blockPattern.findAll(html)) {
            val url = m.groupValues[1]
            if (url.startsWith("http") || url.startsWith("/link")) {
                val title = stripTags(m.groupValues[2])
                // 覆盖率判据: 标题字符与查询实义字符的重合比例。
                // 之前"命中 1 个字就留"太松——实测「泉州鲤城区天气」因含
                // "天气"二字就能混进「重庆今日天气」的结果里。改为要求
                // 覆盖查询词 50% 以上, 主体地名不符的结果会被剔除。
                val cov = if (queryChars.isEmpty()) 1.0
                    else queryChars.count { it in title.toSet() }.toDouble() / queryChars.size
                if (cov < 0.5) continue
                val full = if (url.startsWith("/")) "https://www.baidu.com" + url else url
                out += Result(title, "", full, siteOf(full))
                if (out.size >= maxResults) break
            }
        }
        return out
    }

    /**
     * 解析 Google 结果页。
     *
     * 结果块以 div.g 开始，标题在第一个 h3，链接取块内第一个
     * 非 google 域的 http 链接。
     */
    fun parseGoogle(html: String, maxResults: Int): List<Result> {
        val blocks = html.split("<div class=\"g\"").drop(1)
        val out = mutableListOf<Result>()
        for (b in blocks) {
            val title = SearchPatterns.P4
                .find(b)?.groupValues?.get(1) ?: continue
            val url = SearchPatterns.P5.findAll(b)
                .map { it.groupValues[1] }
                .firstOrNull { !it.contains("google.") && !it.contains("gstatic.") }
                ?: continue
            out += Result(stripTags(title), "", url, siteOf(url))
            if (out.size >= maxResults) break
        }
        return out
    }

    /**
     * 解析 Bing 移动版结果页。
     *
     * 每条结果在 li.b_algo 块内: 标题与链接在第一个 a 标签,
     * 摘要在第一个 p 标签。按块切分加通用标签匹配,
     * 对页面局部结构调整不敏感。
     */
    /**
     * 解析 Bing RSS 输出。
     *
     * 每条结果是一个 `<item>` 块：title / link / description 各一，
     * 结构由 Bing 官方保证，不受页面改版或广告投放影响。
     */
    fun parseBingRss(xml: String, maxResults: Int, query: String): List<Result> {
        val items = xml.split("<item>").drop(1)
        val out = mutableListOf<Result>()
        // 相关性过滤: RSS 对长查询词会退化成"取前几个词"的泛搜索,
        // 实测「重庆今日天气」返回的是「重庆」百科/旅游结果。与
        // parseBaidu 同判: 标题与查询实义字符零重合的条目剔除。
        val queryChars = meaningfulChars(query)
        for (item in items) {
            val title = SearchPatterns.P6
                .find(item)?.groupValues?.get(1) ?: continue
            val link = SearchPatterns.P7
                .find(item)?.groupValues?.get(1) ?: continue
            val desc = SearchPatterns.P8
                .find(item)?.groupValues?.get(1) ?: ""
            val url = stripTags(link)
            val cleanTitle = stripTags(title)
            // 同 parseBaidu: 覆盖率不到 50% 视为不相关。
            // 「重庆今日天气」的泛化结果(重庆百科/旅游)通常只覆盖
            // "重庆"二字, 会被这里拦下, 进而触发下方的 HTML 兜底。
            if (queryChars.isNotEmpty()) {
                val chars = (cleanTitle + stripTags(desc)).toSet()
                val cov = queryChars.count { it in chars }.toDouble() / queryChars.size
                if (cov < 0.5) continue
            }
            out += Result(
                title = cleanTitle,
                snippet = stripTags(desc),
                url = url,
                site = siteOf(url)
            )
            if (out.size >= maxResults) break
        }
        return out
    }
    /** 查询词的实义字符集: 去空白与常见虚词, 供相关性过滤复用 */
    fun meaningfulChars(q: String): Set<Char> =
        q.filter { !it.isWhitespace() && "的地了吗呢吧呀啊".indexOf(it) < 0 }.toSet()

    /** 从 url 提取站点域名（去 www.），favicon 与署名用 */
    fun siteOf(url: String): String =
        SearchPatterns.P9.find(url)?.groupValues?.get(1) ?: ""

    fun parseBing(html: String, maxResults: Int, query: String): List<Result> {
        val blocks = html.split("<li class=\"b_algo\"").drop(1)
        val out = mutableListOf<Result>()
        val queryChars = meaningfulChars(query)
        for (b in blocks) {
            val url = SearchPatterns.P10.find(b)?.groupValues?.get(1)
                ?: continue
            val title = SearchPatterns.P11
                .find(b)?.groupValues?.get(1) ?: continue
            val snippet = SearchPatterns.P12
                .find(b)?.groupValues?.get(1) ?: ""
                        val cleanTitle = stripTags(title)
            if (queryChars.isNotEmpty()) {
                val chars = (cleanTitle + stripTags(snippet)).toSet()
                val coverage = queryChars.count { it in chars }.toDouble() / queryChars.size
                if (coverage < 0.5) {
                    continue
                }
            }
            out += Result(cleanTitle, stripTags(snippet), url, siteOf(url))
            if (out.size >= maxResults) break
        }
        return out
    }

    /** 去除 HTML 标签与实体，压缩空白 */
    fun stripTags(raw: String): String = raw
        .replace(SearchPatterns.P13, "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&nbsp;", " ")
        .replace(SearchPatterns.P14, " ")
        .trim()

    /**
     * DuckDuckGo 的链接是跳转格式 `//duckduckgo.com/l/?uddg=<encoded>`，
     * 解出真实地址；已是直链则原样返回。
     */
    fun cleanUrl(raw: String): String {
        val uddg = SearchPatterns.P15.find(raw)?.groupValues?.get(1)
            ?: return if (raw.startsWith("//")) "https:$raw" else raw
        return java.net.URLDecoder.decode(uddg, "UTF-8")
    }

}

internal object SearchPatterns {
    val P1 = Regex(
            """<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
            RegexOption.DOT_MATCHES_ALL
        )
    val P2 = Regex(
            """<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""",
            RegexOption.DOT_MATCHES_ALL
        )
    val P3 = Regex(
            "<h3[^>]*>\\s*<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
            RegexOption.DOT_MATCHES_ALL
        )
    val P4 = Regex("<h3[^>]*>(.*?)</h3>", RegexOption.DOT_MATCHES_ALL)
    val P5 = Regex("href=\"(https?://[^\"]+)\"")
    val P6 = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
    val P7 = Regex("<link>(.*?)</link>", RegexOption.DOT_MATCHES_ALL)
    val P8 = Regex("<description>(.*?)</description>", RegexOption.DOT_MATCHES_ALL)
    val P9 = Regex("https?://(?:www\\.)?([^/]+)")
    val P10 = Regex("href=\"([^\"]+)\"")
    val P11 = Regex("<a[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
    val P12 = Regex("<p[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
    val P13 = Regex("<[^>]+>")
    val P14 = Regex("\\s+")
    val P15 = Regex("""[?&]uddg=([^&]+)""")
    val P16 = Regex("""<web_search>(.*?)</web_search>""", RegexOption.DOT_MATCHES_ALL)
}
