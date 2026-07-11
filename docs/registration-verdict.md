# Registration verdict (D1 flagship): WRPRC verification

This is the net-new IP (dev-plan.md Workstream D). The reference wallet app does no wallet-side
WRPRC handling; this layer is built on JWS / X.509 primitives on top of B5's access-layer
validation. It runs entirely against locally cached artifacts during consent and makes **zero
network calls** (dev-plan.md D1).

Normative standard: **ETSI TS 119 475 V1.2.1**.

## What it does

After B5 establishes that a presentation request is signed by a trusted, current access
certificate (WRPAC), D1 answers a second question: *is what this verifier is asking for within
what it registered for?* It extracts the verifier's registration certificate (WRPRC) from the
signed request's `verifier_info`, verifies it, binds it to the WRPAC, and diffs the requested
claims against the registered ones.

The user-facing label for the out-of-scope case is fixed: **"outside this verifier's
registration"**, never "over-ask". Semantic necessity ("is claim X actually needed for purpose
P?") is a separate, later layer and is out of scope here.

## Three actors

The registrar, the WRPAC provider, and the WRPRC provider are not necessarily the same entity, so
their trust is kept separate:

- **WRPAC provider** — issues the access certificate B5 validates; anchored by the flavor's
  `*_trust_anchors.pem` bundle.
- **WRPRC provider** — signs the registration certificate D1 validates; anchored by a **separate**
  `*_wrprc_trust_anchors.pem` bundle. A WRPRC that chains only to the WRPAC anchor does not
  validate, and vice versa.

## The pipeline (`DefaultRegistrationEvaluator`)

1. **Extract** (`WrprcExtractor`) — pull the registration certificate out of `verifier_info`. Each
   entry is `{ "format", "data" }`. The first `rc-wrp+*` entry decides the outcome: `rc-wrp+jwt` →
   proceed; any other `rc-wrp+*` (e.g. `rc-wrp+cwt`) → **rejected as an unsupported profile** (a
   CWT cannot be smuggled past by trailing a JWT); none → **rejected as missing** (fail-closed).
2. **Verify layer (b)** (`WrprcValidator`) — under the supported profile only:
   - `typ = rc-wrp+jwt`;
   - an AdES ECDSA `alg` (ES256/384/512);
   - **JAdES B-B markers**: the signing certificate is included (`x5c`) and a signed claimed
     signing time is present in the protected header — either the JAdES `sigT` header or, in the
     deployed V1.2.1 profile, a protected `iat` header (the mandated claimed-signing-time component
     for JAdES B-B signatures created after 2025-07-15). A plain signed JWT lacking both is
     rejected — a generic JWS check would not prove compliance, so the profile is asserted, not
     inferred.
   - signature verifies against the `x5c` leaf;
   - the leaf is temporally valid and chains to the **WRPRC-provider** anchor;
   - required policy/profile fields parse (`wrp`/`sub` identity, `iat`, `status.status_list`,
     `policy_id`, `credentials`);
   - `iat` is not future-dated and `exp` (when present) is not past;
   - the signed status list marks it good (revoked → fail; unknown → fail-closed).
