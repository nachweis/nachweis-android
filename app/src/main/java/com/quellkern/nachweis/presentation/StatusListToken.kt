package com.quellkern.nachweis.presentation

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory
import com.nimbusds.jose.util.X509CertUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.security.cert.X509Certificate
import java.util.Date
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * A verified IETF Token Status List (`typ=statuslist+jwt`), the signed artifact that says which
 * WRPRCs on a given list are still valid. A WRPRC carries a `status.status_list = { uri, idx }`
 * pointer; the list at `uri` is this token, and bit `idx` in its compressed bitstring is that
 * WRPRC's status (0 = valid, non-zero = revoked). Fetched and verified **out of band** by
 * [StatusListRefresher]; consent-time lookups read only the decoded result, never the network.
 *
 * [StatusListVerifier] proves the token before its bits are ever trusted: the supported profile
 * markers, a signature chaining to the status-provider trust anchor, temporal validity, and that
 * the token's `sub` is exactly the list URI the app asked for (so one list cannot be served in
 * place of another). Only then are the bits decompressed and exposed.
 */
data class VerifiedStatusList(
    /** The list identity (`sub`); equals the URI the WRPRC's status pointer names. */
    val uri: String,
    /** Bits per status entry (`status_list.bits`); 1 for the deployed demo list. */
    val bits: Int,
    /** The decompressed status bitstring; entry [idx] is read via [statusAt]. */
    private val bitstring: ByteArray,
    /** `iat` (seconds since epoch): when the list was signed. */
    val issuedAtEpochSeconds: Long,
    /** `ttl` (seconds) caching hint, when present: bounds how long a fetched copy stays fresh. */
    val ttlSeconds: Long?,
    /** `exp` (seconds since epoch) when present: an absolute expiry for the token. */
    val expiresAtEpochSeconds: Long?,
) {
    /**
     * The status value of entry [idx], or `null` when [idx] is outside the list (which the caller
     * treats as unknown / fail-closed). Reads the `bits`-wide little-endian field at [idx], per
     * the IETF Token Status List bit layout.
     */
    fun statusAt(idx: Int): Int? {
        if (idx < 0) return null
        val entriesPerByte = 8 / bits
        val byteIndex = idx / entriesPerByte
        if (byteIndex >= bitstring.size) return null
        val within = idx % entriesPerByte
        val mask = (1 shl bits) - 1
        return (bitstring[byteIndex].toInt() ushr (within * bits)) and mask
    }
}

/** Result of [StatusListVerifier.verify]. */
sealed interface StatusListVerification {
    /** The token passed every check; [list] exposes the verified, decompressed status bits. */
    data class Valid(val list: VerifiedStatusList) : StatusListVerification

    /** The token failed a check; [reason] is the first (most fundamental) failure. */
    data class Invalid(val reason: StatusListRejection) : StatusListVerification
}

/** Why a status-list token was rejected. Distinct reasons so tests assert precisely. */
enum class StatusListRejection {
    /** Not parseable as a compact JWS. */
    Malformed,

    /** `typ` is not `statuslist+jwt`. */
    NotStatusList,

    /** The algorithm is outside the supported ECDSA AdES family. */
    UnsupportedAlg,

    /** No usable `x5c` signing certificate is present. */
    NoSigningCertificate,

    /** The signature did not verify against the signing leaf. */
    BadSignature,

    /** The signing certificate is expired / not yet valid, or does not chain to the anchor. */
    UntrustedSigner,

    /** The token's `sub` is not the list URI the app requested. */
    SubjectMismatch,

    /** The token is future-dated (`iat` beyond skew) or past its `exp`. */
    Expired,

    /** `status_list` (bits / lst) is missing or malformed, or the bitstring will not inflate. */
    MalformedStatusList,
}

/**
 * Verifies a `statuslist+jwt` against a caller-supplied [statusTrust] anchor set and clock — no
 * network, wallet-core, or Android dependency, so it is exhaustively unit-tested on the JVM. The
 * status provider is a distinct actor from the WRPRC and WRPAC providers, hence its own anchor
 * set; in the deployed demo its signing certificate chains through the WRPRC provider to the same
 * demo root, so the WRPRC provider anchor bundle validates it.
 */
class StatusListVerifier(private val statusTrust: TrustStore) {

    private val json = Json { ignoreUnknownKeys = true }
    private val verifierFactory = DefaultJWSVerifierFactory()
    private val supportedAlgs = setOf(JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512)
    private val statusListType = JOSEObjectType(STATUS_LIST_TYPE)

    /**
     * Verify [compactJws] as of [now], requiring its `sub` to equal [expectedSub] (the list URI
     * the WRPRC pointed at). Returns the verified list or the first failing check.
     */
    fun verify(compactJws: String, expectedSub: String, now: Date): StatusListVerification {
        return try {
            verifyInternal(compactJws, expectedSub, now)
        } catch (_: Throwable) {
            StatusListVerification.Invalid(StatusListRejection.Malformed)
        }
    }

