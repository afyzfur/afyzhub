package com.afyzfur.afyzhub.data.settings

import com.afyzfur.afyzhub.domain.model.AiProvider
import com.afyzfur.afyzhub.domain.model.ApiProfile
import com.afyzfur.afyzhub.domain.model.ApiProfileStore

/**
 * 旧版单组配置的一份快照，用于迁移。
 *
 * 旧格式是「每个提供商各一份 Key / 地址 / 模型」，键名带提供商后缀。
 */
data class LegacyProviderConfig(
    val provider: AiProvider,
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val cachedModels: List<String>
) {
    /** 一组都没填过的提供商不值得迁移成配置组 */
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() || baseUrl.isNotBlank() || model.isNotBlank()
}

/**
 * 把旧的按提供商单组配置转成多组结构。
 *
 * 只迁移填过内容的提供商：全部三家都建一组会让新用户一进来就看到
 * 两组空配置。若一组都没填过，返回一个空 store，由界面提示新建。
 *
 * [activeProvider] 是旧版当前选中的提供商，迁移后它对应的那组
 * 继续保持选中，用户不会感觉配置被换掉了。
 *
 * 抽成纯函数与 DataStore 解耦，便于测试各种历史状态组合。
 */
fun migrateLegacyConfigs(
    configs: List<LegacyProviderConfig>,
    activeProvider: AiProvider,
    idFactory: (AiProvider) -> String = { "legacy-${it.id}" }
): ApiProfileStore {
    val configured = configs.filter { it.isConfigured }
    if (configured.isEmpty()) return ApiProfileStore()

    val profiles = configured.map { config ->
        ApiProfile(
            id = idFactory(config.provider),
            // 用提供商名做初始名称：迁移来的组用户没起过名，
            // 显示"OpenAI"比显示空白或"配置1"更容易对应上
            name = config.provider.displayName,
            group = "",
            providerId = config.provider.id,
            apiKey = config.apiKey,
            baseUrl = config.baseUrl,
            model = config.model,
            cachedModels = config.cachedModels
        )
    }

    // 旧的选中项若没配置过（比如选了却没填 Key），退回第一组
    val active = profiles.firstOrNull { it.providerId == activeProvider.id }
        ?: profiles.first()

    return ApiProfileStore(profiles = profiles, activeId = active.id)
}
