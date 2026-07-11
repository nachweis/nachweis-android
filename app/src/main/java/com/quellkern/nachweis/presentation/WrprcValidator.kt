package com.quellkern.nachweis.presentation

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory
import com.nimbusds.jose.util.X509CertUtils
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Verifies a WRPRC JWT under the **supported profile only** — dev-plan.md D1 layer (b): a JWT
 * WRPRC with `typ=rc-wrp+jwt` and a JAdES B-B signature, chaining to the WRPRC **provider's**
 * trust anchor (a different anchor from the WRPAC's — the registrar, WRPAC provider, and WRPRC
 * provider are distinct actors), with a valid status-list entry and required policy/profile
 * fields. Everything runs against the caller-supplied [providerTrust], [statusSource], and clock;
 * no network, wallet-core, or Android dependency, so it is exhaustively unit-tested on the JVM
 * and makes zero network calls during consent.
 *
 * CWT/CBOR-AdES is rejected (its recognition happens earlier, in [WrprcExtractor]); a generic
 * JWS check is deliberately not enough — the JAdES B-B markers (signing-certificate inclusion
 * via `x5c` and a signed claimed signing time `sigT`) are asserted so a plain signed JWT does
 * not pass as a conformant WRPRC. This is a pragmatic B-B marker check, not a full ETSI
 * TS 119 182-1 conformance validator; that boundary is documented in docs/registration-verdict.md.
 */
class WrprcValidator(
    private val providerTrust: TrustStore,
    private val statusSource: WrprcStatusSource,
) {
    private val verifierFactory = DefaultJWSVerifierFactory()

    /** JAdES/AdES signatures here use the ECDSA family; RSA-PKCS1 and `none` are out of profile. */
    private val supportedAlgs = setOf(JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512)

    private val wrprcType = JOSEObjectType(WrprcExtractor.JWT_FORMAT)

    /** Verify [compactJws] as of [now]. Returns the parsed WRPRC or the first failing check. */
    fun validate(compactJws: String, now: Date): WrprcValidation {
        return try {
            validateInternal(compactJws, now)
        } catch (t: Throwable) {
            WrprcValidation.Invalid(WrprcRejection.Malformed)
        }
    }

    private fun validateInternal(compactJws: String, now: Date): WrprcValidation {
        val jws = try {
            JWSObject.parse(compactJws)
        } catch (_: java.text.ParseException) {
            return WrprcValidation.Invalid(WrprcRejection.Malformed)
        }

        // (1) Profile markers: typ=rc-wrp+jwt and an AdES ECDSA alg. Not a generic JWS.
        if (jws.header.type != wrprcType) return WrprcValidation.Invalid(WrprcRejection.NotSupportedProfile)
        if (jws.header.algorithm !in supportedAlgs) return WrprcValidation.Invalid(WrprcRejection.NotJAdES)

        // (2) JAdES B-B markers: the signing certificate is included (x5c) and a signed claimed
        // signing time is present in the protected header. A plain signed JWT lacking these is not
        // a WRPRC. The claimed signing time is carried either as the JAdES `sigT` header or, in the
        // deployed V1.2.1 profile, as a protected `iat` header (the mandated claimed-signing-time
        // component for JAdES B-B signatures created after 2025-07-15); either satisfies the marker.
        val chain = parseX5c(jws) ?: return WrprcValidation.Invalid(WrprcRejection.NotJAdES)
        val hasClaimedSigningTime =
            jws.header.getCustomParam("sigT") != null || jws.header.getCustomParam("iat") != null
        if (!hasClaimedSigningTime) return WrprcValidation.Invalid(WrprcRejection.NotJAdES)
        val leaf = chain.first()

        // (3) The signature must verify against the WRPRC provider leaf.
        val verifier = try {
            verifierFactory.createJWSVerifier(jws.header, leaf.publicKey)
        } catch (_: Exception) {
            return WrprcValidation.Invalid(WrprcRejection.BadSignature)
        }
        if (!jws.verify(verifier)) return WrprcValidation.Invalid(WrprcRejection.BadSignature)

        // (4) The provider leaf must be temporally valid and chain to the provider anchor.
        try {
            leaf.checkValidity(now)
        } catch (_: java.security.cert.CertificateExpiredException) {
            return WrprcValidation.Invalid(WrprcRejection.ProviderCertInvalid)
        } catch (_: java.security.cert.CertificateNotYetValidException) {
            return WrprcValidation.Invalid(WrprcRejection.ProviderCertInvalid)
        }
        if (!providerTrust.validatesPath(chain, now)) return WrprcValidation.Invalid(WrprcRejection.ProviderUntrusted)

        // (5) Required policy/profile fields must parse.
        val wrprc = WrprcExtractor.parsePayload(jws.payload.toString())
            ?: return WrprcValidation.Invalid(WrprcRejection.MissingFields)

        // (6) Temporal validity of the WRPRC itself: not future-dated, not past exp.
        val nowSeconds = now.time / 1000
        if (wrprc.issuedAtEpochSeconds > nowSeconds + CLOCK_SKEW_SECONDS) {
            return WrprcValidation.Invalid(WrprcRejection.Expired)
        }
        if (wrprc.expiresAtEpochSeconds != null && nowSeconds > wrprc.expiresAtEpochSeconds) {
            return WrprcValidation.Invalid(WrprcRejection.Expired)
        }

        // (7) Signed status list: revoked fails; unknown fails closed.
        when (statusSource.statusOf(wrprc.statusRef)) {
            CertStatus.Revoked -> return WrprcValidation.Invalid(WrprcRejection.Revoked)
            CertStatus.Unknown -> return WrprcValidation.Invalid(WrprcRejection.StatusUnavailable)
            CertStatus.Good -> Unit
        }

        return WrprcValidation.Valid(wrprc)
    }

    private fun parseX5c(jws: JWSObject): List<X509Certificate>? {
        val encoded = jws.header.x509CertChain ?: return null
        if (encoded.isEmpty()) return null
        val chain = encoded.mapNotNull { X509CertUtils.parse(it.decode()) }
        return chain.takeIf { it.size == encoded.size && it.isNotEmpty() }
    }

    private companion object {
        /** Tolerance for a WRPRC issued moments in the future relative to the local clock. */
        const val CLOCK_SKEW_SECONDS = 60L
    }
}

