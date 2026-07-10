package com.quellkern.nachweis

import com.quellkern.nachweis.config.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/** Exact configuration expected of the production flavor (placeholder hosts). */
class ProductionConfigTest {

    @Test
    fun production_applicationId() {
        assertEquals("com.quellkern.nachweis", AppConfig.applicationId)
    }

    @Test
    fun production_endpoints() {
        assertEquals("https://api.nachweis.tech", AppConfig.issuerBaseUrl)
        assertEquals("https://verifier.nachweis.tech", AppConfig.verifierBaseUrl)
    }

    @Test
    fun production_trustAnchors() {
        assertEquals("production_trust_anchors", AppConfig.trustAnchorsResourceName)
    }
}
