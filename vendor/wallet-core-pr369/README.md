# Vendored wallet-core (patched with upstream PR #369)

This directory documents the patched `eudi-lib-android-wallet-core` artifact that the
`live-issuance/walletcore-pr369` branch consumes from `vendor/maven/`. **The `main` branch does
not use this** — it stays on the official released `eu.europa.ec.eudi:eudi-lib-android-wallet-core:0.28.1`.
This branch exists so live issuance against the deployed sandbox is reproducible (including in CI)
without waiting for an upstream release.

## Why

Released wallet-core `0.28.1` cannot complete issuance against a spec-compliant OpenID4VCI issuer
(our deployed EUDIPLO) that returns RAR `authorization_details` with `credential_identifiers` in the
token response. `SubmitRequest` unconditionally builds a configuration-based credential request, so
`openid4vci-kt` rejects it before any network call:

> `IllegalArgumentException: Authorization detail type of openid_credential require usage of credential identifiers in credential request`

This is upstream bug **[#353](https://github.com/eu-digital-identity-wallet/eudi-lib-android-wallet-core/issues/353)**.
The fix is upstream PR **[#369](https://github.com/eu-digital-identity-wallet/eudi-lib-android-wallet-core/pull/369)**:
when the authorized request carries `credentialIdentifiers`, send an identifier-based credential
request (falling back to configuration-based otherwise). The deployed EUDIPLO already accepts the
identifier-based request; with this patch, issuance of a real SD-JWT PID (`urn:eudi:pid:de:1`)
completes end-to-end. EUDIPLO offers no configuration lever to suppress `authorization_details`, and
no newer release of either side changes this, so the fix belongs on the wallet.

## Provenance (rebuildable, auditable)

- Upstream: `https://github.com/eu-digital-identity-wallet/eudi-lib-android-wallet-core`
- Tag `v0.28.1`, commit `9f630067fafddd34dc7df4d5e5f143af9dd17141`
- Patch: [`SubmitRequest-PR369.patch`](SubmitRequest-PR369.patch) — byte-identical to upstream PR #369,
  a single-file change to `SubmitRequest.kt` (8 insertions, 2 deletions). No other source is modified.
- License: Apache-2.0 (unchanged from upstream).
- Built with JDK 17.
- Vendored coordinate: `eu.europa.ec.eudi:eudi-lib-android-wallet-core:0.28.1-pr369`
- `.aar` SHA-256: `ee53d1fdf1140d3a7085643cb0632097a3d9138485b8258ea4df18f43a9e65d1`

## Rebuild the artifact yourself

```bash
git clone --branch v0.28.1 https://github.com/eu-digital-identity-wallet/eudi-lib-android-wallet-core.git
cd eudi-lib-android-wallet-core
git rev-parse HEAD   # must be 9f630067fafddd34dc7df4d5e5f143af9dd17141
git apply /path/to/vendor/wallet-core-pr369/SubmitRequest-PR369.patch
JAVA_HOME=<jdk17> ANDROID_HOME=<android-sdk> \
  ./gradlew :wallet-core:publishToMavenLocal -PVERSION_NAME=0.28.1-pr369 -x signMavenPublication
```

Then the artifact lands in `~/.m2/.../0.28.1-pr369/`. This repo's copy under `vendor/maven/` was
produced from it with two edits, so it resolves via POM only: the POM `<version>` was set to
`0.28.1-pr369` and the "published-with-gradle-metadata" marker was removed (the `.module` file is
omitted). The POM still carries the full transitive dependency set, so nothing about resolution
changes except which `SubmitRequest` payload is built.

## When upstream releases the fix

Delete this branch, drop `vendor/`, and bump `walletCore` back to the official released version that
includes PR #369. Nothing else on `main` depends on any of this.
