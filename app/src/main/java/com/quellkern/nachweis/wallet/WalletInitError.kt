package com.quellkern.nachweis.wallet

/**
 * Typed failures for wallet initialization. Raw exceptions from wallet-core, the Keystore,
 * and storage are mapped onto this closed set so the UI can react (and tests can assert)
 * without matching on exception classes or, worse, on human-readable messages that might
 * carry sensitive detail.
 */
sealed interface WalletInitError {
    /** A message safe to show or log: never derived from credential material. */
    val publicMessage: String

    /** The device cannot honor Keystore-enforced user authentication (no secure lock). */
    data object UserAuthUnavailable : WalletInitError {
        override val publicMessage = "Device authentication (PIN, pattern, or biometrics) is required to use the wallet."
    }

    /** StrongBox was requested but is unsupported; caller may retry with it disabled. */
    data object StrongBoxUnavailable : WalletInitError {
        override val publicMessage = "Hardware-backed key storage is unavailable on this device."
    }

    /** The wallet storage directory could not be prepared. */
    data object StorageUnavailable : WalletInitError {
        override val publicMessage = "Wallet storage could not be initialized."
    }

    /** Anything not recognized above. The cause is retained for debugging, not display. */
    data class Unexpected(val cause: Throwable) : WalletInitError {
        override val publicMessage = "The wallet could not be initialized."
    }

    companion object {
        /**
         * Map a thrown exception to a typed error. Matching is by exception type and a
         * small set of stable capability keywords, never by echoing the message to the
         * user. Unknown causes fall through to [Unexpected].
         */
        fun fromThrowable(t: Throwable): WalletInitError {
            val text = (t.message ?: "").lowercase()
            return when {
                t is IllegalStateException && "strongbox" in text -> StrongBoxUnavailable
                t is IllegalStateException && ("user auth" in text || "authentication" in text || "secure lock" in text) -> UserAuthUnavailable
                t is java.io.IOException -> StorageUnavailable
                else -> Unexpected(t)
            }
        }
    }
}
