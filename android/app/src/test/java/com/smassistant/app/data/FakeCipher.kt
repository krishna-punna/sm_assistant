package com.smassistant.app.data

/** Reversible stand-in for [KeystoreCipher]; Robolectric has no AndroidKeyStore provider. */
class FakeCipher : SecretCipher {
    override fun encrypt(plain: String) = "enc:" + plain.reversed()
    override fun decrypt(encoded: String) = encoded.removePrefix("enc:").takeIf { encoded.startsWith("enc:") }?.reversed()
}
