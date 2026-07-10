package com.quellkern.nachweis

import com.quellkern.nachweis.config.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/** Exact configuration expected of the demo flavor (canonical sandbox hosts). */
class DemoConfigTest {

    @Test
    fun demo_applicationId() {
        assertEquals("com.quellkern.nachweis.demo", AppConfig.applicationId)
    }

    @Test
    fun demo_endpoints() {
        assertEquals("https://api-sandbox.nachweis.tech", AppConfig.issuerBaseUrl)
        assertEquals("https://verifier-sandbox.nachweis.tech", AppConfig.verifierBaseUrl)
    }

    @Test
    fun demo_trustAnchors() {
        assertEquals("demo_trust_anchors", AppConfig.trustAnchorsResourceName)
    }
}
