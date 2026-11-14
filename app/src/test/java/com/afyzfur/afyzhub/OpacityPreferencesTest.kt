package com.afyzfur.afyzhub

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.afyzfur.afyzhub.data.settings.UiSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OpacityPreferencesTest {
    private class Store : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
    @Test fun independentOpacityValuesSurviveStoreRecreation() = runTest {
        val disk = Store()
        val store = UiSettingsStore(disk, backgroundScope)
        val clampedStore = UiSettingsStore(disk, backgroundScope)
        assertEquals(1f, store.uiPreferencesFlow.first().chatAppearance.infoOpacity)
        store.setSearchOpacity(0.2f)
        store.setThinkingOpacity(0.4f)
        store.setBodyOpacity(0.6f)
        store.setInfoOpacity(0.8f)
        val restored = UiSettingsStore(disk, backgroundScope).uiPreferencesFlow.first().chatAppearance
        assertEquals(0.2f, restored.searchOpacity)
        assertEquals(0.4f, restored.thinkingOpacity)
        assertEquals(0.6f, restored.bodyOpacity)
        assertEquals(0.8f, restored.infoOpacity)
        clampedStore.setInfoOpacity(2f)
        val clamped = clampedStore.uiPreferencesFlow.first().chatAppearance
        assertEquals(1f, clamped.infoOpacity)
        assertEquals(0.2f, clamped.searchOpacity)
    }
}
