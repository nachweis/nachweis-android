package com.quellkern.nachweis.presentation

/**
 * Whether the verifier's request falls inside its registered scope. This is the **seam for
 * the D1 flagship** (dev-plan.md Workstream D): B5 establishes only that the request is
 * signed by a trusted, current access certificate (WRPAC), and always yields
 * [NotEvaluated]. D1 later parses the WRPRC from the request's `verifier_info`, verifies it,
 * and replaces the verdict with [InsideRegistration] or [OutsideRegistration].
 *
 * The user-facing label for [OutsideRegistration] is fixed by dev-plan.md D1:
 * *"outside this verifier's registration"*, never "over-ask". B5 never renders that label
 * because B5 never produces that state.
 */
sealed interface RegistrationVerdict {
    /**
     * The registration has not been evaluated. B5's terminal verdict; the consent UI shows a
     * neutral banner and no "outside registration" wording.
     */
    data object NotEvaluated : RegistrationVerdict

    /** Every requested claim is within the verifier's registered scope (produced by D1). */
    data object InsideRegistration : RegistrationVerdict

    /**
     * One or more requested claims fall outside the verifier's registration (produced by
     * D1). [claimsOutside] names the offending claim paths for the consent banner.
     */
    data class OutsideRegistration(val claimsOutside: List<String>) : RegistrationVerdict
}
