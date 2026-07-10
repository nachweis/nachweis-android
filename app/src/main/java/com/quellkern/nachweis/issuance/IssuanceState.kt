package com.quellkern.nachweis.issuance

/**
 * Lifecycle of a single issuance attempt, observed by the UI. The consent step
 * ([AwaitingConsent]) is explicit: the app resolves and evaluates an offer, then waits for
 * the user before requesting the credential — nothing is issued silently.
 */
sealed interface IssuanceState {
    /** No issuance in progress. */
    data object Idle : IssuanceState

    /** Resolving the offer URI with the issuer. */
    data object Resolving : IssuanceState

    /**
     * The offer resolved, passed the allowlist, and contains the expected credential.
     * Waiting for the user to confirm before issuance is requested.
     */
    data class AwaitingConsent(
        val issuerIdentifier: String,
        val credentialName: String,
        val vct: String,
        val requiresTransactionCode: Boolean,
    ) : IssuanceState

    /** Issuance requested; contacting the issuer and storing the credential. */
    data object Issuing : IssuanceState

    /** A credential key needs device authentication; the prompt is showing. */
    data object AwaitingUserAuth : IssuanceState

    /** A credential was issued and stored. */
    data class Issued(val documentId: String, val name: String) : IssuanceState

    /**
     * The offer was resolved but declined by policy (allowlist or unsupported credential).
     * Distinct from [Failed]: nothing went wrong; the offer is simply not one we accept.
     */
    data class Declined(val reason: IssuanceError) : IssuanceState

    /** Issuance failed with a typed, display-safe reason. */
    data class Failed(val error: IssuanceError) : IssuanceState

    /**
     * A presentation (OpenID4VP) deep link arrived. The scheme is owned, but the handling
     * lands with the B5 presentation slice; the UI states this plainly rather than failing.
     */
    data object PresentationNotYetSupported : IssuanceState
}
