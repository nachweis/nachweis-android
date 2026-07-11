# Presentation slice (B5)

This documents the OpenID4VP presentation flow and its signed-request validation. It matches
the code in `app/src/main/java/com/quellkern/nachweis/presentation/`; if the code and this
document disagree, that is a bug.

## Flow

A presentation deep link (`openid4vp` and the related schemes registered in B3) is routed to
`PresentationController.onRequest(requestUri)`:

1. **Obtain** — `Oid4vpGateway.obtainSignedRequest` lifts the signed request object (the JAR)
   out of the link: a `request` value carried inline, or the body fetched from a
   `request_uri`. This is request *arrival*.
2. **Validate** — `PresentationRequestValidator.validate` runs the full access/request-layer
   checks against **locally cached** trust and status artifacts (see below). An invalid
   request lands in `Rejected` with a typed reason **before any consent UI**.
3. **Consent** — a valid request stops at `AwaitingConsent`, showing the verifier identity, a
   trust verdict banner, and the exact claims requested.
4. **Disclose** — on the user's allow, `sendResponse` builds the SD-JWT presentation and
   dispatches it; on decline, `reject` notifies the verifier and nothing is disclosed.

## What the validator checks (in `PresentationRequestValidator`)

Every check runs in-app, in this order, and each has its own typed `PresentationError`:

1. **Signed request required** — the request must be a JWS; a plain or `alg=none` token is
   `NotSigned`.
2. **Certificate present** — the header must carry an `x5c` chain (`NoCertificate` otherwise).
3. **Signature** — verified against the leaf (WRPAC) public key (`BadSignature`).
4. **Validity** — the leaf must be temporally valid now (`CertificateExpired`).
5. **Certification path** — the chain must build a PKIX path to a locally bundled demo trust
   anchor (`Untrusted`). Revocation is *not* checked here (next step).
6. **Status** — the WRPAC must be `Good` in the cached status list; `Revoked` fails, and
   `Unknown` **fails closed** (`StatusUnavailable`) — an absent status is never a pass.
7. **client_id binding** — the `client_id` must bind to the leaf via `x509_san_dns` (SAN
   dNSName match) or `x509_hash` (SHA-256 of the DER), else `ClientIdMismatch`.
8. **DCQL subset** — exactly one SD-JWT credential with a simple claim set; `claim_sets`,
   `credential_sets`, `multiple`, and per-claim value restrictions are `UnsupportedQuery`,
   never narrowed or unioned. A non-PID vct is `UnsupportedCredential`.

The validator holds **no network, wallet-core, or Android dependency**. Trust comes from
`TrustStore` (the flavor's bundled demo anchors) and status from `RequestStatusSource` (a
snapshot of the cached status list). Consent therefore makes **zero verifier-keyed network
calls** by construction; `PresentationControllerTest` additionally asserts the flow touches
the gateway once on arrival and again only after the user allows.

### Why this is separate from wallet-core

dev-plan.md notes wallet-core 0.28.1 defaults to `ReaderAuthPolicy.EnforceIfPresent`, which
*accepts an absent reader auth*. Remote OpenID4VP request authenticity is therefore a
**separate requirement**, asserted here in our own code rather than inferred from the library.

## What B5 does NOT do (the D1 seam)

B5 is the access/request layer only (dev-plan.md D1 layer (a)). It always yields
`RegistrationVerdict.NotEvaluated` and carries the request's `verifier_info` through verbatim
in `ValidatedPresentationRequest.verifierInfo`. The D1 flagship parses the WRPRC out of that,
verifies it (JWT `rc-wrp+jwt`, JAdES B-B), checks the WRP-identifier binding, and replaces the
verdict — including the `"outside this verifier's registration"` label, which B5 never renders.

## Honest boundary (what is proven vs. what is not)

- **Proven (JVM + emulator):** the full validation matrix (valid / unsigned / bad signature /
  untrusted / expired / revoked / unknown-status / client-id mismatch / DCQL beyond subset),
  the DCQL subset parser, PKIX path building against the demo anchor, and the controller's
  ordering guarantees including zero-network-during-consent. Deep-link schemes resolve on the
  device (`DeepLinkResolutionInstrumentedTest`).
- **Proven live (2026-07-11, emulator):** the whole request-validation and verdict pipeline
  ran against the deployed verifier (`verifier-sandbox.nachweis.tech`): real signed request
  fetched, WRPAC chain validated with status **Good** via the deployed CRL, the ETSI TS 119 475
  V1.2.1 WRPRC verified with status **Valid** via the live signed status list, WRP-identifier
  binding matched, DCQL diff computed, and the `"outside this verifier's registration"`
  verdict rendered. Status and CRL artifacts are refreshed out of band; consent still makes
  zero network calls.
- **Not yet exercised live:** the response-sending cycle
  (`startRemotePresentation → generateResponse → sendResponse`). The verifier's only deployed
  request over-asks relative to its registration, so the demo flow correctly stops at the
  warning verdict instead of sharing. The within-registration positive verdict is likewise
  covered by tests only until the verifier serves an in-scope request. No physical-device /
  StrongBox run yet (emulator only).
