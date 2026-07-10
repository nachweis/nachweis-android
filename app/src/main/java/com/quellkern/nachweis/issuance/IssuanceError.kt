package com.quellkern.nachweis.issuance

/**
 * Typed failures for the issuance flow. Raw exceptions from wallet-core, the network, and
 * the Keystore are mapped onto this closed set so the UI reacts to a cause, not to an
 * exception class, and so no provider-supplied message (which may echo request detail)
 * reaches the user or the log. Mirrors the approach in
 * [com.quellkern.nachweis.wallet.WalletInitError].
 */
sealed interface IssuanceError {
    /** A message safe to show or log; never derived from credential material. */
    val publicMessage: String

    /** The offer could not be resolved (malformed URI, issuer unreachable, bad metadata). */
    data object OfferUnresolvable : IssuanceError {
        override val publicMessage = "This offer could not be read. Ask the issuer for a new QR code."
    }

    /** The issuer is not on this build's allowlist. */
    data object IssuerNotAllowed : IssuanceError {
        override val publicMessage = "This issuer is not one this app is configured to trust."
    }

    /** The issuer offers no SD-JWT PID this app can accept. */
    data object UnsupportedCredential : IssuanceError {
        override val publicMessage = "This issuer did not offer a credential this app can add yet."
    }

    /** The user cancelled or failed the device-authentication step during issuance. */
    data object UserAuthFailed : IssuanceError {
        override val publicMessage = "Issuance needs device authentication to protect the credential's key."
    }

    /** The network was unavailable while contacting the issuer. */
    data object Network : IssuanceError {
        override val publicMessage = "Couldn't reach the issuer. Check your connection and try again."
    }

    /** Anything not recognized above; cause retained for debugging, not display. */
    data class Unexpected(val cause: Throwable) : IssuanceError {
        override val publicMessage = "The credential could not be added."
    }

    companion object {
        /**
         * Map a thrown cause to a typed error by exception type and a small set of stable
         * keywords, never by echoing the message. Unknown causes fall through to [Unexpected].
         */
        fun fromThrowable(t: Throwable): IssuanceError {
            val text = (t.message ?: "").lowercase()
            return when {
                t is java.net.UnknownHostException || t is java.net.ConnectException -> Network
                t is UserAuthException -> UserAuthFailed
                "user not authenticated" in text || "authentication" in text -> UserAuthFailed
                else -> Unexpected(t)
            }
        }
    }
}
