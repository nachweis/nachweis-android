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
4. **Disclose** — on the user's allow, `sendResponse` re-resolves the request through
   wallet-core (`startRemotePresentation`), unlocks the PID signing key with a device-auth
   prompt (`PresentationAuthenticator`, mirroring issuance's `DocumentRequiresUserAuth`),
   builds and — for `response_mode=direct_post.jwt` — encrypts the `vp_token`, then dispatches
   it and **waits for the verifier's answer** before reporting success; on decline, `reject`
   notifies the verifier and nothing is disclosed.

wallet-core only exposes an OpenID4VP manager when the wallet is **configured for OpenID4VP**
(`WalletConfigFactory.build` → `configureOpenId4Vp`: the x509 client-id schemes, the
presentation deep-link schemes, the SD-JWT PID format, and response encryption). Without that
block `startRemotePresentation` throws `error("Not supported scheme")` and every allow fails
while validation and consent — which run in our own code — still work.

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
- **Proven live (2026-07-11, emulator):** the whole response-sending cycle
  (`startRemotePresentation → generateResponse → sendResponse`) against the deployed verifier
  (`verifier-sandbox.nachweis.tech`, `response_mode=direct_post.jwt`, `x509_hash` client-id).
  Tapping **Allow** re-resolves the request, raises the device-auth prompt, encrypts the
  `vp_token` (JARM), POSTs it to the verifier's `response_uri`, and the verifier **accepts** it;
  the app shows "Shared" only after that acceptance (`TransferEvent.ResponseSent`), and a
  verifier rejection surfaces as an error rather than a false success. This required configuring
  wallet-core for OpenID4VP and wiring presentation-time key-unlock (see step 4); before that,
  Allow failed with `error("Not supported scheme")`.
- **Not yet exercised live:** the within-registration *positive* verdict is covered by tests
  only until the verifier serves an in-scope request (its only deployed request over-asks, so
  the flagship verdict is the warning banner). No physical-device / StrongBox run yet (emulator
  only).
