# Deep links & issuance slice (B3 + B4)

This documents the credential-offer intake and issuance flow. It matches the code in
`app/src/main/java/com/quellkern/nachweis/deeplink/` and `.../issuance/`; if the code and
this document disagree, that is a bug.

## Deep links (B3)

The single activity registers three groups of schemes (see `AndroidManifest.xml` and
`DeepLinkIntake`):

- **Issuance offers:** `openid-credential-offer`, `haip-vci` — routed to the issuance
  controller.
- **Presentation:** `openid4vp`, `eudi-openid4vp`, `mdoc-openid4vp`, `haip-vp` — the scheme
  is owned now so the app appears for these requests, but handling lands with the B5
  presentation slice; until then the UI states plainly that presentation is not yet
  supported. Nothing is silently dropped.
- **Auth-code redirect:** `com.quellkern.nachweis://authorization` — our own redirect target,
  never the EU reference app's `eu.europa.ec.euidi`. A wrong redirect scheme would leave an
  auth-code issuance unable to return into the app.

`DeepLinkIntake.classify` is a pure function of scheme (and host, for the redirect); it is
unit-tested on the JVM and, against the merged manifest, verified on-device
(`DeepLinkResolutionInstrumentedTest`).

## Issuance (B4)

Flow: **QR gesture → resolve offer → allowlist + supported-credential check → consent →
issue → document list.**

- **QR scanning** uses CameraX + ML Kit barcode; camera permission is requested inline.
- **Allowlist:** an offer is accepted only from a configured issuer origin
  (`IssuerAllowlist`, per-flavor). An offer from an unlisted issuer ends in
  `Declined(IssuerNotAllowed)` before any credential is requested. Production's allowlist is
  exactly its configured issuer; the demo flavor additionally honors a developer-local
  override (below).
- **Supported credential:** this slice issues exactly the **unpatched SD-JWT PID**
  (`vct = urn:eudi:pid:de:1`). The vct is left unpatched on purpose — augenmass's DCQL matches
  it exactly, so rewriting it to `urn:eudi:pid:1` would fix the app's label but break the
  presentation match. An offer without it ends in `Declined(UnsupportedCredential)`.
- **Consent is explicit:** resolution stops at `AwaitingConsent`; issuance begins only on
  `confirm()`. Nothing is issued silently.
- **Device auth:** credential keys are Keystore-enforced (B2), so issuance signing prompts
  for device authentication via BiometricPrompt. The controller reflects this as
  `AwaitingUserAuth`; the gateway drives the prompt, so the controller never touches
  BiometricPrompt or Keystore types.

All wallet-core types stay behind `Oid4vciGateway`, so `IssuanceController` is a pure state
machine unit-tested with a fake gateway; the real `DefaultOid4vciGateway` is exercised
end-to-end only against a live issuer.

## Local development override

Committed demo defaults point at the canonical `*-sandbox.nachweis.tech` hosts. To test
against a local EUDIPLO, set in `local.properties` (git-ignored):

```
nachweis.localIssuerOverride=https://<local-issuer-origin>
```

When set, the demo build adds that origin to the allowlist and uses it as the issuer URL.
It is developer-machine state, never a committed default, and production ignores it entirely.

## End-to-end status (honest boundary)

The client flow is implemented and its logic is fully tested (JVM unit tests for deep-link
routing, allowlist, offer evaluation, error mapping, and the controller state machine;
on-device tests for deep-link resolution). **A live end-to-end issuance was not run**, for two
independent reasons observed this session:

1. **No reachable provisioned issuer.** The local EUDIPLO (`localhost:3002`) has no
   provisioned SD-JWT PID tenant — every `issuers/*` metadata path returns HTTP 500. The
   `nachweis` tenant is provisioned by the infrastructure lane (netcub
   `scripts/provision-eudiplo-demo.sh`, run on the VPS), and the public sandbox
   (`api-sandbox.nachweis.tech`) is not yet deployed.
2. **Transport.** wallet-core's OpenID4VCI expects an https issuer identifier; a purely local
   http issuer would not satisfy it even with debug cleartext enabled.

The end-to-end issuance smoke test (scan → issue → the credential appears in the list) should
be run once the sandbox issuer is deployed, using a demo build (optionally with the local
override against a provisioned local EUDIPLO over https).
