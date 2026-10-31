package com.afyzfur.afyzhub.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.afyzfur.afyzhub.data.remote.provider.SearchEngine
import com.afyzfur.afyzhub.data.log.LogRetention
import com.afyzfur.afyzhub.domain.model.AiProvider
import com.afyzfur.afyzhub.domain.model.ApiProfileStore
import com.afyzfur.afyzhub.domain.model.ThinkingEffort
import com.afyzfur.afyzhub.ui.theme.ThemePalette
import kotlinx.serialization.json.Json
import com.afyzfur.afyzhub.util.Constants
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 当前生效的设置。 */
data class AppSettings(
    val provider: AiProvider = AiProvider.DEFAULT,
    /**
     * 生效配置组的显示名。
     *
     * 与 [provider] 并存：前者决定用哪套协议，这个只用于展示。日志里
     * 记的是它而非提供商名——同一个提供商下可以有多组配置（不同中转、
     * 不同密钥），只显示 "OpenAI" 区分不出是哪一组。
     *
     * 没有配置组时回退到提供商名。
     */
    val profileLabel: String = AiProvider.DEFAULT.displayName,
    val apiKey: String = "",
    val model: String = AiProvider.DEFAULT.fallbackModel,
    val baseUrl: String = AiProvider.DEFAULT.defaultBaseUrl,
    /** 是否使用流式输出，默认开启。 */
    val streamEnabled: Boolean = true,
    /**
     * 思考程度。全局项，不随配置组切换。
     *
     * 放在 AppSettings 而非 ApiProfile：它更像"这次想让模型想多久"的
     * 临时选择，用户会频繁在输入栏切换，而不是某组配置的固定属性。
     */
    val thinkingEffort: ThinkingEffort = ThinkingEffort.DEFAULT,
    val webSearchEnabled: Boolean = false,
    /**
     * 内置浏览器总开关。关闭时消息里的链接走系统浏览器。
     * 它与联网搜索相互独立：搜索是模型取信息的通道，
     * 这里只管"链接点开后在哪儿展示"。
     */
    val inAppBrowserEnabled: Boolean = true,
    /** 联网搜索用的搜索引擎 id，取值见 [SearchEngine] */
    val searchEngine: String = SearchEngine.BING.id,
    /** Tavily 专业搜索的 API Key（选 Tavily 引擎时必填，其余引擎忽略） */
    val tavilyApiKey: String = "",
    /**
     * Gemini 原生联网搜索（服务端 grounding）。
     *
     * 只作用于 Gemini 协议，与其他模型的应用层搜索
     * ([AppSettings.webSearchEnabled]) 相互独立。
     * 放在 API 配置而非全局设置：它是协议能力，随配置组走。
     */
    val geminiSearchEnabled: Boolean = false,
    /**
     * 系统提示词。空串表示不注入。
     *
     * 全局项而非按配置组：它描述"我希望这个助手是什么样"，
     * 与用哪家的 API 无关。
     */
    val openingPrompt: String = "",
    val systemPrompt: String = ""
)

