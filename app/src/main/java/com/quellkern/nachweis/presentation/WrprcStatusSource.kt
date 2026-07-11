package com.quellkern.nachweis.presentation

/**
 * The revocation status of a WRPRC (Wallet-Relying-Party Registration Certificate), resolved
 * from a **locally cached** signed status list only. A WRPRC carries its own status pointer in
 * its payload (`status.status_list = { uri, idx }`, ETSI TS 119 475), which is a different
 * mechanism from the X.509 WRPAC status handled by [RequestStatusSource]: a status-list URI
 * plus an index, not an issuer+serial. The two are kept separate so a revoked WRPRC and a
 * revoked WRPAC fail independently and are tested independently.
 *
 * Per dev-plan.md D1 the signed status list is published at a stable public endpoint and
 * refreshed out of band; during consent the app reads this cache, never the verifier or the
 * registrar. Absence is [CertStatus.Unknown] and fails closed — never treated as good.
 */
fun interface WrprcStatusSource {
    /** Resolve the cached status of the status-list entry [ref], or [CertStatus.Unknown]. */
    fun statusOf(ref: WrprcStatusRef): CertStatus
}

/** A WRPRC's status-list pointer: the list [uri] and the [idx] of this certificate within it. */
data class WrprcStatusRef(val uri: String, val idx: Int)

/**
 * A [WrprcStatusSource] backed by an in-memory snapshot of the cached, verified status list,
 * keyed by `uri#idx`. The real refresh path (fetching and verifying the signed
 * `token-status-list` published by Workstream A) populates the snapshot out of band; until that
 * endpoint is deployed the snapshot is empty and every lookup is [CertStatus.Unknown], which
 * fails closed. This keeps the validator's status check pure and unit-testable.
 */
class CachedWrprcStatusSource(
    private val good: Set<String> = emptySet(),
    private val revoked: Set<String> = emptySet(),
) : WrprcStatusSource {

    override fun statusOf(ref: WrprcStatusRef): CertStatus {
        val key = keyOf(ref)
        return when {
            key in revoked -> CertStatus.Revoked
            key in good -> CertStatus.Good
            else -> CertStatus.Unknown
        }
    }

    companion object {
        /** Stable identity for a status-list entry: `uri#idx`. */
        fun keyOf(ref: WrprcStatusRef): String = ref.uri + "#" + ref.idx
    }
}
