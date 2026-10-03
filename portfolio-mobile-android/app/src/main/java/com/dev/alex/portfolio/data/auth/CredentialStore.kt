package com.dev.alex.portfolio.data.auth

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class StoredCredentials(
    val serverUrl: String,
    val username: String,
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

/** The fingerprint key is gone (a new fingerprint was enrolled) — the password must be typed once more. */
class CredentialsInvalidatedException : Exception(
    "Fingerprints changed on this phone, so the saved sign-in was cleared. Sign in with your password.",
)

/**
 * The password, AES-GCM encrypted with an Android Keystore key that only a strong
 * biometric can unlock — for every single use, never "for N seconds after unlock".
 *
 * The key is created with `setInvalidatedByBiometricEnrollment(true)`: enrolling a new
 * fingerprint destroys it, so someone who adds their own finger to the phone cannot open
 * the saved sign-in. The ciphertext is useless without the key, which never leaves the
 * keystore (and the hardware, where the phone has a StrongBox/TEE).
 */
class CredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("credentials", Context.MODE_PRIVATE)

    fun stored(): StoredCredentials? {
        val server = prefs.getString(KEY_SERVER, null) ?: return null
        val user = prefs.getString(KEY_USER, null) ?: return null
        val iv = prefs.getString(KEY_IV, null) ?: return null
        val data = prefs.getString(KEY_DATA, null) ?: return null
        return runCatching {
            StoredCredentials(server, user, Base64.decode(iv, Base64.NO_WRAP), Base64.decode(data, Base64.NO_WRAP))
        }.getOrNull()
    }

    /** A cipher ready to encrypt — hand it to BiometricPrompt, then to [save]. */
    fun encryptionCipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key())
        } catch (e: KeyPermanentlyInvalidatedException) {
            // stale key from an earlier enrollment: start over with a fresh one
            deleteKey()
            cipher.init(Cipher.ENCRYPT_MODE, key())
        }
        return cipher
    }

    /** A cipher ready to decrypt [stored] — hand it to BiometricPrompt, then to [decrypt]. */
    fun decryptionCipher(stored: StoredCredentials): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, stored.iv))
        } catch (e: KeyPermanentlyInvalidatedException) {
            clear()
            throw CredentialsInvalidatedException()
        }
        return cipher
    }

    /** [cipher] must be the one BiometricPrompt just authenticated. */
    fun save(cipher: Cipher, serverUrl: String, username: String, password: String) {
        val ciphertext = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_SERVER, serverUrl)
            .putString(KEY_USER, username)
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_DATA, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
    }

    /** [cipher] must be the one BiometricPrompt just authenticated. */
    fun decrypt(cipher: Cipher, stored: StoredCredentials): String =
        String(cipher.doFinal(stored.ciphertext), Charsets.UTF_8)

    fun clear() {
        prefs.edit().clear().apply()
        deleteKey()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                // 0 s = every use needs its own biometric check; strong biometrics only.
                // Before API 30 the default validity (-1) already means "every use".
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(spec)
        return generator.generateKey()
    }

    private fun deleteKey() {
        runCatching {
            KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "portfolio_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val KEY_SERVER = "server"
        const val KEY_USER = "user"
        const val KEY_IV = "iv"
        const val KEY_DATA = "data"
    }
}
