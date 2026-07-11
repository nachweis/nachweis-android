package com.quellkern.nachweis.presentation

import java.io.InputStream
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.util.Date

/**
 * The locally cached set of demo trust anchors a verifier's access certificate (WRPAC) must
 * chain to. Anchors are loaded once from the active flavor's bundled PEM (the public
 * nachweis demo root), so certification-path building never touches the network — the
 * dev-plan.md rule that consent-time validation uses only locally cached trust artifacts.
 *
 * Path validation is standard JCA PKIX with **revocation disabled here**: certificate
 * *status* (revocation) is a separate concern handled by [RequestStatusSource] against the
 * cached status list, so the two can fail independently and be tested independently.
 */
class TrustStore(anchors: Collection<X509Certificate>) {

    private val trustAnchors: Set<TrustAnchor> = anchors.map { TrustAnchor(it, null) }.toSet()

    /** True when at least one anchor is present; an empty store trusts nothing. */
    fun isNotEmpty(): Boolean = trustAnchors.isNotEmpty()

    /**
     * Validate that [chain] (leaf first) builds a valid PKIX path to one of the anchors at
     * [at]. Returns true on success. Revocation is not checked here (see [RequestStatusSource]).
     * Any validation failure — untrusted issuer, broken chain, bad basic constraints —
     * returns false rather than throwing, so the validator maps it to a single typed error.
     */
    fun validatesPath(chain: List<X509Certificate>, at: Date): Boolean {
        if (trustAnchors.isEmpty() || chain.isEmpty()) return false
        return try {
            // A PKIX CertPath runs from the leaf up to — but not including — the trust anchor.
            // Drop self-signed roots (subject == issuer) so a chain that carries its own root,
            // like [leaf, ca], validates against the anchor supplied separately.
            val path = chain.filterNot { it.subjectX500Principal == it.issuerX500Principal }
            if (path.isEmpty()) return false
            val factory = CertificateFactory.getInstance("X.509")
            val certPath = factory.generateCertPath(path)
            val params = PKIXParameters(trustAnchors).apply {
                isRevocationEnabled = false
                date = at
            }
            java.security.cert.CertPathValidator.getInstance("PKIX").validate(certPath, params)
            true
        } catch (_: Exception) {
            // Includes CertPathValidatorException (untrusted / broken chain / expired) and
            // InvalidAlgorithmParameterException (no anchors). All mean "not a trusted path".
            false
        }
    }

    companion object {
        /**
         * Parse zero or more PEM `CERTIFICATE` blocks from [pem]. Comment lines and blank
         * lines are ignored, so a placeholder PEM with only comments yields an empty store
         * (which then trusts nothing — fail-closed).
         */
        fun fromPem(pem: String): TrustStore = TrustStore(parsePemCertificates(pem.byteInputStream()))

        /** Parse all certificates from a PEM [stream]; returns empty when none are present. */
        fun parsePemCertificates(stream: InputStream): List<X509Certificate> {
            val text = stream.bufferedReader().use { it.readText() }
            val factory = CertificateFactory.getInstance("X.509")
            // Isolate each PEM CERTIFICATE block before decoding. Android's Conscrypt provider
            // parses *zero* certificates when a block is preceded by comment lines (our anchors
            // carry a leading `#` provenance header), whereas OpenJDK skips the preamble — so
            // handing the raw stream to generateCertificates() silently yields an empty,
            // trusts-nothing anchor set on device while every JVM test passes. Feeding clean
            // blocks one at a time makes parsing identical across providers.
            return PEM_CERTIFICATE.findAll(text).mapNotNull { match ->
                try {
                    match.value.byteInputStream().use {
                        factory.generateCertificate(it) as X509Certificate
                    }
                } catch (_: CertificateException) {
                    null
                }
            }.toList()
        }

        private val PEM_CERTIFICATE = Regex(
            "-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----",
            RegexOption.DOT_MATCHES_ALL,
        )
    }
}
