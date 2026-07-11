package com.quellkern.nachweis.presentation

import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory
import com.nimbusds.jose.util.X509CertUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date

/**
 * Validates a signed OpenID4VP request (the "JAR") for the access/request layer of
 * dev-plan.md D1 — layer (a): JAR signature, WRPAC certification path, WRPAC status, and
 * client-id binding. Every check runs against **locally cached** trust ([TrustStore]) and
 * status ([RequestStatusSource]) artifacts and a caller-supplied clock; the validator holds
 * no network, wallet-core, or Android dependency, so it is exhaustively unit-tested on the
 * JVM and — by construction, not by convention — makes zero network calls during consent.
 *
 * It deliberately does **not** rely on wallet-core's OpenID4VP resolution to have enforced
 * any of this: dev-plan.md notes wallet-core's `ReaderAuthPolicy.EnforceIfPresent` accepts an
 * absent reader auth, so a remote request's authenticity is a separate requirement, asserted
 * here rather than inferred from the library.
 *
 * The WRPRC layer (b) and the WRP-identifier binding (c) are **out of scope for B5**: the
 * validated request carries [ValidatedPresentationRequest.verifierInfo] verbatim so D1 can
 * parse the WRPRC from it, and the verdict is always [RegistrationVerdict.NotEvaluated] here.
 */
