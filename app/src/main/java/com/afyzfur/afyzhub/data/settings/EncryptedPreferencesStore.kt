package com.afyzfur.afyzhub.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

internal interface PreferenceCipher {
    fun encrypt(value: String): String
    fun decrypt(value: String): String
    fun reset() = Unit
}

internal class CredentialsUnavailableException : IllegalStateException("Credentials unavailable; reset requires confirmation")

/** Reads never discard ciphertext; unrelated edits preserve unavailable credentials verbatim. */
internal class EncryptedPreferencesStore(
    private val delegate: DataStore<Preferences>,
    private val cipher: PreferenceCipher
) : DataStore<Preferences> {
    private val mutableUnavailable = MutableStateFlow(false)
    val credentialsUnavailable: StateFlow<Boolean> = mutableUnavailable.asStateFlow()

    private fun sensitive(name: String) = name == "api_profiles" || name == "tavily_api_key" ||
        name == "openai_api_key" || name.startsWith("api_key_")

    private data class Decoded(val preferences: Preferences, val failed: Boolean)

    private fun decoded(prefs: Preferences): Decoded {
        var failed = false
        val plain = prefs.toMutablePreferences()
        prefs.asMap().forEach { (key, value) ->
            if (value is String && sensitive(key.name)) {
                try {
                    plain[stringPreferencesKey(key.name)] = cipher.decrypt(value)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    plain.remove(stringPreferencesKey(key.name))
                    failed = true
                }
            }
        }
        mutableUnavailable.value = failed
        return Decoded(plain, failed)
    }

    override val data: Flow<Preferences> = delegate.data.map { decoded(it).preferences }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val written = delegate.updateData { stored ->
            val before = decoded(stored)
            val plain = transform(before.preferences)
            plain.toMutablePreferences().apply {
                if (before.failed) {
                    val oldSensitive = before.preferences.asMap().filterKeys { sensitive(it.name) }
                    val newSensitive = plain.asMap().filterKeys { sensitive(it.name) }
                    if (oldSensitive != newSensitive) throw CredentialsUnavailableException()
                    asMap().keys.filter { sensitive(it.name) }.toList().forEach {
                        remove(stringPreferencesKey(it.name))
                    }
                    stored.asMap().forEach { (key, value) ->
                        if (value is String && sensitive(key.name)) this[stringPreferencesKey(key.name)] = value
                    }
                } else {
                    plain.asMap().forEach { (key, value) ->
                        if (value is String && sensitive(key.name)) {
                            val old = stored[stringPreferencesKey(key.name)]
                            this[stringPreferencesKey(key.name)] =
                                if (old?.startsWith("enc:v1:") == true && cipher.decrypt(old) == value) old
                                else cipher.encrypt(value)
                        }
                    }
                }
            }
        }
        return decoded(written).preferences
    }

    /** Destructive recovery is exposed only behind a UI confirmation. */
    suspend fun resetCredentialsAfterConfirmation() {
        delegate.updateData { stored ->
            val cleared = stored.toMutablePreferences()
            stored.asMap().keys.filter { sensitive(it.name) }.forEach {
                cleared.remove(stringPreferencesKey(it.name))
            }
            cipher.reset()
            cleared
        }
        mutableUnavailable.value = false
    }
}
