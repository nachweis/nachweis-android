package com.quellkern.nachweis.presentation

import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

/**
 * The WRP-identifier binding between a WRPAC and a WRPRC — dev-plan.md D1 layer (c). ETSI
 * TS 119 475 V1.2.1 binds the access certificate (WRPAC) and the registration certificate
 * (WRPRC) through the **WRP identifier**: at least one interoperable identifier must match for
 * the pair to be considered the same Wallet-Relying Party.
 *
 * Binding-field derivation [assumed — resolved from the V1.2.1 structure; the official PDF is
 * not in the repo, the local clone is fixtures-only per dev-plan.md]:
 *
 * - **WRPRC side:** the WRP legal identifiers in `wrp.id[].identifier` / `sub.id[].identifier`
 *   (e.g. an LEI `LEIXG-743700EB2QF2J1WRO915`), parsed by [WrprcExtractor] into
 *   [Wrprc.wrpIdentifiers].
 * - **WRPAC side:** the subject **organizationIdentifier** attribute, OID `2.5.4.97`
 *   (ETSI EN 319 412-1), which encodes the same semantic legal identifier of the certificate
 *   subject.
 *
 * The match is by that **standardized identity string**, not by certificate instance — so a
 * different WRPAC leaf (different key, different serial) carrying the same organizationIdentifier
 * still binds (dev-plan.md: "binding is by identity, not instance"), while an unrelated
 * identifier fails. Absence on either side fails closed.
 */
object WrpBinding {

    /** OID of the X.509 subject organizationIdentifier attribute (ETSI EN 319 412-1). */
    private const val ORGANIZATION_IDENTIFIER_OID = "2.5.4.97"

    // Alias the OID to a keyword so X500Principal renders it as `ORGID=value` (a readable
    // string) instead of the default `2.5.4.97=#<DER-hex>` form, which would need hex decoding.
    private val oidKeywordMap = mapOf(ORGANIZATION_IDENTIFIER_OID to "ORGID")
    private val orgIdRegex = Regex("""(?:^|,)ORGID=([^,]+)""")

    /**
     * True when [wrpac]'s organizationIdentifier matches at least one of [wrprc]'s WRP
     * identifiers. Case-insensitive exact match on the full identifier string.
     */
    fun isBound(wrpac: X509Certificate, wrprc: Wrprc): Boolean {
        val access = wrpacIdentifiers(wrpac)
        if (access.isEmpty() || wrprc.wrpIdentifiers.isEmpty()) return false
        return access.any { a -> wrprc.wrpIdentifiers.any { it.equals(a, ignoreCase = true) } }
    }

    /**
     * The WRP identifier(s) carried by [wrpac]'s subject organizationIdentifier. Threaded onto
     * [ValidatedPresentationRequest.verifierWrpIdentifiers] by the access-layer validator so the
     * registration evaluator can bind without re-parsing the certificate.
     */
    fun wrpacIdentifiers(wrpac: X509Certificate): List<String> {
        val rendered = try {
            wrpac.subjectX500Principal.getName(X500Principal.RFC2253, oidKeywordMap)
        } catch (_: Exception) {
            return emptyList()
        }
        return orgIdRegex.findAll(rendered)
            .map { unescapeRfc2253(it.groupValues[1]).trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    /** Undo RFC2253 backslash escaping (organizationIdentifier values are normally plain). */
    private fun unescapeRfc2253(value: String): String {
        if ('\\' !in value) return value
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                out.append(value[i + 1]); i += 2
            } else {
                out.append(c); i++
            }
        }
        return out.toString()
    }
}
