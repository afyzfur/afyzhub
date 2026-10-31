package com.afyzfur.afyzhub.data.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.toMutablePreferences
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal interface PreferenceCipher {
    fun encrypt(value: String): String
    fun decrypt(value: String): String
}

/** AES-GCM key never leaves Android Keystore. No plaintext/key/exception messages in diagnostics. */
internal class KeystorePreferenceCipher : PreferenceCipher {
    private val prefix = "enc:v1:"
    private val key: SecretKey by lazy {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("afyzhub.preferences.v1", null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(KeyGenParameterSpec.Builder("afyzhub.preferences.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
    }
    override fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encoded = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return prefix + Base64.encodeToString(encoded, Base64.NO_WRAP)
    }
    override fun decrypt(value: String): String {
        if (!value.startsWith(prefix)) return value // legacy plaintext is migrated on next update
        val data = Base64.decode(value.removePrefix(prefix), Base64.NO_WRAP)
        require(data.size >= 28) { "Invalid encrypted preference" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data.copyOfRange(0, 12)))
        return cipher.doFinal(data.copyOfRange(12, data.size)).toString(Charsets.UTF_8)
    }
}

/** Encrypt sensitive values at the DataStore boundary, including profiles' embedded credentials. */
internal class EncryptedPreferencesStore(
    private val delegate: DataStore<Preferences>,
    private val cipher: PreferenceCipher
) : DataStore<Preferences> {
    private fun sensitive(name: String) = name == "api_profiles" || name == "tavily_api_key" ||
        name == "openai_api_key" || name.startsWith("api_key_")

    private fun decoded(prefs: Preferences): Preferences = prefs.toMutablePreferences().apply {
        prefs.asMap().forEach { (key, value) ->
            if (value is String && sensitive(key.name)) this[stringPreferencesKey(key.name)] = cipher.decrypt(value)
        }
    }
    override val data: Flow<Preferences> = delegate.data.map(::decoded)

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val written = delegate.updateData { stored ->
            val plain = transform(decoded(stored))
            plain.toMutablePreferences().apply {
                plain.asMap().forEach { (key, value) ->
                    if (value is String && sensitive(key.name)) {
                        // Reuse ciphertext when unchanged; do not rewrite IVs on ordinary UI edits.
                        val old = stored[stringPreferencesKey(key.name)]
                        this[stringPreferencesKey(key.name)] = if (old?.startsWith("enc:v1:") == true && cipher.decrypt(old) == value) old else cipher.encrypt(value)
                    }
                }
            }
        }
        return decoded(written)
    }
}