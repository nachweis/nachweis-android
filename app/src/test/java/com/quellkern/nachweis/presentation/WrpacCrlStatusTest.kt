package com.quellkern.nachweis.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Covers the out-of-band WRPAC CRL refresh against the real deployed, signed CRL committed as a
 * public fixture. The live presentation surfaced that the access-cert revocation source was an
 * empty [CachedStatusSource] (every WRPAC lookup Unknown → fail closed); this locks the refresh
 * that populates it and its fail-closed edges.
 */
class WrpacCrlStatusTest {

    private val nowMs = 1784000000000L // 2026-07-12, within the CRL [thisUpdate, nextUpdate] window
    private val clock = { nowMs }

    private fun cert(name: String): X509Certificate =
        javaClass.getResourceAsStream("/trust/deployed/$name.pem").use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }

    private fun crlBytes(): ByteArray =
        javaClass.getResourceAsStream("/trust/deployed/wrpac-provider.crl.der").use { it.readBytes() }

    private val leafGood = cert("wrpac-leaf") // serial 1000, not listed
    private val leafRevoked = cert("wrpac-leaf-revoked") // serial 1003, listed
    private val issuer = cert("wrpac-provider")
    private val root = cert("demo-root")

    private fun refresh(
        fetcher: CrlFetcher,
        anchors: List<X509Certificate> = listOf(root),
    ): Pair<Boolean, SwappableRequestStatusSource> {
        val target = SwappableRequestStatusSource()
        val ok = WrpacCrlRefresher(
            fetcher = fetcher,
            crlUri = "https://verifier-sandbox.nachweis.tech/trust/wrpac/wrpac-provider.crl.der",
            issuerCert = issuer,
            trustStore = TrustStore(anchors),
            target = target,
            clock = clock,
        ).refresh(Date(nowMs))
        return ok to target
    }

    @Test
    fun verifiedCrlResolvesGoodAndRevoked() {
        val (ok, source) = refresh(fetcher = { crlBytes() })
        assertTrue("the real signed CRL must verify and publish", ok)
        assertEquals(CertStatus.Good, source.statusOf(leafGood))
        assertEquals(CertStatus.Revoked, source.statusOf(leafRevoked))
    }

    @Test
    fun fetchFailureLeavesSourceFailClosed() {
        val (ok, source) = refresh(fetcher = { null })
        assertFalse(ok)
        assertEquals(CertStatus.Unknown, source.statusOf(leafGood))
    }

    @Test
    fun untrustedIssuerIsRejected() {
        // Empty anchor set → the issuer cert does not chain → the CRL it signs is refused.
        val (ok, source) = refresh(fetcher = { crlBytes() }, anchors = emptyList())
        assertFalse(ok)
        assertEquals(CertStatus.Unknown, source.statusOf(leafGood))
    }

    @Test
    fun staleCrlFailsClosed() {
        val crl = CertificateFactory.getInstance("X.509")
            .generateCRL(crlBytes().inputStream()) as X509CRL
        val stale = WrpacCrlStatusSource(crl, freshUntilEpochMillis = nowMs - 1, clock = clock)
        assertEquals(CertStatus.Unknown, stale.statusOf(leafGood))
    }
}
