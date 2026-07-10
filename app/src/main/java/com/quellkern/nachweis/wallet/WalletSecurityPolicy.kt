package com.quellkern.nachweis.wallet

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Security posture applied to every credential key and to wallet logging. These are
 * not UI decisions: [userAuthenticationRequired] becomes an Android Keystore key
 * property, so a locked or lock-less device cannot silently fall back to unauthenticated
 * key use.
 *
 * The [secure] default is the only posture the app ships. It is expressed as an explicit
 * value (rather than relying on wallet-core defaults) so the guarantee is visible, tested,
 * and cannot regress unnoticed.
 */
data class WalletSecurityPolicy(
    /** Require a Keystore-enforced user authentication for each credential key use. */
    val userAuthenticationRequired: Boolean,
    /**
     * Validity window after a successful authentication during which the key may be used
     * again without re-authenticating. [Duration.ZERO] means authentication is required
     * for every single use (strongest posture, no reuse window).
     */
    val userAuthenticationTimeout: Duration,
    /** Prefer a StrongBox-backed key when the device advertises the capability. */
    val useStrongBox: Boolean,
    /** wallet-core log level; see [com.quellkern.nachweis.wallet.SecureWalletLogger]. */
    val logLevel: Int,
) {
    companion object {
        /**
         * The shipping posture. Authentication is required for every key use (no reuse
         * window). StrongBox is preferred; wallet-core downgrades gracefully when the
         * device lacks it. Logging is silenced in release and limited to errors otherwise,
         * so no credential material can reach logcat regardless of build type.
         */
        fun secure(debuggable: Boolean): WalletSecurityPolicy = WalletSecurityPolicy(
            userAuthenticationRequired = true,
            userAuthenticationTimeout = Duration.ZERO,
            useStrongBox = true,
            logLevel = if (debuggable) SecureWalletLogger.LEVEL_ERROR else SecureWalletLogger.OFF,
        )

        /** Convenience for callers that think in seconds. */
        fun reuseWindow(seconds: Long): Duration = seconds.seconds
    }
}
