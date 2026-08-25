package com.afyzfur.afyzhub.domain.model

import kotlinx.serialization.Serializable

/**
 * 一组命名的 API 配置。
 *
 * 此前每个提供商只能存一份 Key / 地址 / 模型，同一家有多个 Key
 * （不同额度、不同中转、测试与生产）时只能来回覆盖。现在改为
 * 可以建任意多组，每组自带名称与分组，互不影响。
 *
 * [id] 由创建时生成且不再变化：名称允许重复也允许改，用它做标识
 * 会让「当前选中哪一组」在改名后失效。
 */
@Serializable
data class ApiProfile(
    val id: String,
    /** 用户可见的名称，如「主号」「中转-便宜」 */
    val name: String,
    /** 用于归类的分组名，空串表示未分组 */
    val group: String = "",
    val providerId: String = AiProvider.DEFAULT.id,
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = "",
    /** 该组上次拉取到的模型列表，避免每次重进都要重新获取 */
    val cachedModels: List<String> = emptyList()
) {
    val provider: AiProvider get() = AiProvider.fromId(providerId)

    /** 名称为空时给一个可读的兜底，避免列表里出现空白行 */
    val displayName: String
        get() = name.ifBlank { provider.displayName }

    /** 地址留空时用提供商默认值，与旧逻辑一致 */
    val effectiveBaseUrl: String
        get() = baseUrl.ifBlank { provider.defaultBaseUrl }

    /** 模型留空时用提供商兜底模型 */
    val effectiveModel: String
        get() = model.ifBlank { provider.fallbackModel }
}

/**
 * 全部 API 配置组与当前选中项。
 *
 * [activeId] 可能指向已删除的组（并发或异常情况），因此取值统一
 * 走 [active]，它在找不到时退回第一组而非返回 null——界面上
 * 永远有一组是生效的，不需要处理"未选中"这个额外状态。
 */
@Serializable
data class ApiProfileStore(
    val profiles: List<ApiProfile> = emptyList(),
    val activeId: String = ""
) {
    val active: ApiProfile?
        get() = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()

    /** 按分组归类，保持组内原有顺序。未分组的排在最后 */
    fun grouped(): List<Pair<String, List<ApiProfile>>> {
        val byGroup = profiles.groupBy { it.group }
        val named = byGroup.filterKeys { it.isNotBlank() }
            .toList()
            .sortedBy { it.first }
        val ungrouped = byGroup[""].orEmpty()
        return if (ungrouped.isEmpty()) named else named + ("" to ungrouped)
    }
}
