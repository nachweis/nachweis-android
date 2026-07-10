package com.quellkern.nachweis.wallet

import java.io.File
import kotlin.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The config assembly is pure, so its security-relevant outputs are asserted here without a
 * device: the produced [EudiWalletConfig] must carry exactly the storage path and key/auth
 * settings the policy specifies.
 */
class WalletConfigFactoryTest {

    @Test
    fun build_encodesStoragePathAndSecurePosture() {
        val dir = File("/data/user/0/com.quellkern.nachweis/no_backup/wallet")
        val policy = WalletSecurityPolicy.secure(debuggable = false)

        val config = WalletConfigFactory.build(dir, policy)

        assertEquals(dir.absolutePath, config.documentsStoragePath)
        assertTrue("user authentication must be required", config.userAuthenticationRequired)
        assertEquals(Duration.ZERO, config.userAuthenticationTimeout)
        assertTrue("StrongBox preference must be carried", config.useStrongBoxForKeys)
        assertEquals(SecureWalletLogger.OFF, config.logLevel)
    }

    @Test
    fun build_debugPolicy_keepsAuthRequiredButAllowsErrorLogging() {
        val config = WalletConfigFactory.build(File("/tmp/wallet"), WalletSecurityPolicy.secure(debuggable = true))
        assertTrue(config.userAuthenticationRequired)
        assertEquals(SecureWalletLogger.LEVEL_ERROR, config.logLevel)
    }
}
