package com.quellkern.nachweis.issuance

/**
 * Decides whether a [ResolvedOffer] may proceed to issuance. Two gates, in order:
 *
 *  1. **Allowlist** — the offer's issuer must be a configured, permitted issuer.
 *  2. **Supported credential** — this slice issues exactly the unpatched SD-JWT PID
 *     (`vct = urn:eudi:pid:de:1`); an offer without it is declined rather than silently
 *     falling back to another format. The vct is left unpatched on purpose: augenmass's
 *     DCQL matches `urn:eudi:pid:de:1` exactly, so rewriting it to `urn:eudi:pid:1` would
 *     fix the app's label but break the presentation match (see dev-plan.md B4).
 *
 * Pure function; no SDK, no I/O. The result carries the chosen credential so the caller
 * requests precisely it, never the whole offer.
 */
object OfferEvaluation {

    /** The one credential type this slice issues. */
    const val EXPECTED_VCT: String = "urn:eudi:pid:de:1"

    fun evaluate(offer: ResolvedOffer, allowlist: IssuerAllowlist): OfferDecision {
        if (!allowlist.isAllowed(offer.issuerIdentifier)) {
            return OfferDecision.NotAllowlisted(offer.issuerIdentifier)
        }
        val pid = offer.offeredCredentials.firstOrNull { it.vct == EXPECTED_VCT }
            ?: return OfferDecision.UnsupportedCredential(
                offeredVcts = offer.offeredCredentials.mapNotNull { it.vct },
            )
        return OfferDecision.Acceptable(offer, pid)
    }
}

/** Outcome of [OfferEvaluation.evaluate]. */
sealed interface OfferDecision {
    /** The offer is permitted and contains the expected SD-JWT PID. */
    data class Acceptable(val offer: ResolvedOffer, val credential: OfferedCredential) : OfferDecision

    /** The issuer is not on this build's allowlist; nothing was requested. */
    data class NotAllowlisted(val issuerIdentifier: String) : OfferDecision

    /** The issuer is permitted but offers no SD-JWT PID `urn:eudi:pid:de:1`. */
    data class UnsupportedCredential(val offeredVcts: List<String>) : OfferDecision
}
