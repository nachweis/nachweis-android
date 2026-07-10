package com.quellkern.nachweis

import com.quellkern.nachweis.config.AppConfig
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Flavor-agnostic configuration invariants. This test runs for every variant
 * (demoDebug and productionDebug) and asserts properties that must hold in all of
 * them. Exact per-flavor values are asserted in the flavor-specific source sets
 * (src/testDemo, src/testProduction).
 */
class AppConfigTest {

    @Test
    fun config_isStructurallyValid() {
        assertTrue("resolved flavor config must be valid", AppConfig.isValid())
    }

    @Test
    fun endpoints_areHttpsAndDistinct() {
        assertTrue(AppConfig.issuerBaseUrl.startsWith("https://"))
        assertTrue(AppConfig.verifierBaseUrl.startsWith("https://"))
        assertNotEquals(AppConfig.issuerBaseUrl, AppConfig.verifierBaseUrl)
    }

    @Test
    fun applicationId_isNamespaced() {
        assertTrue(AppConfig.applicationId.startsWith("com.quellkern.nachweis"))
    }

    @Test
    fun trustAnchorsResource_isNamed() {
        assertTrue(AppConfig.trustAnchorsResourceName.isNotBlank())
    }
}