/** Result of [WrprcValidator.validate]. */
sealed interface WrprcValidation {
    /** The WRPRC passed layer (b); carries the parsed registration for binding and DCQL diff. */
    data class Valid(val wrprc: Wrprc) : WrprcValidation

    /** The WRPRC failed a check; [reason] is the first (most fundamental) failure. */
    data class Invalid(val reason: WrprcRejection) : WrprcValidation
}

/** Why a WRPRC failed layer (b). Distinct reasons so the acceptance matrix asserts precisely. */
enum class WrprcRejection {
    /** Not parseable as a compact JWS at all. */
    Malformed,

    /** `typ` is not `rc-wrp+jwt` (or a non-JWT registration certificate reached the validator). */
    NotSupportedProfile,

    /** Missing the JAdES B-B markers (AdES alg, included signing cert, signed signing time). */
    NotJAdES,

    /** The signature did not verify against the WRPRC provider leaf. */
    BadSignature,

    /** The WRPRC provider certificate is expired or not yet valid. */
    ProviderCertInvalid,

    /** The WRPRC provider certificate does not chain to the trusted WRPRC-provider anchor. */
    ProviderUntrusted,

    /** A required policy/profile field (identity, iat, status, policy_id, credentials) is absent. */
    MissingFields,

    /** The WRPRC is expired (past `exp`) or future-dated (`iat` beyond the clock skew). */
    Expired,

    /** The WRPRC is revoked per the cached signed status list. */
    Revoked,

    /** The WRPRC's status could not be established from the local cache; fail-closed. */
    StatusUnavailable,
}
