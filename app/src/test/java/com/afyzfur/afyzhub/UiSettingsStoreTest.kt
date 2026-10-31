package com.afyzfur.afyzhub

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.afyzfur.afyzhub.data.settings.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class UiSettingsStoreTest {
    private class MemoryStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val next = transform(data.value)
            data.value = next
            return next
        }
    }

    @Test fun facadeKeepsStoredIdsAndAtomicImageVersion() = runTest {
        val raw = MemoryStore()
        val repository = SettingsRepository(raw, backgroundScope)
        repository.setColorMode(ColorMode.DARK)
        repository.setChatAnimation(ChatAnimation.SLIDE)
        repository.setBackgroundDim(4f)
        repository.setQuickPrompts(listOf("  one  ", "", "two"))
        repository.setUserAvatarPath("/images/avatar.webp")
        repository.setBackgroundPath("/images/background.webp")
        val prefs = repository.uiPreferencesFlow.first()
        assertEquals(ColorMode.DARK, prefs.colorMode)
        assertEquals(ChatAnimation.SLIDE, prefs.chatAppearance.animation)
        assertEquals(1f, prefs.chatAppearance.backgroundDim)
        assertEquals(listOf("one", "two"), prefs.quickPrompts)
        assertEquals(2L, prefs.chatAppearance.imageVersion)
        assertEquals("slide", raw.data.value[stringPreferencesKey("chat_animation")])
        repository.setQuickPrompts(emptyList())
        assertTrue(repository.uiPreferencesFlow.first().quickPrompts.isEmpty())
    }
}
