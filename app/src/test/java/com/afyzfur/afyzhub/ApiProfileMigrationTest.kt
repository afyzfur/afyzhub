package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.data.settings.LegacyProviderConfig
import com.afyzfur.afyzhub.data.settings.migrateLegacyConfigs
import com.afyzfur.afyzhub.domain.model.AiProvider
import com.afyzfur.afyzhub.domain.model.ApiProfile
import com.afyzfur.afyzhub.domain.model.ApiProfileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 旧单组配置迁移到多组的测试。
 *
 * 这里出错的代价很高——用户填过的 Key 会凭空消失，而且发生在升级
 * 之后、没有回退路径。各种历史状态组合都要固定住。
 */
class ApiProfileMigrationTest {

    private fun legacy(
        provider: AiProvider,
        key: String = "",
        baseUrl: String = "",
        model: String = "",
        models: List<String> = emptyList()
    ) = LegacyProviderConfig(provider, key, baseUrl, model, models)

    @Test
    fun `填过的提供商各成一组`() {
        val store = migrateLegacyConfigs(
            configs = listOf(
                legacy(AiProvider.OPENAI, key = "sk-1"),
                legacy(AiProvider.ANTHROPIC, key = "sk-ant-1"),
                legacy(AiProvider.GEMINI)
            ),
            activeProvider = AiProvider.OPENAI
        )

        assertEquals(2, store.profiles.size)
        assertTrue(store.profiles.none { it.providerId == AiProvider.GEMINI.id })
    }

    @Test
    fun `迁移后保留原来的选中项`() {
        val store = migrateLegacyConfigs(
            configs = listOf(
                legacy(AiProvider.OPENAI, key = "sk-1"),
                legacy(AiProvider.ANTHROPIC, key = "sk-ant-1")
            ),
            activeProvider = AiProvider.ANTHROPIC
        )

        assertEquals(AiProvider.ANTHROPIC.id, store.active?.providerId)
    }

    @Test
    fun `原选中项没配置过时退回第一组`() {
        // 用户选了 Gemini 却没填 Key，此时不能让 active 指向不存在的组
        val store = migrateLegacyConfigs(
            configs = listOf(
                legacy(AiProvider.OPENAI, key = "sk-1"),
                legacy(AiProvider.GEMINI)
            ),
            activeProvider = AiProvider.GEMINI
        )

        assertEquals(1, store.profiles.size)
        assertEquals(AiProvider.OPENAI.id, store.active?.providerId)
    }

    @Test
    fun `Key 地址模型任一填过就算配置过`() {
        // 只改过地址没填 Key 的情况（中转试配）也要保住
        val store = migrateLegacyConfigs(
            configs = listOf(legacy(AiProvider.OPENAI, baseUrl = "https://x.cc/")),
            activeProvider = AiProvider.OPENAI
        )

        assertEquals(1, store.profiles.size)
        assertEquals("https://x.cc/", store.profiles.first().baseUrl)
    }

    @Test
    fun `全都没配置过时不建任何组`() {
        val store = migrateLegacyConfigs(
            configs = AiProvider.entries.map { legacy(it) },
            activeProvider = AiProvider.OPENAI
        )

        assertTrue(store.profiles.isEmpty())
        assertNull("没有组时 active 应为 null，由界面提示新建", store.active)
    }

    @Test
    fun `模型列表缓存一并迁移`() {
        val store = migrateLegacyConfigs(
            configs = listOf(
                legacy(AiProvider.OPENAI, key = "sk-1", models = listOf("a", "b"))
            ),
            activeProvider = AiProvider.OPENAI
        )

        assertEquals(listOf("a", "b"), store.profiles.first().cachedModels)
    }

    @Test
    fun `active 在 id 失效时退回第一组`() {
        // activeId 指向已删除的组，取值不该返回 null
        val store = ApiProfileStore(
            profiles = listOf(ApiProfile(id = "a", name = "甲")),
            activeId = "已删除的id"
        )

        assertNotNull(store.active)
        assertEquals("a", store.active?.id)
    }

    @Test
    fun `未分组的排在具名分组之后`() {
        val store = ApiProfileStore(
            profiles = listOf(
                ApiProfile(id = "1", name = "散的"),
                ApiProfile(id = "2", name = "乙", group = "工作"),
                ApiProfile(id = "3", name = "甲", group = "个人")
            )
        )

        val groups = store.grouped().map { it.first }
        assertEquals(listOf("个人", "工作", ""), groups)
    }

    @Test
    fun `留空的地址与模型回落到提供商默认值`() {
        val profile = ApiProfile(
            id = "1",
            name = "空配置",
            providerId = AiProvider.OPENAI.id
        )

        assertEquals(AiProvider.OPENAI.defaultBaseUrl, profile.effectiveBaseUrl)
        assertEquals(AiProvider.OPENAI.fallbackModel, profile.effectiveModel)
    }
}
