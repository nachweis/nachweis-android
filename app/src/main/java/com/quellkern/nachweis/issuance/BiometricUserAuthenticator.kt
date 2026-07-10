package com.quellkern.nachweis.issuance

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.multipaz.crypto.Algorithm
import org.multipaz.securearea.AndroidKeystoreKeyUnlockData
import org.multipaz.securearea.AndroidKeystoreSecureArea
import org.multipaz.securearea.KeyUnlockData
import org.multipaz.securearea.SecureArea
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Unlocks credential keys with the platform BiometricPrompt (biometric or device
 * credential). Each key requiring authentication is bound to its own crypto object so a
 * single successful authentication authorizes exactly the signing operation issuance needs,
 * matching the Keystore posture set in B2 (per-use authentication, no reuse window).
 *
 * The current [FragmentActivity] is supplied through [activityProvider]; when no foreground
 * activity is available the unlock fails rather than proceeding unauthenticated.
 *
 * Exercised on-device only (BiometricPrompt requires a running activity and secure lock);
 * the issuance controller it feeds is unit-tested through a fake [UserAuthenticator].
 */
class BiometricUserAuthenticator(
    private val activityProvider: () -> FragmentActivity?,
) : UserAuthenticator {

    override suspend fun unlock(
        keysRequireAuth: Map<String, SecureArea>,
        algorithm: Algorithm,
    ): Map<String, KeyUnlockData> {
        val activity = activityProvider()
            ?: throw UserAuthException("no foreground activity to authenticate against")

        val unlocked = LinkedHashMap<String, KeyUnlockData>(keysRequireAuth.size)
        for ((alias, secureArea) in keysRequireAuth) {
            val keystoreArea = secureArea as? AndroidKeystoreSecureArea
                ?: throw UserAuthException("unsupported secure area for key $alias")
            val unlockData = AndroidKeystoreKeyUnlockData(keystoreArea, alias)
            val cryptoObject = unlockData.getCryptoObjectForSigning()
            authenticate(activity, cryptoObject)
            unlocked[alias] = unlockData
        }
        return unlocked
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
                .setSubtitle("Authenticate to add this credential")
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .build()
            if (cryptoObject != null) prompt.authenticate(info, cryptoObject) else prompt.authenticate(info)
        }
    }
}
