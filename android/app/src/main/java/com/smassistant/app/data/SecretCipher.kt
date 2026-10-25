package com.smassistant.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts small secrets (API keys, tokens) before they are written to storage. */
interface SecretCipher {
    fun encrypt(plain: String): String

    /** Returns null if [encoded] can't be decrypted, e.g. the device key was reset. */
    fun decrypt(encoded: String): String?
}

/**
 * AES-256-GCM with a key that never leaves the Android Keystore. Output is
 * Base64(IV + ciphertext). The key is created on first use and isn't backed up,
 * so a restored or transferred install can't read old values (the user re-enters them).
 */
class KeystoreCipher(private val alias: String = "sm_assistant_secrets") : SecretCipher {
    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()))
    }

    override fun decrypt(encoded: String): String? = try {
        val bytes = Base64.getDecoder().decode(encoded)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        }
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
    } catch (e: GeneralSecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
