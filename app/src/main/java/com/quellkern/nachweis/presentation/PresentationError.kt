package com.quellkern.nachweis.presentation

/**
 * Typed, display-safe reasons a presentation request is rejected or a presentation fails.
 * As in [com.quellkern.nachweis.issuance.IssuanceError], raw exception text and any
 * verifier-supplied string are kept out of [publicMessage] so nothing echoes request detail
 * to the user or the log; the cause is retained only where a caller may need it for triage.
 *
 * The set is ordered by the validation layer that produces it (dev-plan.md D1, access/
 * request layer): the request must be *read*, then *signed*, then *trusted*, then *current*,
 * then *bound to its client id*, then carry a *supported query*. Each stage has its own
 * failure so the UI and tests distinguish "not signed" from "signed by an untrusted party".
 */
sealed interface PresentationError {
    /** A message safe to show or log; never derived from request or credential material. */
    val publicMessage: String

    /** The request URI or object could not be read into a request object at all. */
    data object Unreadable : PresentationError {
        override val publicMessage = "This request could not be read. Ask the verifier to try again."
    }

    /**
     * The request was not a signed request object (no JWS, or `alg=none`). A remote
     * OpenID4VP request must be signed; an unsigned one is refused before any consent UI.
     */
    data object NotSigned : PresentationError {
        override val publicMessage = "This request is not signed, so it can't be trusted."
    }

    /** The signature did not verify against the certificate in the request header. */
    data object BadSignature : PresentationError {
        override val publicMessage = "This request's signature is invalid."
    }

    /** No certificate chain accompanied the signed request, so the signer is unverifiable. */
    data object NoCertificate : PresentationError {
        override val publicMessage = "This request has no certificate, so the sender can't be identified."
    }

    /** The signing certificate does not chain to a trusted nachweis demo anchor. */
    data object Untrusted : PresentationError {
        override val publicMessage = "This verifier is not one this app is configured to trust."
    }

    /** The signing certificate is expired or not yet valid at the current time. */
    data object CertificateExpired : PresentationError {
        override val publicMessage = "This verifier's certificate is expired."
    }

    /** The access certificate (WRPAC) is revoked per the cached status list. */
    data object Revoked : PresentationError {
        override val publicMessage = "This verifier's authorisation has been revoked."
    }

    /**
     * Status could not be established from locally cached artifacts. Fail-closed: absence of
     * required status material is a rejection, never a silent pass (dev-plan.md D1).
     */
    data object StatusUnavailable : PresentationError {
        override val publicMessage = "This verifier's status can't be confirmed right now."
    }

    /**
     * The `client_id` does not bind to the signing certificate (x509_san_dns / x509_hash).
     * A verifier could otherwise present a valid-but-unrelated certificate.
     */
    data object ClientIdMismatch : PresentationError {
        override val publicMessage = "This request's sender identity doesn't match its certificate."
    }

    /**
     * The request's DCQL query is outside the supported subset (more than one credential,
     * `claim_sets`, `credential_sets`, `multiple`, or value restrictions). Never unioned or
     * silently narrowed — the request is refused so the user is never asked to disclose
     * against a query the app did not fully understand.
     */
    data object UnsupportedQuery : PresentationError {
        override val publicMessage = "This request asks in a way this app doesn't support yet."
    }

    /** The requested credential type is not one held or supported (the SD-JWT PID). */
    data object UnsupportedCredential : PresentationError {
        override val publicMessage = "This request asks for a credential this app can't present."
    }

    /**
     * The request carries no registration certificate (WRPRC) in `verifier_info`, so the
     * verifier's registered scope can't be established. Fail-closed: with the flagship enabled,
     * a request whose registration cannot be evaluated is refused (dev-plan.md D1).
     */
    data object RegistrationMissing : PresentationError {
        override val publicMessage = "This verifier didn't provide a registration, so it can't be checked."
    }

    /**
     * The registration certificate uses an unsupported profile — a CWT/CBOR WRPRC, a non-JWT
     * `typ`, missing JAdES markers, or missing required fields. The JWT `rc-wrp+jwt` / JAdES B-B
     * profile is asserted, not inferred (dev-plan.md D1).
     */
    data object UnsupportedRegistrationProfile : PresentationError {
        override val publicMessage = "This verifier's registration is in a format this app can't verify."
    }

    /**
     * The registration certificate's signature or provider certification path did not verify
     * against the locally trusted WRPRC-provider anchor.
     */
    data object RegistrationUnverifiable : PresentationError {
        override val publicMessage = "This verifier's registration could not be verified."
    }

    /** The registration certificate is revoked per the cached signed status list. */
    data object RegistrationRevoked : PresentationError {
        override val publicMessage = "This verifier's registration has been revoked."
    }

    /** The registration certificate is expired or future-dated. */
    data object RegistrationExpired : PresentationError {
        override val publicMessage = "This verifier's registration is expired."
    }

    /**
     * The registration certificate's status could not be established from local caches.
     * Fail-closed: absence of required status material is a rejection (dev-plan.md D1).
     */
    data object RegistrationStatusUnavailable : PresentationError {
        override val publicMessage = "This verifier's registration status can't be confirmed right now."
    }

    /**
     * The registration certificate does not bind to the request's access certificate: their
     * WRP identifiers do not match (ETSI TS 119 475 V1.2.1 binds WRPAC and WRPRC by identity).
     */
    data object RegistrationBindingMismatch : PresentationError {
        override val publicMessage = "This verifier's registration doesn't match its access certificate."
    }

    /** Anything not classified above; cause retained for triage, never for display. */
    data class Unexpected(val cause: Throwable) : PresentationError {
        override val publicMessage = "This request could not be processed."
    }
}
