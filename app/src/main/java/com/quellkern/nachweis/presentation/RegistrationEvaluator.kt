package com.quellkern.nachweis.presentation

import java.util.Date

/**
 * The D1 flagship (dev-plan.md Workstream D): given a request that already passed the
 * access/request layer (B5), extract the WRPRC from its `verifier_info`, verify it under the
 * supported profile ([WrprcValidator]), bind it to the WRPAC by WRP identifier ([WrpBinding]),
 * and diff what the request asks against what the WRPRC registers. The result either **rejects**
 * the request before consent (the WRPRC is missing, unverifiable, revoked, expired, or unbound —
 * the acceptance-matrix "fails" cases) or lets it **proceed to consent** carrying a
 * [RegistrationVerdict] of [RegistrationVerdict.InsideRegistration] or
 * [RegistrationVerdict.OutsideRegistration] with the offending claims.
 *
 * The verdict label for the outside case is fixed by dev-plan.md D1: *"outside this verifier's
 * registration"*, never "over-ask". Semantic necessity ("is claim X needed for purpose P?") is a
 * separate, later layer and is deliberately out of scope here.
 *
 * All inputs are local — the WRPRC comes from the request itself, trust and status from caches,
 * time from [clock] — so evaluation makes **zero network calls** and is JVM-unit-tested. The
 * [NotEvaluating] instance preserves B5's behaviour (always [RegistrationVerdict.NotEvaluated])
 * for callers that have not enabled the flagship.
 */
fun interface RegistrationEvaluator {
    /** Evaluate the registration standing of [request] as of the evaluator's own clock. */
    fun evaluate(request: ValidatedPresentationRequest): RegistrationOutcome

    companion object {
        /** A no-op evaluator: every request proceeds with [RegistrationVerdict.NotEvaluated]. */
        val NotEvaluating: RegistrationEvaluator = RegistrationEvaluator {
            RegistrationOutcome.Proceed(RegistrationVerdict.NotEvaluated)
        }
    }
}

/** The outcome of registration evaluation: reject before consent, or proceed with a verdict. */
sealed interface RegistrationOutcome {
    /** The request must be refused before any consent UI; [error] is a display-safe reason. */
    data class Reject(val error: PresentationError) : RegistrationOutcome

    /** The request may proceed to consent carrying [verdict]. */
    data class Proceed(val verdict: RegistrationVerdict) : RegistrationOutcome
}

/**
 * The default [RegistrationEvaluator]: runs WRPRC verification and the DCQL registration diff
 * against locally cached artifacts. [wrprcValidator] holds the WRPRC-provider trust and status
 * caches; [clock] is injected so tests pin time and the acceptance matrix is deterministic.
 */
class DefaultRegistrationEvaluator(
    private val wrprcValidator: WrprcValidator,
    private val clock: () -> Date = { Date() },
) : RegistrationEvaluator {

    override fun evaluate(request: ValidatedPresentationRequest): RegistrationOutcome {
        val now = clock()

        // (1) Extract the WRPRC from verifier_info. Missing → reject; CWT/CBOR → reject (profile).
        val wrprc = when (val extracted = WrprcExtractor.extract(request.verifierInfo)) {
            is VerifierInfoResult.JwtWrprc -> extracted.compactJws
            VerifierInfoResult.UnsupportedEncoding ->
                return RegistrationOutcome.Reject(PresentationError.UnsupportedRegistrationProfile)
            VerifierInfoResult.Absent ->
                return RegistrationOutcome.Reject(PresentationError.RegistrationMissing)
        }

        // (2) Layer (b): profile, signature, provider chain, temporal validity, status.
        val validated = when (val result = wrprcValidator.validate(wrprc, now)) {
            is WrprcValidation.Valid -> result.wrprc
            is WrprcValidation.Invalid -> return RegistrationOutcome.Reject(result.reason.toPresentationError())
        }

        // (3) Layer (c): WRP-identifier binding between the WRPAC and this WRPRC.
        val bound = request.verifierWrpIdentifiers.any { access ->
            validated.wrpIdentifiers.any { it.equals(access, ignoreCase = true) }
        }
        if (!bound) return RegistrationOutcome.Reject(PresentationError.RegistrationBindingMismatch)

        // (4) DCQL diff: requested claims vs the WRPRC's registered set for this vct.
        val registered = validated.registeredCredentials
            .filter { it.vct == request.vct }
            .flatMap { it.claimPaths }
            .toSet()
        val outside = request.requestedClaims.map { it.path }.filter { it !in registered }

        val verdict = if (outside.isEmpty()) {
            RegistrationVerdict.InsideRegistration
        } else {
            RegistrationVerdict.OutsideRegistration(outside)
        }
        return RegistrationOutcome.Proceed(verdict)
    }
}

/** Map a WRPRC layer-(b) rejection to the display-safe [PresentationError] the UI shows. */
private fun WrprcRejection.toPresentationError(): PresentationError = when (this) {
    WrprcRejection.Malformed,
    WrprcRejection.MissingFields,
    WrprcRejection.NotSupportedProfile,
    WrprcRejection.NotJAdES,
    -> PresentationError.UnsupportedRegistrationProfile
    WrprcRejection.BadSignature,
    WrprcRejection.ProviderCertInvalid,
    WrprcRejection.ProviderUntrusted,
    -> PresentationError.RegistrationUnverifiable
    WrprcRejection.Expired -> PresentationError.RegistrationExpired
    WrprcRejection.Revoked -> PresentationError.RegistrationRevoked
    WrprcRejection.StatusUnavailable -> PresentationError.RegistrationStatusUnavailable
}
