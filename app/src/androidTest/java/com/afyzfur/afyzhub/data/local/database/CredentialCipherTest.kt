package com.afyzfur.afyzhub.data.local.database

import com.afyzfur.afyzhub.data.settings.KeystorePreferenceCipher
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialCipherTest {
    @Test fun keystoreRoundTripRandomIvAndTamperRejection() {
        val cipher=KeystorePreferenceCipher()
        val value="secret\n😀\\\""
        val first=cipher.encrypt(value)
        val second=cipher.encrypt(value)
        assertNotEquals(first,second)
        assertFalse(first.contains("secret"))
        assertEquals(value,cipher.decrypt(first))
        assertEquals(value,KeystorePreferenceCipher().decrypt(first))
        val offset=first.length-5
        val changed=first.substring(0,offset)+(if (first[offset]=='A') "B" else "A")+first.substring(offset+1)
        try { cipher.decrypt(changed); fail("Authenticated ciphertext must reject changes") }
        catch (_: java.security.GeneralSecurityException) { }
        assertEquals("legacy",cipher.decrypt("legacy"))
    }

    @Test fun missingKeystoreAliasDoesNotGetRecreatedOnDecrypt() {
        val alias = "afyzhub.test." + java.util.UUID.randomUUID().toString()
        val cipher = KeystorePreferenceCipher(alias)
        try {
            val encrypted = cipher.encrypt("secret")
            cipher.reset()
            try { cipher.decrypt(encrypted); fail("lost key must fail") }
            catch (_: IllegalStateException) { }
            val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            assertFalse(store.containsAlias(alias))
            val fresh = cipher.encrypt("new-secret")
            assertEquals("new-secret", cipher.decrypt(fresh))
            try { cipher.decrypt("enc:v1:AA=="); fail("truncated data must fail") }
            catch (_: IllegalArgumentException) { }
        } finally { cipher.reset() }
    }
}