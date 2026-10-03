package com.dev.alex.portfolio.data.auth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The prompt ended without a match. [userChosePassword] = the "Use password" button. */
class BiometricCancelledException(
    val code: Int,
    message: String,
) : Exception(message) {
    val userChosePassword: Boolean get() = code == BiometricPrompt.ERROR_NEGATIVE_BUTTON
    val userDismissed: Boolean
        get() = code == BiometricPrompt.ERROR_USER_CANCELED || code == BiometricPrompt.ERROR_CANCELED
}

/**
 * Shows BiometricPrompt bound to a keystore cipher (CryptoObject), so the prompt's success
 * is what actually unlocks the key — not a boolean the app could be tricked into setting.
 * BiometricPrompt needs a FragmentActivity; MainActivity attaches itself here.
 */
class BiometricGate {
    private var activity: WeakReference<FragmentActivity>? = null

    fun attach(activity: FragmentActivity) {
        this.activity = WeakReference(activity)
    }

    fun detach(activity: FragmentActivity) {
        if (this.activity?.get() === activity) this.activity = null
    }

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    suspend fun authenticate(cipher: Cipher, title: String, subtitle: String): Cipher {
        val host = activity?.get() ?: throw IllegalStateException("No screen to show the fingerprint prompt on.")
        return suspendCancellableCoroutine { continuation ->
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val unlocked = result.cryptoObject?.cipher
                    if (!continuation.isActive) return
                    if (unlocked != null) {
                        continuation.resume(unlocked)
                    } else {
                        continuation.resumeWithException(IllegalStateException("The fingerprint prompt returned no key."))
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(BiometricCancelledException(errorCode, errString.toString()))
                    }
                }
                // onAuthenticationFailed = one finger that did not match; the prompt stays up.
            }
            val prompt = BiometricPrompt(host, ContextCompat.getMainExecutor(host), callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("Use password")
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .build()
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
            continuation.invokeOnCancellation { runCatching { prompt.cancelAuthentication() } }
        }
    }
}
