package com.afyzfur.afyzhub

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.afyzfur.afyzhub.data.settings.SettingsRepository
import com.afyzfur.afyzhub.domain.model.ThinkingEffort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingEffortTest {
    private class MemoryStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    @Test fun exposesFiveOrderedLevels() {
        assertEquals(listOf("low", "medium", "high", "xhigh", "max"), ThinkingEffort.entries.map { it.id })
        assertEquals(listOf("低", "中", "高", "极高", "最大"), ThinkingEffort.entries.map { it.label })
    }

    @Test fun providerMappingsStayWithinSupportedOpenAiEfforts() {
        assertEquals("low", ThinkingEffort.openAiEffort(ThinkingEffort.LOW))
        assertEquals("medium", ThinkingEffort.openAiEffort(ThinkingEffort.MEDIUM))
        assertEquals("high", ThinkingEffort.openAiEffort(ThinkingEffort.HIGH))
        assertEquals("high", ThinkingEffort.openAiEffort(ThinkingEffort.XHIGH))
        assertEquals("high", ThinkingEffort.openAiEffort(ThinkingEffort.MAX))
    }

    @Test fun legacyOffMapsToDisabledMasterAndDefaultSlider() {
        assertEquals(ThinkingEffort.MEDIUM, ThinkingEffort.fromId("off"))
        assertFalse(checkNotNull(ThinkingEffort.legacyThinkingEnabled("off")))
        assertTrue(checkNotNull(ThinkingEffort.legacyThinkingEnabled("high")))
    }

    @Test fun legacyStoredEffortRemainsVisibleWithoutNewKeys() = runTest {
        val preferences = androidx.datastore.preferences.core.preferencesOf(
            stringPreferencesKey("thinking_effort") to "high"
        )
        val repository = SettingsRepository(MemoryStore(preferences), backgroundScope)
        val settings = repository.settingsFlow.first()
        assertTrue(settings.thinkingEnabled)
        assertEquals(ThinkingEffort.HIGH, settings.thinkingEffort)
    }

    @Test fun newMasterKeyOverridesLegacyOffAndGuidanceIsIndependent() = runTest {
        val preferences = androidx.datastore.preferences.core.preferencesOf(
            stringPreferencesKey("thinking_effort") to "off",
            booleanPreferencesKey("thinking_enabled") to true,
            booleanPreferencesKey("thinking_guidance_enabled") to true
        )
        val repository = SettingsRepository(MemoryStore(preferences), backgroundScope)
        val settings = repository.settingsFlow.first()
        assertTrue(settings.thinkingEnabled)
        assertEquals(ThinkingEffort.MEDIUM, settings.thinkingEffort)
        assertTrue(settings.thinkingGuidanceEnabled)
    }

    @Test fun allAnthropicBudgetsReserveOutputRoom() {
        ThinkingEffort.entries.forEach { effort ->
            assertEquals(4096 + effort.tokenBudget, effort.anthropicMaxTokens(4096))
        }
    }
}
