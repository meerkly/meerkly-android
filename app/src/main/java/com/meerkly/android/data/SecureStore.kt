package com.meerkly.android.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.meerkly.android.logging.AppLogger
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small encrypted key/value store for secrets (OAuth state, device token) — the
 * Android counterpart of the desktop's Electron safeStorage persistence.
 * Values are AES-256/GCM encrypted with a key that lives in AndroidKeyStore
 * (non-exportable), and the `base64(iv):base64(ciphertext)` blob sits in the
 * app's SharedPreferences. If the Keystore is unusable (rare OEM breakage) the
 * store degrades to session memory — secrets are never written in plaintext.
 */
interface SecureStore {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)
}

/** Cipher seam so JVM/Robolectric tests can run without AndroidKeyStore. */
interface SecureStoreCrypto {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(blob: ByteArray): ByteArray
}

class KeystoreSecureStore(
    context: Context,
    private val logger: AppLogger,
    private val crypto: SecureStoreCrypto? = runCatching { KeystoreCrypto() }.getOrNull(),
) : SecureStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Fallback when the Keystore is broken: secrets survive the session only.
    private val memory = HashMap<String, String>()

    init {
        if (crypto == null) {
            logger.warn("securestore.no_keystore", mapOf("fallback" to "session memory"))
        }
    }

    @Synchronized
    override fun put(key: String, value: String) {
        val c = crypto
        if (c == null) {
            memory[key] = value
            return
        }
        runCatching {
            val blob = Base64.encodeToString(c.encrypt(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            prefs.edit().putString(prefKey(key), blob).apply()
        }.onFailure {
            logger.warn("securestore.encrypt_failed", mapOf("key" to key, "error" to it.message))
            memory[key] = value
        }
    }

    @Synchronized
    override fun get(key: String): String? {
        memory[key]?.let { return it }
        val c = crypto ?: return null
        val blob = prefs.getString(prefKey(key), null) ?: return null
        return runCatching {
            String(c.decrypt(Base64.decode(blob, Base64.NO_WRAP)), Charsets.UTF_8)
        }.onFailure {
            // Undecryptable (key rotated/wiped by the OS): treat as absent.
            logger.warn("securestore.decrypt_failed", mapOf("key" to key, "error" to it.message))
        }.getOrNull()
    }

    @Synchronized
    override fun remove(key: String) {
        memory.remove(key)
        prefs.edit().remove(prefKey(key)).apply()
    }

    private fun prefKey(key: String) = "secure_$key"

    companion object {
        private const val PREFS = "meerkly_prefs"
    }
}

/** AES-256/GCM with a non-exportable AndroidKeyStore key. Blob = iv || ciphertext. */
class KeystoreCrypto : SecureStoreCrypto {
    private val key: SecretKey = loadOrCreateKey()

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > IV_BYTES) { "blob too short" }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
        return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }

    private fun loadOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "meerkly_secure_v1"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
