package com.quellkern.nachweis.presentation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The parsed payload of a Wallet-Relying-Party Registration Certificate (WRPRC), the net-new
 * artifact D1 verifies (dev-plan.md Workstream D). A WRPRC is what a registrar issued to a
 * verifier: it states which credentials and claims that verifier is *registered* to request.
 * D1 compares it against what the live request actually asks for and labels anything beyond it
 * as *"outside this verifier's registration"*.
 *
 * Field derivation [assumed — resolved from the V1.2.1 structure]: the official ETSI TS 119 475
 * V1.2.1 PDF is not in the repo, and the local schema clone (v1.1.1 + an aspirational draft) is
 * fixtures, not authority (dev-plan.md compatibility matrix). This parser accepts both shapes so
 * a V1.2.1 fixture between them still reads: the WRP identity container is `wrp` (aspirational)
 * or `sub` (v1.1.1); registered claims are `credentials[].claims[].path` as an array
 * (aspirational, DCQL-aligned) or `credentials[].claim[].path` as a string (v1.1.1). The
 * binding-field derivation is documented in docs/registration-verdict.md.
 */
data class Wrprc(
    /** WRP legal identifiers (e.g. `LEIXG-…`) from `wrp.id[]` / `sub.id[]`; the binding key. */
    val wrpIdentifiers: List<String>,
    /** Registered credentials: the verifier's registered scope, matched against the request. */
    val registeredCredentials: List<RegisteredCredential>,
    /** `iat` (seconds since epoch); required. */
    val issuedAtEpochSeconds: Long,
    /** `exp` (seconds since epoch) when present; a WRPRC may rely on the status list instead. */
    val expiresAtEpochSeconds: Long?,
    /** The status-list pointer (`status.status_list`); required, checked against the cache. */
    val statusRef: WrprcStatusRef,
    /** `policy_id` values (clause 6.1.3); required to be present and non-empty. */
    val policyIds: List<String>,
)

/** One registered credential in a WRPRC: the credential type and the claim paths registered. */
data class RegisteredCredential(
    /** The registered credential type (SD-JWT `vct`). */
    val vct: String,
    /** Registered claim paths, dotted (e.g. `given_name`, `address.locality`). */
    val claimPaths: Set<String>,
)

/** Outcome of pulling a WRPRC out of a request's `verifier_info` array. */
sealed interface VerifierInfoResult {
    /** A JWT WRPRC (`typ=rc-wrp+jwt`) was found; [compactJws] is its compact serialization. */
    data class JwtWrprc(val compactJws: String) : VerifierInfoResult

    /**
     * A WRPRC was found but in an unsupported encoding — a CWT/CBOR registration certificate
     * (`rc-wrp+cwt`). V1.2.1 permits it; this app supports only the JWT profile, so it is
     * rejected rather than parsed (dev-plan.md D1: the profile is asserted, not inferred).
     */
    data object UnsupportedEncoding : VerifierInfoResult

    /** No registration certificate is present in `verifier_info`. */
    data object Absent : VerifierInfoResult
}

/**
 * Parses a WRPRC out of the signed request's `verifier_info` (dev-plan.md D1: the WRPRC is
 * extracted from the request, never fetched during consent) and parses its JWT payload.
 * Structural only — signature, chain, status, and binding are [WrprcValidator]'s job.
 */
object WrprcExtractor {

    private val json = Json { ignoreUnknownKeys = true }

    /** The `format` discriminator for a JWT WRPRC entry in `verifier_info`. */
    const val JWT_FORMAT: String = "rc-wrp+jwt"

    /** The `format` discriminator for a CWT/CBOR WRPRC entry (recognised, then rejected). */
    const val CWT_FORMAT: String = "rc-wrp+cwt"

    /**
     * The OpenID4VP `verifier_info` entry `format` under which the deployed EUDI verifiers (and
     * augenmass) carry a relying-party registration certificate. The JWT-vs-CWT profile is decided
     * by the certificate encoding in `data`, not this outer format — so a compact JWS is the
     * supported JAdES profile and anything else (CBOR/CWT) is rejected here, with the JWT `typ`
     * re-checked by [WrprcValidator].
     */
    const val REGISTRATION_CERT_FORMAT: String = "registration_cert"

    private val COMPACT_JWS = Regex("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$")

    /**
     * Find the registration certificate in [verifierInfoJson] (the raw `verifier_info` array as
     * a JSON string, as carried by [ValidatedPresentationRequest.verifierInfo]). Each entry is
     * `{ "format": …, "data": … }`; the first registration entry decides the outcome, so a verifier
     * cannot smuggle an unsupported CWT past the check by pairing it with a later JWT.
     *
     * A registration entry is recognised by the OpenID4VP [REGISTRATION_CERT_FORMAT], or the
     * legacy [JWT_FORMAT] the profile once assumed. Its `data` is the supported JWT WRPRC only when
     * it is a compact JWS; a CBOR/CWT encoding (or the explicit [CWT_FORMAT]) is rejected.
     */
    fun extract(verifierInfoJson: String?): VerifierInfoResult {
        if (verifierInfoJson.isNullOrBlank()) return VerifierInfoResult.Absent
        val array = try {
            json.parseToJsonElement(verifierInfoJson) as? JsonArray ?: return VerifierInfoResult.Absent
        } catch (_: Exception) {
            return VerifierInfoResult.Absent
        }
        for (element in array) {
            val entry = element as? JsonObject ?: continue
            val format = entry["format"]?.jsonPrimitive?.contentOrNull ?: continue
            when {
                format.equals(REGISTRATION_CERT_FORMAT, ignoreCase = true) ||
                    format.equals(JWT_FORMAT, ignoreCase = true) -> {
                    val data = entry["data"]?.jsonPrimitive?.contentOrNull
                        ?: return VerifierInfoResult.UnsupportedEncoding
                    return if (COMPACT_JWS.matches(data.trim())) {
                        VerifierInfoResult.JwtWrprc(data.trim())
                    } else {
                        VerifierInfoResult.UnsupportedEncoding
                    }
                }
                format.startsWith("rc-wrp", ignoreCase = true) -> return VerifierInfoResult.UnsupportedEncoding
            }
        }
        return VerifierInfoResult.Absent
    }

