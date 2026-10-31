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

/**
 * 应用层网络搜索。
 *
 * 目标：任何模型（无论提供商是否支持服务端联网）都能获取网络信息。
 * 原理：提示词要求模型在需要信息时输出 `<search>查询词</search>`，
 * 发送流程检测到该标签后调用本服务抓取搜索结果，把结果作为
 * 上下文再次请求，模型基于结果作答。
 *
 * 搜索源用 DuckDuckGo 的 HTML 版（html.duckduckgo.com）：
 * 无需 API Key、无请求频次的注册流程，返回的简化页面用正则即可
 * 提取标题、摘要与链接。稳定性依赖第三方页面结构，但作为
 * "聊天下文的补充信息"够用——失败时降级为无搜索继续回答。
 */
/**
 * 联网搜索引擎。
 *
 * 默认 Bing：对移动 UA 最宽容且无地域墙。百度作为国内网络
 * 环境的可靠备选，Google 需要设备本身可达。选择存于设置，
 * 搜索时若所选引擎失败会自动按 BING → BAIDU → GOOGLE 降级。
 */

class WebSearchService(
    private val transport: Transport
) {

    /** 一次搜索的结果条目 */
    data class Result(
        val title: String,
        val snippet: String,
        val url: String,
        /** 来源站点域名，如 "zhihu.com"，用于 favicon 与署名 */
        val site: String = ""
    )

    /**
     * 执行搜索，返回前 [maxResults] 条结果。
     *
     * 任何异常都返回空列表：搜索是增强能力，失败不该让整条消息
     * 发送失败——模型会按无搜索结果继续回答（通常会说明信息不足）。
     */
    /**
     * 执行搜索，返回前 [maxResults] 条结果。
     *
     * 先按用户设置的引擎查，空结果或异常时按固定顺序降级到
     * 其余引擎——各家反爬策略不同，单一引擎可靠性不够。
     * 全部失败才返回空列表：搜索是增强能力，失败不该让整条
     * 消息发送失败，模型会按无结果路径兜底回答。
     */
    suspend fun search(
        query: String,
        maxResults: Int = 5,
        engineId: String? = null,
        tavilyApiKey: String? = null
    ): List<Result> {
        if (query.isBlank()) return emptyList()
        val engine = SearchEngine.fromId(engineId)
        return try {
            when (engine) {
                SearchEngine.BING -> searchBing(query, maxResults)
                SearchEngine.BAIDU -> searchBaidu(query, maxResults)
                SearchEngine.GOOGLE -> searchGoogle(query, maxResults)
                SearchEngine.TAVILY -> searchTavily(query, maxResults, tavilyApiKey)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            ParseDiagnostics.failed("WebSearch", e)
            emptyList()
        }
    }

    /**
     * Tavily 搜索。
     *
     * 走官方 JSON 接口 POST /search, 直接拿结构化结果。
     * 没填 Key 时说明用户还没配置, 自动回退到 Bing 保证"至少能搜",
     * 而不是直接报错——搜索是增强能力, 不该让整条消息失败。
     */
    private suspend fun searchTavily(query: String, maxResults: Int, apiKey: String?): List<Result> {
        if (apiKey.isNullOrBlank()) {
            return searchBing(query, maxResults)
        }
        val body = Json.encodeToString(TavilyRequest(apiKey, query, maxResults.coerceIn(1, 20)))
        val resp = transport.postForText(
            baseUrl = "https://api.tavily.com",
            path = "/search",
            headers = mapOf("Content-Type" to "application/json"),
            body = body,
            logContext = RequestLogContext(provider = "web-search", model = "tavily")
        )
        val parsed = parseTavily(resp, maxResults)
        if (parsed.isNotEmpty()) return parsed
        // Tavily 也空(额度用尽/网络问题): 回退 Bing, 不让搜索彻底失败
        return searchBing(query, maxResults)
    }

    private suspend fun searchBing(query: String, maxResults: Int): List<Result> {
        // 一级: RSS 输出——结构稳定多年、无广告与 SEO 垃圾，
        // 每条 item 固定为 title/link/description 三件套。
        // 实测缺陷: 对含时间限定词的长查询(「重庆今日天气」)会退化成
        // 只取前几个词的泛搜索, 所以 RSS 全空或全被相关性过滤剔除时,
        // 降级走 HTML 版(其结果按完整查询词组织)。
        val xml = try {
            transport.getForText(
                baseUrl = "https://www.bing.com",
                path = "/search",
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36",
                    "Accept-Language" to "zh-CN,zh;q=0.9"
                ),
                query = mapOf("q" to query, "format" to "rss", "count" to maxResults.toString()),
                logContext = RequestLogContext(provider = "web-search", model = "bing-rss")
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            ""
        }
        val fromRss = if (xml.isBlank()) emptyList() else parseBingRss(xml, maxResults, query)
        if (fromRss.isNotEmpty()) {
            // RSS 退化检测: RSS 对长查询会退化成只取前几个词的泛搜索
            // (实测「重庆今日天气」返回「重庆」百科/旅游)。退化特征是
            // 连最好的结果也覆盖不了查询词六成实义字符——「重庆旅游
            // 攻略」只覆盖「重庆」二字, 拦不住零重合过滤, 必须看比例。
            val qc = meaningfulChars(query)
            val bestCoverage = if (qc.isEmpty()) 1.0 else fromRss.maxOf { r ->
                val chars = (r.title + r.snippet).toSet()
                qc.count { it in chars }.toDouble() / qc.size
            }
            if (bestCoverage >= 0.5) {
                return fromRss
            }
        }
        // 二级: HTML 版 + 桌面 UA(移动 UA 的结果页结构不同且易触发自适应布局)
        val html = transport.getForText(
            baseUrl = "https://www.bing.com",
            path = "/search",
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
                "Accept-Language" to "zh-CN,zh;q=0.9"
            ),
            query = mapOf("q" to query, "count" to maxResults.toString()),
            logContext = RequestLogContext(provider = "web-search", model = "bing-html")
        )
        val htmlRes = parseBing(html, maxResults, query)
        if (htmlRes.isEmpty()) {
            // 两级都拿不到相关结果: 明确告知, 而不是把无关结果塞给用户
            return listOf(Result(
                title = "必应未返回相关结果",
                snippet = "已尝试 RSS 与网页两路。可能被反爬或按地域重排。建议: ① 换百度引擎 ② 换个问法 ③ 复制请求日志发开发者。诊断码: BING_ALL_EMPTY",
                url = "",
                site = "诊断"
            ))
        }
        return htmlRes
    }
    private suspend fun searchBaidu(query: String, maxResults: Int): List<Result> {
        // 多策略重试: 桌面 Chrome UA 在实测网络可用, 但部分网络环境
        // (运营商代理/反爬差异)可能拦掉某个 UA。同引擎内依次换 UA,
        // 全部失败才返回空——不跨引擎, 尊重用户的引擎选择。
        // 实测依据: 移动 UA 返回 cosc-title 结构(链接在 JS 里抓不到),
        // 桌面 UA 返回经典 h3+a 结构可解析, 所以只用桌面 UA 系列。
        val strategies = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
        )
        for ((index, ua) in strategies.withIndex()) {
            val attempt = index + 1
            val html = try {
                transport.getForText(
                    baseUrl = "https://www.baidu.com",
                    path = "/s",
                    headers = mapOf(
                        "User-Agent" to ua,
                        "Accept" to "text/html,application/xhtml+xml",
                        "Accept-Language" to "zh-CN,zh;q=0.9"
                    ),
                    query = mapOf("wd" to query, "rn" to maxResults.toString(), "ie" to "utf-8"),
                    logContext = RequestLogContext(provider = "web-search", model = "baidu-$attempt")
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                continue
            }
            val parsed = parseBaidu(html, maxResults, query)
            if (parsed.isNotEmpty()) return parsed
        }
        val diag = "[AfyzSearch] BAIDU_ALL_FAILED query=$query attempts=${strategies.size}"
        // 返回特殊诊断条目: 标题就是错误信息, snippet 是建议
        return listOf(Result(
            title = "百度搜索失败（已尝试 ${strategies.size} 个 UA）",
            snippet = "网络问题或被反爬拦截。建议: ① 换必应引擎 ② 检查网络 ③ 复制请求日志发开发者。诊断码: $diag",
            url = "",
            site = "诊断"
        ))
    }
    private suspend fun searchGoogle(query: String, maxResults: Int): List<Result> {
        val html = transport.getForText(
            baseUrl = "https://www.google.com",
            path = "/search",
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36",
                "Accept-Language" to "zh-CN,zh;q=0.9"
            ),
            query = mapOf("q" to query, "num" to maxResults.toString()),
            logContext = RequestLogContext(provider = "web-search", model = "google")
        )
        return parseGoogle(html, maxResults)
    }

    private fun parseTavily(json: String, maxResults: Int): List<Result> = SearchResultParser.parseTavily(json, maxResults)
    private fun parseResults(html: String, maxResults: Int): List<Result> = SearchResultParser.parseResults(html, maxResults)
    private fun parseBaidu(html: String, maxResults: Int, query: String): List<Result> = SearchResultParser.parseBaidu(html, maxResults, query)
    private fun parseGoogle(html: String, maxResults: Int): List<Result> = SearchResultParser.parseGoogle(html, maxResults)
    private fun parseBingRss(xml: String, maxResults: Int, query: String): List<Result> = SearchResultParser.parseBingRss(xml, maxResults, query)
    private fun meaningfulChars(q: String): Set<Char> = SearchResultParser.meaningfulChars(q)
    private fun siteOf(url: String): String = SearchResultParser.siteOf(url)
    private fun parseBing(html: String, maxResults: Int, query: String): List<Result> = SearchResultParser.parseBing(html, maxResults, query)
    private fun stripTags(raw: String): String = SearchResultParser.stripTags(raw)
    private fun cleanUrl(raw: String): String = SearchResultParser.cleanUrl(raw)

    companion object {
        /**
         * 搜索指令，注入到系统提示词末尾。
         *
         * 只在联网开启且提供商无原生搜索时注入（Gemini 的服务端
         * grounding 质量更高，走原生）。措辞要点：
         * - 明确"仅在需要时"——避免每个问题都触发一轮额外请求
         * - 要求查询词精炼——它就是搜索框里的输入
         */
        fun instruction(): String = """
            |你可以在需要实时或最新信息时使用网络搜索。
            |方法：紧接着你只输出一行，把你真实想搜的完整关键词写在下面这个标签内，然后立即停止输出：
            |<web_search>北京今日天气</web_search>
            |上例仅示意格式，务必换成你自己想搜的内容；
            |查询词必须是完整需求，包含问题的全部关键信息。
            |例如用户问「重庆的天气」→ 查询词写「重庆今日天气」；
            |用户问「XX的股价」→ 查询词写「XX最新股价」。
            |绝不能丢字缩写(如把「重庆的天气」缩成「重庆市」)，
            |一次搜索就要覆盖用户的完整问题；
            |绝不能照抄示例、也不能输出“关键词”这类占位词。
            |系统会执行搜索并把结果提供给你，你再基于结果回答。
            |无需搜索时直接回答，不要输出任何标签。
        """.trimMargin()
        /** 从模型输出中提取搜索查询词 */
        fun extractQuery(reply: String): String? {
            // 容错: 部分模型把标签写进代码围栏、或拼成大写/带空格,
            // 也有把搜索词包在引号里的。去掉代码围栏后大小写不敏感地取。
            val stripped = reply.replace("```", "")
            val open = "<web_search>"
            val openLoose = "<web_search"
            val close = "</web_search>"
            val lowered = stripped.lowercase()
            var q: String? = null
            val oi = lowered.indexOf(open)
            if (oi >= 0) {
                val ci = lowered.indexOf(close, oi + open.length)
                if (ci >= 0) q = stripped.substring(oi + open.length, ci)
            }
            if (q == null) {
                val li = lowered.indexOf(openLoose)
                if (li >= 0) {
                    var rest = stripped.substring(li + openLoose.length)
                    val gt = rest.indexOf('>')
                    if (gt >= 0) rest = rest.substring(gt + 1)
                    val nl = rest.indexOf('\n')
                    if (nl >= 0) rest = rest.substring(0, nl)
                    q = rest
                }
            }
            val cleaned = q?.trim()?.trim('"', '\'', '“', '”', ',')?.takeIf { it.isNotBlank() } ?: return null
            val placeholders = setOf("关键词", "要搜的词", "搜索词", "搜索关键词", "query", "search", "你真实想搜索的内容", "2025中秋节是几月几号", "openai最新模型发布时间")
            return if (cleaned.lowercase() in placeholders) null else cleaned
        }
                /** 把搜索结果格式化为注入上下文的文本 */
        fun formatResults(results: List<Result>): String {
            if (results.isEmpty()) {
                return "（搜索没有返回结果。请基于已有知识回答，并向用户说明信息可能过时。）"
            }
            return results.joinToString("\n\n") { r ->
                "${r.title}\n${r.snippet}\n来源: ${r.url}"
            }
        }

        /**
         * 清理模型输出的复读标签。
         *
         * 二轮请求的上下文里带有搜索与来源标签, 模型有时照样子在自己的输出里
         * 也包一层。复读的闭合 sources 会把整段正文当来源剥掉(正文消失),
         * 复读的 web_search 会在正文里多出搜索块。这里剥掉标签对本身,
         * sources 内的文本(往往是正文)保留, 官方来源块由发送流程统一追加。
         */
        /**
         * 剔掉正文中“整行只有网址”的裸链接。
         *
         * 部分模型把搜索到的链接一行一条直接摆出来当正文, 而来源已由
         * 界面的搜索块单独展示, 正文再罗列只是噪音。只删“从行首到行尾
         * 整行就是一个网址”的行, 句子里嵌的链接不动。
         */
        fun stripBareUrlLines(content: String): String {
            return content.lines()
                .filterNot { line ->
                    val s = line.trim()
                    (s.startsWith("http://") || s.startsWith("https://")) && (!s.contains(" "))
                }
                .joinToString("\n")
                .trim()
        }
        /**
         * 剥掉内容里的搜索标签对, 只留内部文字。
         *
         * 二次请求时把第一轮回复当作历史传回, 若其中残留 web_search
         * 标签, 模型会学着再输出一遍、触发重复搜索。传回前先剥掉。
         */
        /**
         * 第一轮内容只保留到第一个搜索标签的闭标签为止。
         *
         * 模型常在输出搜索标签后继续写“根据搜索结果…”之类的草稿,
         * 而真正的回答来自第二轮。草稿留在正文里会和第二轮回答重复,
         * 表现为“回答两次”。这里把标签之后的内容全部截掉。
         */
        fun truncateAfterFirstSearchTag(content: String): String {
            val oi = content.indexOf("<web_search>")
            if (oi < 0) return content
            val ci = content.indexOf("</web_search>", oi)
            if (ci < 0) return content
            return content.substring(0, ci + "</web_search>".length)
        }
        fun stripSearchTagsOnly(content: String): String {
            return content
                .replace("<web_search>", "")
                .replace("</web_search>", "")
        }
        /**
         * 去重正文里重复的搜索标签对。
         *
         * 二次请求以第一轮内容(含搜索标签)为流式起点,
         * 若模型又复读一个标签, 最终内容里会出现两个,
         * UI 就渲染出两个搜索块(其中一个从未真正搜过,
         * 永远显示“正在获取…”)。这里把内容相同的
         * 搜索标签只保留第一个。
         */
        fun dedupeSearchTags(content: String): String {
            val seen = HashSet<String>()
            val regex = SearchPatterns.P16
            return regex.replace(content) { m ->
                val q = m.groupValues[1].trim()
                if (seen.add(q)) m.value else ""
            }
        }
        fun stripModelEchoTags(content: String): String {
            // 只剥模型复读的 sources 标签对: 复读的闭合 sources 会把整段正文
            // 当来源剥掉(正文消失)。sources 内的文本(往往是正文)保留。
            // web_search 标签不剥: 一轮输出的搜索标签是合法记录, 剥掉会让
            // 搜索栏消失、思考段错位; 复读的重复搜索块由 UI 端 seenQueries 去重。
            return content
                .replace("<sources>", "")
                .replace("</sources>", "")
                .trim()
        }
    }
}

