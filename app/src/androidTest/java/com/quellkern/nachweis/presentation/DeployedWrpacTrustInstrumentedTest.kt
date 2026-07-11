package com.quellkern.nachweis.presentation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date

/**
 * On-device guard for the trust-anchor parsing bug the live presentation test surfaced. The
 * bundled anchor PEMs carry a leading `#` provenance header; Android's Conscrypt provider parses
 * *zero* certificates from a comment-prefixed stream (OpenJDK skips the preamble), so every trust
 * store silently loaded empty and trusted nothing while the JVM suite stayed green. These assert
 * that the real bundled anchors load on device and that the deployed WRPAC chain then validates.
 */
@RunWith(AndroidJUnit4::class)
class DeployedWrpacTrustInstrumentedTest {

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val at = Date(1788307200000L) // 2026-09-01, within the deployed leaf window

    private fun asset(name: String): X509Certificate =
        testContext.assets.open("trust/deployed/$name.pem").use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }

    private fun bundledAnchors(resourceName: String): List<X509Certificate> {
        val resId = appContext.resources.getIdentifier(resourceName, "raw", appContext.packageName)
        assertTrue("$resourceName must resolve to a raw resource", resId != 0)
        return appContext.resources.openRawResource(resId)
            .use { TrustStore.parsePemCertificates(it) }
    }

    @Test
    fun bundledDemoAnchorsParseOnDevice() {
        assertTrue(
            "demo WRPAC anchor must parse to >=1 cert on device (Conscrypt comment-prefix bug)",
            bundledAnchors("demo_trust_anchors").isNotEmpty(),
        )
        assertTrue(
            "demo WRPRC-provider anchor must parse to >=1 cert on device",
            bundledAnchors("demo_wrprc_trust_anchors").isNotEmpty(),
        )
    }

    @Test
    fun deployedChainValidatesAgainstBundledAnchorOnDevice() {
        val leaf = asset("wrpac-leaf")
        val provider = asset("wrpac-provider")
        val root = asset("demo-root")
        val store = TrustStore(bundledAnchors("demo_trust_anchors"))
        assertTrue(
            "deployed WRPAC chain must build a PKIX path to the bundled demo root on device",
            store.validatesPath(listOf(leaf, provider, root), at),
        )
    }
}