    /**
     * Parse a WRPRC JWT [payloadJson] (already signature-verified by the caller) into [Wrprc],
     * or null when a required policy/profile field is absent or malformed. Accepts both the
     * `wrp`/`sub` identity containers and both claim shapes (see [Wrprc]).
     */
    fun parsePayload(payloadJson: String): Wrprc? {
        val payload = try {
            json.parseToJsonElement(payloadJson).jsonObject
        } catch (_: Exception) {
            return null
        }

        val identifiers = wrpIdentifiers(payload)
        if (identifiers.isEmpty()) return null

        val iat = payload["iat"]?.jsonPrimitive?.longOrNull ?: return null
        val exp = payload["exp"]?.jsonPrimitive?.longOrNull

        val statusRef = statusRef(payload) ?: return null

        val policyIds = policyIds(payload)
        if (policyIds.isEmpty()) return null

        val credentials = registeredCredentials(payload) ?: return null

        return Wrprc(
            wrpIdentifiers = identifiers,
            registeredCredentials = credentials,
            issuedAtEpochSeconds = iat,
            expiresAtEpochSeconds = exp,
            statusRef = statusRef,
            policyIds = policyIds,
        )
    }

    /**
     * Collect WRP identifiers. Three accepted shapes so a V1.2.1 fixture reads regardless of the
     * exact serialization the registrar chose:
     * - `sub` as a plain string: the WRP legal identifier directly (the deployed V1.2.1 shape,
     *   whose binding field is `sub` per the sandbox trust manifest);
     * - `wrp.id[].identifier` (aspirational, DCQL-aligned object container);
     * - `sub.id[].identifier` (v1.1.1 object container).
     */
    private fun wrpIdentifiers(payload: JsonObject): List<String> {
        (payload["sub"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return listOf(it) }
        val container = (payload["wrp"] as? JsonObject) ?: (payload["sub"] as? JsonObject) ?: return emptyList()
        val ids = container["id"] as? JsonArray ?: return emptyList()
        return ids.mapNotNull { (it as? JsonObject)?.get("identifier")?.jsonPrimitive?.contentOrNull }
            .filter { it.isNotBlank() }
    }

    private fun statusRef(payload: JsonObject): WrprcStatusRef? {
        val statusList = (payload["status"] as? JsonObject)?.get("status_list") as? JsonObject ?: return null
        val uri = statusList["uri"]?.jsonPrimitive?.contentOrNull ?: return null
        val idx = statusList["idx"]?.jsonPrimitive?.intOrNull ?: return null
        return WrprcStatusRef(uri, idx)
    }

    /** `policy_id` may be a single string or an array of strings. */
    private fun policyIds(payload: JsonObject): List<String> {
        return when (val element = payload["policy_id"]) {
            is JsonArray -> element.mapNotNull { it.jsonPrimitive.contentOrNull }
            null -> emptyList()
            else -> listOfNotNull(element.jsonPrimitive.contentOrNull)
        }.filter { it.isNotBlank() }
    }

    private fun registeredCredentials(payload: JsonObject): List<RegisteredCredential>? {
        val credentials = payload["credentials"] as? JsonArray ?: return null
        val parsed = ArrayList<RegisteredCredential>(credentials.size)
        for (element in credentials) {
            val credential = element as? JsonObject ?: return null
            val vct = credentialVct(credential) ?: return null
            val claims = credentialClaimPaths(credential)
            parsed.add(RegisteredCredential(vct, claims))
        }
        return parsed
    }

    /** vct from `meta.vct_values[0]` (aspirational) or `meta` as a plain string (v1.1.1). */
    private fun credentialVct(credential: JsonObject): String? {
        when (val meta = credential["meta"]) {
            is JsonObject -> {
                val values = meta["vct_values"] as? JsonArray ?: return null
                return values.firstOrNull()?.jsonPrimitive?.contentOrNull
            }
            null -> return null
            else -> return meta.jsonPrimitive.contentOrNull
        }
    }

    /** Claim paths from `claims[].path` (array segments) or `claim[].path` (dotted string). */
    private fun credentialClaimPaths(credential: JsonObject): Set<String> {
        val claims = (credential["claims"] as? JsonArray) ?: (credential["claim"] as? JsonArray) ?: return emptySet()
        val paths = LinkedHashSet<String>()
        for (element in claims) {
            val claim = element as? JsonObject ?: continue
            when (val path = claim["path"]) {
                is JsonArray -> {
                    val segments = path.mapNotNull { it.jsonPrimitive.contentOrNull }
                    if (segments.size == path.size && segments.isNotEmpty()) paths.add(segments.joinToString("."))
                }
                else -> path?.jsonPrimitive?.contentOrNull?.let { paths.add(it) }
            }
        }
        return paths
    }
}
