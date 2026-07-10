package com.quellkern.nachweis.issuance

import org.multipaz.crypto.Algorithm
import org.multipaz.securearea.KeyUnlockData
import org.multipaz.securearea.SecureArea

/**
 * Unlocks credential keys that require device authentication during issuance. Abstracted so
 * the gateway does not depend on BiometricPrompt directly and so the flow can be reasoned
 * about without a device; the real implementation is
 * [com.quellkern.nachweis.issuance.BiometricUserAuthenticator].
 */
interface UserAuthenticator {
    /**
     * Authenticate the user and return unlock data for each key in [keysRequireAuth].
     * Throws [UserAuthException] if the user cancels or authentication fails.
     */
    suspend fun unlock(
        keysRequireAuth: Map<String, SecureArea>,
        algorithm: Algorithm,
    ): Map<String, KeyUnlockData>
}

/** Raised when device authentication is cancelled or cannot be satisfied. */
class UserAuthException(message: String) : Exception(message)
