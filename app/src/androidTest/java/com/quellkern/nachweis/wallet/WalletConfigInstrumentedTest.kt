package com.quellkern.nachweis.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end (on device) check that the wired config the app actually builds points wallet
 * storage under noBackupFilesDir and requires user authentication. Complements the pure JVM
 * WalletConfigFactoryTest by exercising the real context-resolving path.
 */
@RunWith(AndroidJUnit4::class)
class WalletConfigInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun createdConfig_storesUnderNoBackup_andRequiresAuth() {
        val config = WalletConfigFactory.create(context, WalletSecurityPolicy.secure(debuggable = false))
        val storagePath = requireNotNull(config.documentsStoragePath) { "storage path must be set" }
        // Canonicalize both sides: noBackupFilesDir resolves the /data/user/0 -> /data/data
        // symlink, while the stored path does not.
        assertTrue(
            "config storage must resolve under noBackupFilesDir, was $storagePath",
            WalletStorage.isUnderNoBackup(context, File(storagePath)),
        )
        assertTrue("credential keys must require user authentication", config.userAuthenticationRequired)
    }
}
