package com.quellkern.nachweis

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Device-auth gate in front of *viewing* a stored credential's claims. Unlike the issuance and
 * presentation authenticators it unlocks no key and binds no crypto object — it is a UI gate — but
 * it reuses the same platform BiometricPrompt and the same BIOMETRIC_STRONG-or-DEVICE_CREDENTIAL
 * posture, so reading stored claim values needs a fresh authentication just as key use does.
 *
 * The current [FragmentActivity] is supplied through [activityProvider]; with no foreground activity
 * the gate stays closed rather than opening unauthenticated. Returns false on cancel or error rather
 * than throwing, since the caller's only response is to return to the list.
 *
 * Exercised on-device only (BiometricPrompt requires a running activity and secure lock).
 */
class BiometricDeviceAuthGate(
    private val activityProvider: () -> FragmentActivity?,
) {

    suspend fun authenticate(): Boolean = withContext(Dispatchers.Main) {
        val activity = activityProvider() ?: return@withContext false
        suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resume(false)
                    }

                    override fun onAuthenticationFailed() {
                        // A single non-match is not terminal; BiometricPrompt keeps prompting.
                    }
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Confirm it's you")
                .setSubtitle("Authenticate to view your credential")
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .build()
            prompt.authenticate(info)
        }
    }
}
