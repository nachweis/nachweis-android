package com.quellkern.nachweis.presentation

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses a DCQL query into the **constrained subset** the wallet supports (dev-plan.md D1):
 * exactly one SD-JWT credential with a simple claim set. Anything outside that subset —
 * `credential_sets`, multiple credentials, `claim_sets`, `multiple`, per-claim value
 * restrictions, or a non-SD-JWT format — is reported as [DcqlResult.Unsupported] rather than
 * being narrowed or unioned. The caller refuses such a request; the user is never asked to
 * disclose against a query the app did not fully model.
 */
object Dcql {

    /** SD-JWT VC formats a request may use for the PID (both spellings seen in the wild). */
    private val SD_JWT_FORMATS = setOf("dc+sd-jwt", "vc+sd-jwt")

    /**
     * Parse the `dcql_query` [query] object. Returns the single requested credential's vct
     * and claim paths, or [DcqlResult.Unsupported] with a short reason (for logs/tests only).
     */
    fun parse(query: JsonObject): DcqlResult {
        // credential_sets add cross-credential choice/optionality: outside the subset.
        if (query.containsKey("credential_sets")) return unsupported("credential_sets present")

        val credentials = (query["credentials"] as? JsonArray) ?: return unsupported("no credentials array")
        if (credentials.size != 1) return unsupported("expected exactly one credential, got ${credentials.size}")

        val credential = credentials.first() as? JsonObject ?: return unsupported("credential is not an object")

        val format = credential["format"]?.jsonPrimitive?.contentOrNull
        if (format == null || format !in SD_JWT_FORMATS) return unsupported("unsupported format: $format")

        // `multiple: true` asks for several matching instances: outside the subset.
        if (credential["multiple"]?.jsonPrimitive?.booleanOrNull == true) return unsupported("multiple=true")

        // claim_sets express alternative claim combinations: outside the subset.
        if (credential.containsKey("claim_sets")) return unsupported("claim_sets present")

        val vct = extractVct(credential) ?: return unsupported("no vct in meta.vct_values")

        val claimsArray = (credential["claims"] as? JsonArray) ?: return unsupported("no claims array")
        if (claimsArray.isEmpty()) return unsupported("empty claims array")

        val claims = ArrayList<RequestedClaim>(claimsArray.size)
        for (element in claimsArray) {
            val claim = element as? JsonObject ?: return unsupported("claim is not an object")
            // A per-claim `values` restriction constrains the disclosed value: outside the subset.
            if (claim.containsKey("values")) return unsupported("per-claim value restriction present")
            val path = extractPath(claim) ?: return unsupported("claim has no simple string path")
            claims.add(RequestedClaim(path))
        }

        return DcqlResult.Supported(vct = vct, claims = claims)
    }

    /** The vct is required and taken from `meta.vct_values` (exactly one supported value). */
    private fun extractVct(credential: JsonObject): String? {
        val meta = credential["meta"] as? JsonObject ?: return null
        val values = meta["vct_values"] as? JsonArray ?: return null
        if (values.size != 1) return null
        return values.first().jsonPrimitive.contentOrNull
    }

    /**
     * A supported claim path is a non-empty array of string segments (`["address","locality"]`).
     * A path element that is null (SD-JWT array wildcard) or an integer index is outside the
     * simple subset and yields null here, which the caller maps to Unsupported.
     */
    private fun extractPath(claim: JsonObject): String? {
        val path = claim["path"] as? JsonArray ?: return null
        if (path.isEmpty()) return null
        val segments = ArrayList<String>(path.size)
        for (element in path) {
            val primitive = element as? JsonPrimitive ?: return null
            if (!primitive.isString) return null
            segments.add(primitive.contentOrNull ?: return null)
        }
        return segments.joinToString(".")
    }

    private fun unsupported(reason: String): DcqlResult.Unsupported = DcqlResult.Unsupported(reason)
}

/** Outcome of parsing a DCQL query against the supported subset. */
sealed interface DcqlResult {
    /** The query is within the subset: one SD-JWT credential and its simple claim paths. */
    data class Supported(val vct: String, val claims: List<RequestedClaim>) : DcqlResult

    /** The query is outside the subset; [reason] is for logs and tests, never for the user. */
    data class Unsupported(val reason: String) : DcqlResult
}
