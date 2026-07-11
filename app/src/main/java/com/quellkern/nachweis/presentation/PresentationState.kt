package com.quellkern.nachweis.presentation

/**
 * Lifecycle of a single presentation attempt, observed by the UI. Consent ([AwaitingConsent])
 * is explicit and always preceded by full request validation: an invalid request lands in
 * [Rejected] without ever showing a consent sheet, so the user is only ever asked to disclose
 * against a request whose sender was cryptographically established.
 */
sealed interface PresentationState {
    /** No presentation in progress. */
    data object Idle : PresentationState

    /** Obtaining and validating the signed request. */
    data object Resolving : PresentationState

    /**
     * The request is valid and awaiting the user's decision. Carries the validated request
     * whose [ValidatedPresentationRequest.registrationVerdict] the consent banner renders
     * (always [RegistrationVerdict.NotEvaluated] until D1).
     */
    data class AwaitingConsent(val request: ValidatedPresentationRequest) : PresentationState

    /** The user granted; building and dispatching the response. */
    data object Sending : PresentationState

    /** The response was dispatched successfully. */
    data object Sent : PresentationState

    /** The user declined a valid request; nothing was disclosed. */
    data object Declined : PresentationState

    /**
     * The request was refused before consent (validation failed) or the response failed to
     * send. [error] is a typed, display-safe reason.
     */
    data class Rejected(val error: PresentationError) : PresentationState
}