class PresentationRequestValidator(
    private val trustStore: TrustStore,
    private val statusSource: RequestStatusSource,
    private val supportedVct: String = SUPPORTED_VCT,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val verifierFactory = DefaultJWSVerifierFactory()

    /**
     * Validate [request] as of [now]. Returns [PresentationValidation.Valid] with the parsed
     * request on success, or [PresentationValidation.Invalid] with the first failing check.
     * Never throws for request-shaped input; unexpected internal faults map to
     * [PresentationError.Unexpected].
     */
    fun validate(request: SignedPresentationRequest, now: Date): PresentationValidation {
        return try {
            validateInternal(request, now)
        } catch (t: Throwable) {
            PresentationValidation.Invalid(PresentationError.Unexpected(t))
        }
    }

    private fun validateInternal(request: SignedPresentationRequest, now: Date): PresentationValidation {
        // (1) The request must be a signed request object (JWS compact), not plain or `none`.
        val jws = try {
            JWSObject.parse(request.compactJws)
        } catch (_: java.text.ParseException) {
            return invalid(PresentationError.NotSigned)
        }
        if (jws.header.algorithm == null || jws.header.algorithm.name.equals("none", ignoreCase = true)) {
            return invalid(PresentationError.NotSigned)
        }

        // (2) A certificate chain must accompany the signature so the signer is identifiable.
        val chain = parseX5c(jws) ?: return invalid(PresentationError.NoCertificate)
        val leaf = chain.first()

        // (3) The signature must verify against the leaf's public key.
        val verifier = try {
            verifierFactory.createJWSVerifier(jws.header, leaf.publicKey)
        } catch (_: Exception) {
            return invalid(PresentationError.BadSignature)
        }
        if (!jws.verify(verifier)) return invalid(PresentationError.BadSignature)

        // (4) The leaf must be temporally valid now (explicit, for a precise error)...
        try {
            leaf.checkValidity(now)
        } catch (_: java.security.cert.CertificateExpiredException) {
            return invalid(PresentationError.CertificateExpired)
        } catch (_: java.security.cert.CertificateNotYetValidException) {
            return invalid(PresentationError.CertificateExpired)
        }

        // (5) ...and chain to a trusted local anchor (revocation handled separately, below).
        if (!trustStore.validatesPath(chain, now)) return invalid(PresentationError.Untrusted)

        // (6) The WRPAC must not be revoked; unknown status fails closed.
        when (statusSource.statusOf(leaf)) {
            CertStatus.Revoked -> return invalid(PresentationError.Revoked)
            CertStatus.Unknown -> return invalid(PresentationError.StatusUnavailable)
            CertStatus.Good -> Unit
        }

        // (7) Parse the signed payload; every displayed field comes from here, not the URL.
        val payload = try {
            json.parseToJsonElement(jws.payload.toString()).jsonObject
        } catch (_: Exception) {
            return invalid(PresentationError.Unreadable)
        }

        // (8) client_id must bind to the leaf (x509_san_dns / x509_hash).
        val binding = bindClientId(payload, leaf) ?: return invalid(PresentationError.ClientIdMismatch)

        val responseUri = payload.string("response_uri") ?: payload.string("redirect_uri")
            ?: return invalid(PresentationError.Unreadable)
        val nonce = payload.string("nonce") ?: return invalid(PresentationError.Unreadable)

        // (9) The DCQL query must be within the supported subset and name the supported vct.
        val dcqlObject = (payload["dcql_query"] as? JsonObject) ?: return invalid(PresentationError.UnsupportedQuery)
        val dcql = Dcql.parse(dcqlObject)
        if (dcql !is DcqlResult.Supported) return invalid(PresentationError.UnsupportedQuery)
        if (dcql.vct != supportedVct) return invalid(PresentationError.UnsupportedCredential)

        val verifierInfo = (payload["verifier_info"] as? JsonArray)?.toString()

        return PresentationValidation.Valid(
            ValidatedPresentationRequest(
                verifierIdentity = binding,
                responseOrigin = originOf(responseUri) ?: responseUri,
                responseUri = responseUri,
                purpose = payload.string("purpose"),
                vct = dcql.vct,
                requestedClaims = dcql.claims,
                nonce = nonce,
                verifierInfo = verifierInfo,
                // Read-only extraction of the WRPAC's WRP identifier so D1 can bind the WRPRC
                // to it without re-parsing the certificate. Not part of the access decision.
                verifierWrpIdentifiers = WrpBinding.wrpacIdentifiers(leaf),
                registrationVerdict = RegistrationVerdict.NotEvaluated,
            ),
        )
    }

    /** Decode the header `x5c` into an ordered chain (leaf first), or null when absent/empty. */
    private fun parseX5c(jws: JWSObject): List<X509Certificate>? {
        val encoded = jws.header.x509CertChain ?: return null
        if (encoded.isEmpty()) return null
        val chain = encoded.mapNotNull { X509CertUtils.parse(it.decode()) }
        return chain.takeIf { it.size == encoded.size && it.isNotEmpty() }
    }

    /**
     * Verify the request's `client_id` is cryptographically bound to [leaf] and return the
     * verifier identity to display, or null when it does not bind. Supports both the prefixed
     * form (`x509_san_dns:host`, `x509_hash:b64url`) and a separate `client_id_scheme` field.
     */
    private fun bindClientId(payload: JsonObject, leaf: X509Certificate): String? {
        val clientId = payload.string("client_id") ?: return null
        val explicitScheme = payload.string("client_id_scheme")

        val (scheme, value) = when {
            clientId.startsWith("x509_san_dns:") -> "x509_san_dns" to clientId.removePrefix("x509_san_dns:")
            clientId.startsWith("x509_hash:") -> "x509_hash" to clientId.removePrefix("x509_hash:")
            explicitScheme != null -> explicitScheme to clientId
            else -> return null
        }

        return when (scheme) {
            "x509_san_dns" -> if (dnsNames(leaf).any { it.equals(value, ignoreCase = true) }) value else null
            "x509_hash" -> if (constantTimeEquals(sha256Base64Url(leaf.encoded), value)) subjectCommonName(leaf) ?: value else null
            else -> null
        }
    }

    /** dNSName entries from the leaf's subjectAltName extension. */
    private fun dnsNames(leaf: X509Certificate): List<String> =
        leaf.subjectAlternativeNames.orEmpty()
            .filter { it.size >= 2 && it[0] == 2 } // GeneralName type 2 = dNSName
            .mapNotNull { it[1] as? String }

    private fun subjectCommonName(leaf: X509Certificate): String? {
        val dn = leaf.subjectX500Principal.name
        return Regex("CN=([^,]+)").find(dn)?.groupValues?.get(1)
    }

    private fun sha256Base64Url(der: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(der))

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    private fun JsonObject.string(key: String): String? =
        (this[key])?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

    private fun originOf(url: String): String? = try {
        val uri = java.net.URI(url)
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val port = uri.port
        if (port == -1) "$scheme://$host" else "$scheme://$host:$port"
    } catch (_: Exception) {
        null
    }

    private fun invalid(error: PresentationError): PresentationValidation.Invalid =
        PresentationValidation.Invalid(error)

    companion object {
        /** The one SD-JWT PID vct this app presents (unpatched; matches augenmass DCQL). */
        const val SUPPORTED_VCT: String = "urn:eudi:pid:de:1"
    }
}

/** Result of [PresentationRequestValidator.validate]. */
sealed interface PresentationValidation {
    /** The request passed the access/request layer; carries the request to consent on. */
    data class Valid(val request: ValidatedPresentationRequest) : PresentationValidation

    /** The request failed a check; [error] is the first (most fundamental) failure. */
    data class Invalid(val error: PresentationError) : PresentationValidation
}
