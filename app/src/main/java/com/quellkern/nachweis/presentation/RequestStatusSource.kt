package com.quellkern.nachweis.presentation

import java.security.cert.X509Certificate

/**
 * The revocation status of a verifier's access certificate (WRPAC), resolved from **locally
 * cached** status material only. Per dev-plan.md, the signed status list is published at a
 * stable public endpoint and refreshed independently of presentations; during consent the
 * app reads the cache, never the verifier or the registrar.
 *
 * Absence is not "good": [Unknown] is a distinct outcome the validator treats as fail-closed.
 */
fun interface RequestStatusSource {
    /** Resolve the cached status of [certificate], or [CertStatus.Unknown] if not covered. */
    fun statusOf(certificate: X509Certificate): CertStatus
}

/** Cached revocation status of a certificate. */
enum class CertStatus {
    /** The status list covers this certificate and marks it valid. */
    Good,

    /** The status list covers this certificate and marks it revoked. */
    Revoked,

    /**
     * No cached status covers this certificate. The validator fails closed on this — an
     * unknown status is never treated as good (dev-plan.md D1).
     */
    Unknown,
}

/**
 * A status source backed by an in-memory snapshot of the cached status list, keyed by the
 * certificate's issuer + serial. The real refresh path (fetching and verifying the signed
 * `token-status-list` published by Workstream A) populates this snapshot out of band; until
 * that endpoint is deployed the snapshot is empty and every lookup is [CertStatus.Unknown],
 * which fails closed. This keeps the validator's status check pure and unit-testable.
 */
class CachedStatusSource(
    private val good: Set<String> = emptySet(),
    private val revoked: Set<String> = emptySet(),
) : RequestStatusSource {

    override fun statusOf(certificate: X509Certificate): CertStatus {
        val key = keyOf(certificate)
        return when {
            key in revoked -> CertStatus.Revoked
            key in good -> CertStatus.Good
            else -> CertStatus.Unknown
        }
    }

    companion object {
        /** Stable identity for a certificate in the status list: issuer DN + serial (hex). */
        fun keyOf(certificate: X509Certificate): String =
            certificate.issuerX500Principal.name + "#" + certificate.serialNumber.toString(16)
    }
}
