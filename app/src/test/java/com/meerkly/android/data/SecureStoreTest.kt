package com.meerkly.android.data

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** XOR "cipher" stand-in — AndroidKeyStore doesn't exist under Robolectric. */
private class FakeCrypto : SecureStoreCrypto {
    override fun encrypt(plaintext: ByteArray) = plaintext.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    override fun decrypt(blob: ByteArray) = blob.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecureStoreTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication() as Application

    private fun store(crypto: SecureStoreCrypto? = FakeCrypto()) = KeystoreSecureStore(
        context,
        NoopLogger(),
        crypto = crypto,
    )

    @Test
    fun `round-trips values through encryption and persists across instances`() {
        store().put("auth_state", """{"token":"secret"}""")

        // A fresh instance over the same prefs decrypts the persisted blob.
        assertEquals("""{"token":"secret"}""", store().get("auth_state"))
    }

    @Test
    fun `never persists plaintext`() {
        store().put("auth_state", "super-secret-value")
        val prefs = context.getSharedPreferences("meerkly_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("secure_auth_state", null)
        org.junit.Assert.assertNotNull(raw)
        org.junit.Assert.assertFalse(raw!!.contains("super-secret-value"))
    }

    @Test
    fun `remove deletes the value`() {
        val s = store()
        s.put("k", "v")
        s.remove("k")
        assertNull(s.get("k"))
    }

    @Test
    fun `degrades to session memory without a keystore`() {
        val s = store(crypto = null)
        s.put("k", "v")
        assertEquals("v", s.get("k"))
        // Nothing persisted: a new instance sees nothing.
        assertNull(store(crypto = null).get("k"))
    }
}
