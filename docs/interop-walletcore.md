# wallet-core interop: OpenID4VCI `authorization_details` and live issuance

## Summary

`main` builds against the official released `eu.europa.ec.eudi:eudi-lib-android-wallet-core:0.28.1`.
That release cannot complete issuance against our deployed EUDIPLO issuer because of an upstream bug.
The fix exists upstream but is not yet released, so `main` stays on the official artifact and a
reproducible patched build is provided on a separate branch/PR (not merged).

## The bug

The EUDIPLO token endpoint returns RAR `authorization_details` carrying `credential_identifiers`
(spec-compliant). Per OpenID4VCI, the wallet must then send an **identifier-based** credential
request. wallet-core `0.28.1` always builds a **configuration-based** request in
`SubmitRequest.submitRequest(...)`, so `eudi-lib-jvm-openid4vci-kt` rejects it before any network
call:

```
IllegalArgumentException: Authorization detail type of openid_credential
require usage of credential identifiers in credential request
  at eu.europa.ec.eudi.openid4vci.internal.RequestIssuanceImpl.validateRequestPayload
```

- Upstream bug: <https://github.com/eu-digital-identity-wallet/eudi-lib-android-wallet-core/issues/353>
- Upstream fix (open): <https://github.com/eu-digital-identity-wallet/eudi-lib-android-wallet-core/pull/369>

There is no EUDIPLO configuration that suppresses `authorization_details` for the pre-authorized flow
(verified against the deployed v5.1.0 and upstream `main`), and no newer release of either project
changes the behavior. The fix belongs on the wallet side, and the deployed EUDIPLO already accepts
the identifier-based request.

## Why `main` is not patched

This repo is an authorship reference, so `main` keeps clean, official dependencies. Vendoring a
self-built fork of a security-critical library onto `main` would trade that provenance for
convenience. Instead:

- `main` documents the issue (here + in the README) and links the upstream PR.
- A reproducible patched build lives on branch `live-issuance/walletcore-pr369`
  (PR [#1](https://github.com/nachweis/nachweis-android/pull/1)), which vendors
  `0.28.1-pr369` (official `v0.28.1` + PR #369) under `vendor/maven/` with full provenance and a
  rebuild recipe in `vendor/wallet-core-pr369/README.md`. That branch builds, passes tests, and
  issues a real SD-JWT PID (`urn:eudi:pid:de:1`) end-to-end.

## When upstream releases the fix

Bump `walletCore` on `main` to the official release that includes PR #369, then close the branch/PR
and delete `vendor/`.
