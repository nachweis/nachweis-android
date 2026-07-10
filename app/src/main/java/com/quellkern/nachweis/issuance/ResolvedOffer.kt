package com.quellkern.nachweis.issuance

/**
 * A credential offer after resolution, reduced to the fields this slice reasons about.
 * Decoupled from wallet-core's `Offer` so the evaluation and the controller state machine
 * are pure Kotlin, testable on the JVM without the SDK.
 */
data class ResolvedOffer(
    /** The credential-issuer identifier (an origin the allowlist is checked against). */
    val issuerIdentifier: String,
    /** The credentials the issuer offers in this session. */
    val offeredCredentials: List<OfferedCredential>,
    /** True when the issuer requires a transaction code (PIN) to complete issuance. */
    val requiresTransactionCode: Boolean,
)

/** One credential an offer makes available. */
data class OfferedCredential(
    /** Issuer-defined configuration identifier used to request this specific credential. */
    val configurationIdentifier: String,
    /** SD-JWT VC type, when the offered credential is an SD-JWT VC; null otherwise. */
    val vct: String?,
    /** ISO mdoc doctype, when the offered credential is an mdoc; null otherwise. */
    val docType: String?,
    /** Human-readable name from the issuer metadata, when present. */
    val displayName: String?,
)
