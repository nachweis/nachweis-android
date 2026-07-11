# Deep links & issuance slice (B3 + B4)

This documents the credential-offer intake and issuance flow. It matches the code in
`app/src/main/java/com/quellkern/nachweis/deeplink/` and `.../issuance/`; if the code and
this document disagree, that is a bug.

## Deep links (B3)

The single activity registers three groups of schemes (see `AndroidManifest.xml` and
`DeepLinkIntake`):

- **Issuance offers:** `openid-credential-offer`, `haip-vci` — routed to the issuance
  controller.
- **Presentation:** `openid4vp`, `eudi-openid4vp`, `mdoc-openid4vp`, `haip-vp` — routed to the
  presentation controller (B5); see [presentation-slice.md](presentation-slice.md).
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
on-device tests for deep-link resolution).

**Live end-to-end issuance was completed on 2026-07-11** against the deployed sandbox issuer
(`api-sandbox.nachweis.tech`, EUDIPLO with the provisioned `nachweis` SD-JWT PID tenant): a
pre-authorized offer resolved through the allowlist and consent, Keystore user authentication
was exercised, and a real SD-JWT PID (`urn:eudi:pid:de:1`) was issued and stored, appearing
in the credential list. Run on an emulator (no physical-device/StrongBox run yet).

One caveat: completing issuance currently requires a locally patched `wallet-core` build
because of an upstream OpenID4VCI bug ([#353] / [PR #369]); `main` stays on the official
release and documents this in [`interop-walletcore.md`](interop-walletcore.md).

[#353]: https://github.com/eu-digital-identity-wallet/eudi-lib-android-wallet-core/issues/353
[PR #369]: https://github.com/eu-digital-identity-wallet/eudi-lib-android-wallet-core/pull/369
