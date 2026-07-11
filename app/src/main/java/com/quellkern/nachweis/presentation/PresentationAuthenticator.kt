package com.quellkern.nachweis.presentation

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.quellkern.nachweis.issuance.UserAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.multipaz.securearea.AndroidKeystoreKeyUnlockData
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Unlocks the credential signing keys used to build an OpenID4VP presentation response.
 *
 * The PID's signing key is minted under the same per-use device-authentication posture as every
 * other key ([com.quellkern.nachweis.wallet.WalletSecurityPolicy]: user-auth required, no reuse
 * window). Signing the key-binding JWT during disclosure therefore needs a fresh authentication,
 * exactly as issuance does. Abstracted so [DefaultOid4vpGateway] does not depend on
 * BiometricPrompt directly and so the disclosure path can be reasoned about without a device; the
 * real implementation is [BiometricPresentationAuthenticator].
 */
fun interface PresentationAuthenticator {
    /**
     * Authenticate the user once per key in [unlockData], binding each unlock to its own crypto
     * object so the subsequent signing is authorized. A no-op for an empty list (no key required
     * authentication). Throws [UserAuthException] if the user cancels or authentication fails.
     */
    suspend fun authenticate(unlockData: List<AndroidKeystoreKeyUnlockData>)
}

/**
 * Unlocks presentation signing keys with the platform BiometricPrompt (biometric or device
 * credential), mirroring [com.quellkern.nachweis.issuance.BiometricUserAuthenticator]. Each key's
 * crypto object is authenticated in turn, so a single successful authentication authorizes exactly
 * the signing operation disclosure needs.
 *
 * The current [FragmentActivity] is supplied through [activityProvider]; when no foreground
 * activity is available the unlock fails rather than proceeding unauthenticated.
 *
 * Exercised on-device only (BiometricPrompt requires a running activity and secure lock).
 */
class BiometricPresentationAuthenticator(
    private val activityProvider: () -> FragmentActivity?,
) : PresentationAuthenticator {

    override suspend fun authenticate(unlockData: List<AndroidKeystoreKeyUnlockData>) {
        if (unlockData.isEmpty()) return
        val activity = activityProvider()
            ?: throw UserAuthException("no foreground activity to authenticate against")
        for (data in unlockData) {
            authenticate(activity, data.getCryptoObjectForSigning())
        }
    }

    private suspend fun authenticate(
        activity: FragmentActivity,
        cryptoObject: BiometricPrompt.CryptoObject?,
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resumeWithException(UserAuthException("authentication error $errorCode"))
                    }

                    override fun onAuthenticationFailed() {
                        // A single non-match is not terminal; BiometricPrompt keeps prompting.
                    }
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Confirm it's you")
                .setSubtitle("Authenticate to share your credential")
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .build()
            if (cryptoObject != null) prompt.authenticate(info, cryptoObject) else prompt.authenticate(info)
        }
    }
}
