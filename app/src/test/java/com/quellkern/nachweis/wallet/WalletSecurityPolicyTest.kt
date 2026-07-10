package com.quellkern.nachweis.wallet

import kotlin.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shipping security posture is a hard requirement, not a default that may drift.
 * These assertions fail loudly if a future change weakens key authentication or turns on
 * verbose logging in release.
 */
class WalletSecurityPolicyTest {

    @Test
    fun secure_requiresAuthenticationForEveryUse() {
        val policy = WalletSecurityPolicy.secure(debuggable = false)
        assertTrue("keys must require user authentication", policy.userAuthenticationRequired)
        assertEquals("no reuse window: auth on every key use", Duration.ZERO, policy.userAuthenticationTimeout)
    }

    @Test
    fun secure_prefersStrongBox() {
        assertTrue(WalletSecurityPolicy.secure(debuggable = true).useStrongBox)
    }

    @Test
    fun secure_release_disablesLogging() {
        assertEquals(SecureWalletLogger.OFF, WalletSecurityPolicy.secure(debuggable = false).logLevel)
    }

    @Test
    fun secure_debug_limitsLoggingToErrors() {
        val level = WalletSecurityPolicy.secure(debuggable = true).logLevel
        assertEquals(SecureWalletLogger.LEVEL_ERROR, level)
        assertNotEquals("debug builds must not log at DEBUG level", SecureWalletLogger.LEVEL_DEBUG, level)
    }
}
