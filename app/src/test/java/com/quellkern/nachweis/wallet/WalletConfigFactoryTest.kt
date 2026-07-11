package com.quellkern.nachweis.wallet

import eu.europa.ec.eudi.wallet.transfer.openId4vp.ClientIdScheme
import eu.europa.ec.eudi.wallet.transfer.openId4vp.Format
import java.io.File
import kotlin.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        val dbFile = File("/data/user/0/com.quellkern.nachweis/no_backup/wallet/wallet-store.db")
        val policy = WalletSecurityPolicy.secure(debuggable = false)

        val config = WalletConfigFactory.build(dbFile, policy)

        assertEquals(dbFile.absolutePath, config.documentsStoragePath)
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

    @Test
    fun build_configuresOpenId4VpSoRemotePresentationCanRun() {
        // Regression guard: without an OpenID4VP config wallet-core builds no OpenId4VpManager,
        // so the very first disclosure throws `error("Not supported scheme")` and the Allow path
        // fails while validation/consent still work. The config must be present and cover the
        // verifier's x509 client-id binding, the app's deep-link schemes, and the SD-JWT PID.
        val config = WalletConfigFactory.build(File("/tmp/wallet"), WalletSecurityPolicy.secure(debuggable = false))
        val vp = config.openId4VpConfig
        assertNotNull("OpenID4VP must be configured or startRemotePresentation cannot run", vp)
        vp!!

        assertTrue(
            "x509_san_dns client-id binding must be accepted",
            vp.clientIdSchemes.any { it is ClientIdScheme.X509SanDns },
        )
        assertTrue(
            "x509_hash client-id binding must be accepted (the deployed verifier uses it)",
            vp.clientIdSchemes.any { it is ClientIdScheme.X509Hash },
        )
        // The wallet must accept every presentation deep-link scheme the manifest routes in, or
        // startRemotePresentation rejects the request as an unsupported scheme.
        assertEquals(WalletConfigFactory.OPENID4VP_SCHEMES, vp.schemes)
        assertTrue(
            "the SD-JWT VC PID format must be presentable",
            vp.formats.any { it is Format.SdJwtVc },
        )
        // Response encryption must be offered for the verifier's direct_post.jwt (JARM) mode.
        assertTrue("encryption algorithms must be offered", vp.encryptionAlgorithms.isNotEmpty())
        assertTrue("encryption methods must be offered", vp.encryptionMethods.isNotEmpty())
    }
}