3. **Bind layer (c)** (`WrpBinding`) — the **WRP-identifier binding** between WRPAC and WRPRC.
4. **DCQL diff** — the requested claims (B5's constrained subset) against the WRPRC's registered
   claim set for the requested `vct`. Anything requested beyond it is the outside set.

Steps 1–3 failing **reject** the request before any consent UI (the acceptance-matrix "fails"
cases). Step 4 never rejects: it produces a consent-time verdict of `InsideRegistration` or
`OutsideRegistration(claimsOutside)`.

## WRP-identifier binding fields (derivation)

The binding fields were originally *derived* from the V1.2.1 structure (the official PDF is not in
the repo and the local schema clone `external/etsi-ts-119-475-wrprc-schemas/` is **fixtures, not
authority**). They are now **reconciled against the deployed sandbox trust bundle**
(`verifier-sandbox.nachweis.tech/trust/manifest.json`), which was generated per ETSI TS 119 475
V1.2.1 by the infrastructure lane and declares the binding explicitly
(`wrpacField: subject.organizationIdentifier (2.5.4.97)`, `wrprcField: sub`):

- **WRPRC side** — the WRP legal identifier. The deployed V1.2.1 shape carries it as the `sub`
  **string** claim directly (e.g. `NTRDE-NACHWEIS-DEMO-0001`). The parser also accepts the object
  containers `wrp.id[].identifier` / `sub.id[].identifier` (e.g. an LEI
  `LEIXG-743700EB2QF2J1WRO915`) so an alternately-serialized certificate still reads.
- **WRPAC side** — the subject **organizationIdentifier** attribute, OID **2.5.4.97**
  (ETSI EN 319 412-1), which encodes the same semantic legal identifier.

Binding passes when the WRPAC organizationIdentifier equals at least one WRPRC identifier
(case-insensitive, full-string). This is **by standardized identity, not by certificate
instance**: a different WRPAC leaf (different key/serial) carrying the same organizationIdentifier
still binds; an unrelated identifier fails; absence on either side fails closed. At least one
interoperable identifier must match (dev-plan.md decision 6).

## Payload shape tolerance

The WRPRC payload parser reads the identity from `sub` as a plain string (the deployed V1.2.1
shape), or from the `wrp` / `sub` object containers (`id[].identifier`). It reads registered claims
from `credentials[].claim[].path` as array segments (the deployed shape, joined with `.`) or
`credentials[].claims[].path`, and the `vct` from `meta.vct_values[0]` or a plain `meta` string.
The `DeployedWrprcFixtureTest` pins this against the untouched public `wrprc/valid.jwt` fixture, so
the offline generator and the in-app validator are verified to agree on the real artifact shape.

## Deliberate boundaries

- **Not a full JAdES validator.** The `x5c` + claimed-signing-time (`sigT` / protected `iat`)
  marker check is a pragmatic B-B assertion sufficient to reject a generic JWS, not an ETSI
  TS 119 182-1 conformance validator. Full JAdES qualifying-property validation is future work.
- **Fail-closed status by default.** The demo flavor now bundles the deployed public
  WRPRC-provider trust anchor (the Nachweis Demo Root CA, shared with the WRPAC chain), so the
  provider trust store is populated and the x5c chain validates. The **status cache** is still
  empty until the out-of-band refresh path fetches the published signed status list, so at runtime
  a valid WRPRC still fails closed on status (`RegistrationStatusUnavailable`) rather than passing
  silently. The `production` flavor keeps both anchor bundles empty and fails closed entirely.
- **CWT/CBOR-AdES** is recognised and explicitly rejected. V1.2.1 permits it; this app supports
  only the JWT profile.

## Acceptance matrix

Implemented end-to-end in `RegistrationAcceptanceMatrixTest` (through the real
`PresentationController`) and per-layer in `WrprcValidatorTest`, `WrpBindingTest`,
`WrprcExtractorTest`, and `RegistrationEvaluatorTest`:

| Case | Result |
|---|---|
| Valid WRPAC + valid WRPRC + cached valid status | passes (InsideRegistration) |
| Revoked WRPRC | fails (RegistrationRevoked) |
| Revoked WRPAC | fails (Revoked, B5) |
| Expired WRPAC | fails (CertificateExpired, B5) |
| Expired WRPRC | fails (RegistrationExpired) |
| Unrelated WRP identifier | fails (RegistrationBindingMismatch) |
| Same WRP identifier, different WRPAC instance | passes (InsideRegistration) |
| CWT/CBOR WRPRC | fails (UnsupportedRegistrationProfile) |
| Missing `typ` / non-JAdES headers | fails (UnsupportedRegistrationProfile) |
| DCQL claim beyond the registered set | OutsideRegistration(claimsOutside) |
| DCQL within the registered set | InsideRegistration |
| Consent evaluation with network disabled | succeeds, zero network calls |
