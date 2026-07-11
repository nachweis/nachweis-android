package com.quellkern.nachweis.presentation

/**
 * A signed OpenID4VP request as it arrives, before any validation. [compactJws] is the
 * request object in JWS compact form (the "JAR"); it is the sole input the
 * [PresentationRequestValidator] trusts — every field the consent UI shows is derived from
 * the *signed* payload, never from unauthenticated deep-link query parameters.
 *
 * The gateway is responsible for obtaining this: reading a `request` value directly, or
 * fetching a `request_uri`. That fetch is request *arrival*, not consent-time trust
 * resolution, so it does not violate the "no verifier-keyed network traffic during consent"
 * rule (dev-plan.md D1) — trust and status come from local caches inside the validator.
 */
data class SignedPresentationRequest(
    /** The request object in JWS compact serialization (`header.payload.signature`). */
    val compactJws: String,
)

/**
 * One claim the verifier asks to disclose, as a dotted path into the SD-JWT credential
 * (e.g. `given_name`, or `address.locality`). Kept as the standardized path so D1 can match
 * it against the WRPRC's registered claims by identity.
 */
data class RequestedClaim(
    /** The DCQL claim path segments, joined with '.', as shown and matched. */
    val path: String,
)

/**
 * A request that passed the full access/request-layer validation (dev-plan.md D1 layer (a)):
 * signature, WRPAC certification path, WRPAC status, and client-id binding all checked
 * against locally cached trust and status artifacts. This is what the consent UI renders.
 *
 * [registrationVerdict] is always [RegistrationVerdict.NotEvaluated] when produced by B5;
 * D1 recomputes it from [verifierInfo]. [verifierInfo] is carried through verbatim and
 * opaquely so D1 can extract the WRPRC without B5 needing to understand it.
 */
data class ValidatedPresentationRequest(
    /** Stable verifier identity from the certificate binding (SAN dNSName or subject CN). */
    val verifierIdentity: String,
    /** Origin (scheme://host[:port]) the response will be dispatched to. */
    val responseOrigin: String,
    /** Full response URI from the signed request; used only by the gateway to dispatch. */
    val responseUri: String,
    /** The verifier's stated purpose text, if present; display-only, never trusted for logic. */
    val purpose: String?,
    /** The requested SD-JWT credential type (vct). */
    val vct: String,
    /** The claims requested, in DCQL order. */
    val requestedClaims: List<RequestedClaim>,
    /** The request nonce, bound into the response by the gateway. */
    val nonce: String,
    /**
     * The raw `verifier_info` array from the signed payload, as a JSON string, or null when
     * absent. Opaque to B5; D1 parses the WRPRC out of it. Never shown to the user.
     */
    val verifierInfo: String?,
    /**
     * The WRP identifier(s) carried by the WRPAC's subject organizationIdentifier (OID 2.5.4.97),
     * extracted by the access-layer validator. D1 binds the WRPRC to the WRPAC by matching one of
     * these against the WRPRC's WRP identifiers, without re-parsing the certificate. Empty when
     * the access certificate carries no organizationIdentifier.
     */
    val verifierWrpIdentifiers: List<String> = emptyList(),
    /** The registration scope verdict; [RegistrationVerdict.NotEvaluated] until D1 runs. */
    val registrationVerdict: RegistrationVerdict = RegistrationVerdict.NotEvaluated,
)