    private fun verifyInternal(compactJws: String, expectedSub: String, now: Date): StatusListVerification {
        val jws = try {
            JWSObject.parse(compactJws)
        } catch (_: java.text.ParseException) {
            return StatusListVerification.Invalid(StatusListRejection.Malformed)
        }

        if (jws.header.type != statusListType) {
            return StatusListVerification.Invalid(StatusListRejection.NotStatusList)
        }
        if (jws.header.algorithm !in supportedAlgs) {
            return StatusListVerification.Invalid(StatusListRejection.UnsupportedAlg)
        }

        val chain = parseX5c(jws)
            ?: return StatusListVerification.Invalid(StatusListRejection.NoSigningCertificate)
        val leaf = chain.first()

        val verifier = try {
            verifierFactory.createJWSVerifier(jws.header, leaf.publicKey)
        } catch (_: Exception) {
            return StatusListVerification.Invalid(StatusListRejection.BadSignature)
        }
        if (!jws.verify(verifier)) {
            return StatusListVerification.Invalid(StatusListRejection.BadSignature)
        }

        try {
            leaf.checkValidity(now)
        } catch (_: java.security.cert.CertificateExpiredException) {
            return StatusListVerification.Invalid(StatusListRejection.UntrustedSigner)
        } catch (_: java.security.cert.CertificateNotYetValidException) {
            return StatusListVerification.Invalid(StatusListRejection.UntrustedSigner)
        }
        if (!statusTrust.validatesPath(chain, now)) {
            return StatusListVerification.Invalid(StatusListRejection.UntrustedSigner)
        }

        val payload = try {
            json.parseToJsonElement(jws.payload.toString()).let { it as? JsonObject }
        } catch (_: Exception) {
            null
        } ?: return StatusListVerification.Invalid(StatusListRejection.MalformedStatusList)

        val sub = payload["sub"]?.jsonPrimitive?.contentOrNull
        if (sub == null || sub != expectedSub) {
            return StatusListVerification.Invalid(StatusListRejection.SubjectMismatch)
        }

        val iat = payload["iat"]?.jsonPrimitive?.longOrNull
            ?: return StatusListVerification.Invalid(StatusListRejection.MalformedStatusList)
        val ttl = payload["ttl"]?.jsonPrimitive?.longOrNull
        val exp = payload["exp"]?.jsonPrimitive?.longOrNull

        val nowSeconds = now.time / 1000
        if (iat > nowSeconds + CLOCK_SKEW_SECONDS) {
            return StatusListVerification.Invalid(StatusListRejection.Expired)
        }
        if (exp != null && nowSeconds > exp) {
            return StatusListVerification.Invalid(StatusListRejection.Expired)
        }

        val statusList = payload["status_list"] as? JsonObject
            ?: return StatusListVerification.Invalid(StatusListRejection.MalformedStatusList)
        val bits = statusList["bits"]?.jsonPrimitive?.intOrNull
            ?: return StatusListVerification.Invalid(StatusListRejection.MalformedStatusList)
        if (bits !in setOf(1, 2, 4, 8)) {
            return StatusListVerification.Invalid(StatusListRejection.MalformedStatusList)
        }
        val lst = statusList["lst"]?.jsonPrimitive?.contentOrNull
            ?: return StatusListVerification.Invalid(StatusListRejection.MalformedStatusList)
        val bytes = inflate(lst)
            ?: return StatusListVerification.Invalid(StatusListRejection.MalformedStatusList)

        return StatusListVerification.Valid(
            VerifiedStatusList(
                uri = sub,
                bits = bits,
                bitstring = bytes,
                issuedAtEpochSeconds = iat,
                ttlSeconds = ttl,
                expiresAtEpochSeconds = exp,
            )
        )
    }

    private fun parseX5c(jws: JWSObject): List<X509Certificate>? {
        val encoded = jws.header.x509CertChain ?: return null
        if (encoded.isEmpty()) return null
        val chain = encoded.mapNotNull { X509CertUtils.parse(it.decode()) }
        return chain.takeIf { it.size == encoded.size && it.isNotEmpty() }
    }

    /** Decode the base64url `lst` and DEFLATE-inflate it (zlib wrapper), per the IETF spec. */
    private fun inflate(lstBase64Url: String): ByteArray? {
        val compressed = try {
            java.util.Base64.getUrlDecoder().decode(lstBase64Url)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val inflater = Inflater()
        return try {
            inflater.setInput(compressed)
            val out = java.io.ByteArrayOutputStream(compressed.size * 4)
            val buffer = ByteArray(1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                }
                out.write(buffer, 0, n)
            }
            out.toByteArray().takeIf { it.isNotEmpty() }
        } catch (_: DataFormatException) {
            null
        } finally {
            inflater.end()
        }
    }

    private companion object {
        const val STATUS_LIST_TYPE = "statuslist+jwt"

        /** Tolerance for a token signed moments in the future relative to the local clock. */
        const val CLOCK_SKEW_SECONDS = 60L
    }
}
