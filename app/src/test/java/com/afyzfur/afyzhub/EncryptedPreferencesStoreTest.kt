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

    @Test fun unreadableCredentialsRemainStoredAcrossReadsAndOrdinaryEdits() = runTest {
        val profiles = stringPreferencesKey("api_profiles")
        val theme = stringPreferencesKey("color_mode")
        val raw = MemoryStore(preferencesOf(profiles to "enc:v1:unreadable", theme to "dark"))
        val failing = object : PreferenceCipher {
            override fun encrypt(value: String) = error("must not encrypt during an unrelated edit")
            override fun decrypt(value: String): String = error("key lost")
        }
        val store = EncryptedPreferencesStore(raw, failing)
        assertNull(store.data.first()[profiles])
        assertTrue(store.credentialsUnavailable.value)
        assertEquals("enc:v1:unreadable", raw.data.value[profiles])
        store.edit { it[theme] = "light" }
        assertEquals("light", store.data.first()[theme])
        assertEquals("enc:v1:unreadable", raw.data.value[profiles])
        try {
            store.edit { it[profiles] = "replacement" }
            fail("must not overwrite unavailable credentials")
        } catch (_: com.afyzfur.afyzhub.data.settings.CredentialsUnavailableException) {
            assertEquals("enc:v1:unreadable", raw.data.value[profiles])
        }
    }

    @Test fun confirmedRecoveryClearsOnlySensitivePreferencesAndAllowsNewCredentials() = runTest {
        val profiles = stringPreferencesKey("api_profiles")
        val tavily = stringPreferencesKey("tavily_api_key")
        val theme = stringPreferencesKey("color_mode")
        val raw = MemoryStore(preferencesOf(profiles to "enc:v1:lost", tavily to "enc:v1:lost", theme to "dark"))
        var lost = true
        val recoverable = object : PreferenceCipher {
            override fun encrypt(value: String) = "enc:v1:" + value.reversed()
            override fun decrypt(value: String): String {
                if (lost) error("key missing")
                return value.removePrefix("enc:v1:").reversed()
            }
            override fun reset() { lost = false }
        }
        val store = EncryptedPreferencesStore(raw, recoverable)
        store.data.first()
        assertTrue(store.credentialsUnavailable.value)
        store.resetCredentialsAfterConfirmation()
        assertFalse(store.credentialsUnavailable.value)
        assertNull(raw.data.value[profiles])
        assertNull(raw.data.value[tavily])
        assertEquals("dark", raw.data.value[theme])
        store.edit { it[tavily] = "fresh-secret" }
        assertEquals("fresh-secret", store.data.first()[tavily])
        assertNotEquals("fresh-secret", raw.data.value[tavily])
    }

    @Test fun resetFailurePreservesStoredData() = runTest {
        val key = stringPreferencesKey("api_key_openai")
        val raw = MemoryStore(preferencesOf(key to "enc:v1:lost"))
        val broken = object : PreferenceCipher {
            override fun encrypt(value: String) = error("unavailable")
            override fun decrypt(value: String) = error("unavailable")
            override fun reset() { error("unavailable") }
        }
        try { EncryptedPreferencesStore(raw, broken).resetCredentialsAfterConfirmation(); fail("must fail") }
        catch (_: IllegalStateException) { assertEquals("enc:v1:lost", raw.data.value[key]) }
    }

    @Test fun cancellationIsNotConvertedIntoMissingCredentials() = runTest {
        val key = stringPreferencesKey("api_key_openai")
        val raw = MemoryStore(preferencesOf(key to "enc:v1:value"))
        val cancelled = object : PreferenceCipher {
            override fun encrypt(value: String) = value
            override fun decrypt(value: String): String = throw kotlinx.coroutines.CancellationException()
        }
        try { EncryptedPreferencesStore(raw, cancelled).data.first(); fail("must cancel") }
        catch (_: kotlinx.coroutines.CancellationException) { assertEquals("enc:v1:value", raw.data.value[key]) }
    }
}