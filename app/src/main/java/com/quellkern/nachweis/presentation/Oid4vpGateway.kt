package com.quellkern.nachweis.presentation

/**
 * The seam between [PresentationController] and wallet-core's OpenID4VP stack. Obtaining the
 * signed request and dispatching the response are the only two operations that touch the
 * network or the SDK; everything between them — validation and consent — is pure. Keeping
 * both behind this interface lets the controller be a JVM-unit-tested state machine with a
 * fake gateway, and confines the wallet-core response path (exercised only against a live
 * verifier) to [DefaultOid4vpGateway].
 */
interface Oid4vpGateway {

    /**
     * Obtain the signed request object for [requestUri]. Reads a `request` value directly or
     * fetches a `request_uri`; this is request *arrival*, distinct from consent-time trust
     * resolution (which is local). Throws if no signed request can be obtained.
     */
    suspend fun obtainSignedRequest(requestUri: String): SignedPresentationRequest

    /**
     * Build and dispatch the presentation for [request], disclosing exactly
     * [disclosedClaims]. Suspends until the response is sent (or throws on failure).
     */
    suspend fun sendResponse(request: ValidatedPresentationRequest, disclosedClaims: List<RequestedClaim>)

    /** Tell the verifier the user declined, where the transport supports it. Best-effort. */
    fun reject()
}
