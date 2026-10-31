package com.afyzfur.afyzhub

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.afyzfur.afyzhub.data.settings.EncryptedPreferencesStore
import com.afyzfur.afyzhub.data.settings.PreferenceCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class EncryptedPreferencesStoreTest {
    private class MemoryStore(initial: Preferences) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val next = transform(data.value); data.value = next; return next
        }
    }
    private val cipher = object : PreferenceCipher {
        override fun encrypt(value: String) = "enc:v1:" + value.reversed()
        override fun decrypt(value: String) = if (value.startsWith("enc:v1:")) value.removePrefix("enc:v1:").reversed() else value
    }
    @Test fun legacyMigrationAndUpdatesNeverPersistPlainCredentials() = runTest {
        val profiles = stringPreferencesKey("api_profiles")
        val tavily = stringPreferencesKey("tavily_api_key")
        val model = stringPreferencesKey("model_openai")
        val raw = MemoryStore(preferencesOf(profiles to "{key:secret}", tavily to "secret-2", model to "gpt"))
        val store = EncryptedPreferencesStore(raw,cipher)
        assertEquals("{key:secret}",store.data.first()[profiles])
        store.updateData { it }
        assertTrue(raw.data.value[profiles].orEmpty().startsWith("enc:v1:"))
        assertTrue(raw.data.value[tavily].orEmpty().startsWith("enc:v1:"))
        assertEquals("gpt",raw.data.value[model])
        store.edit { it[tavily]="updated" }
        assertEquals("updated",store.data.first()[tavily])
        assertNotEquals("updated",raw.data.value[tavily])
        store.edit { it.remove(tavily) }
        assertNull(raw.data.value[tavily])
    }
    @Test fun encryptionFailureKeepsLegacyFileUnmodified() = runTest {
        val key=stringPreferencesKey("api_key_openai")
        val raw=MemoryStore(preferencesOf(key to "original"))
        val failing=object : PreferenceCipher {
            override fun encrypt(value: String): String = throw IllegalStateException("unavailable")
            override fun decrypt(value: String) = value
        }
        try { EncryptedPreferencesStore(raw,failing).updateData { it }; fail("must report failure") }
        catch (_: IllegalStateException) { assertEquals("original",raw.data.value[key]) }
    }
}