package com.quellkern.nachweis.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Regression over the *real deployed* WRPAC request chain and the comment-prefixed anchor PEM.
 * B5's fixtures used a locally minted CA, so this deployed chain (leaf -> wrpac-provider ->
 * demo root) and the leading-`#` provenance header on the bundled anchor were never exercised
 * through [TrustStore] until the live presentation test surfaced an on-device trust rejection:
 * Android's Conscrypt parses zero certs from a comment-prefixed PEM. The parser now isolates
 * PEM blocks; this locks that contract and the deployed-chain validation on the JVM side.
 */
class DeployedWrpacTrustDiagnosticTest {

    private fun load(name: String): X509Certificate =
        javaClass.getResourceAsStream("/trust/deployed/$name.pem").use { input ->
            CertificateFactory.getInstance("X.509").generateCertificate(input) as X509Certificate
        }

    // Well within the deployed leaf window (notBefore 2026-07-10, notAfter 2027-07-10).
    private val at = Date(1788307200000L) // 2026-09-01T00:00:00Z

    @Test
    fun commentPrefixedAnchorPemParsesToOneCertificate() {
        val pem = """
            # Public root for the self-managed nachweis demo trust bundle.
            # Private CA and provider keys remain offline.
            ${javaClass.getResourceAsStream("/trust/deployed/demo-root.pem")!!.bufferedReader().readText().trim()}
        """.trimIndent()
        val certs = TrustStore.parsePemCertificates(pem.byteInputStream())
        assertEquals("a leading comment header must not hide the certificate", 1, certs.size)
    }

    @Test
    fun deployedChainValidatesToBundledAnchor() {
        val leaf = load("wrpac-leaf")
        val provider = load("wrpac-provider")
        val root = load("demo-root")
        assertTrue(
            "deployed WRPAC chain must build a PKIX path to the demo root",
            TrustStore(listOf(root)).validatesPath(listOf(leaf, provider, root), at),
        )
    }
}
