package com.quellkern.nachweis.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves, on a real device/emulator, that wallet storage lives under noBackupFilesDir —
 * the platform-level guarantee that the wallet database is excluded from auto-backup and
 * device-to-device transfer.
 */
@RunWith(AndroidJUnit4::class)
class WalletStorageInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun documentsDir_isUnderNoBackupFilesDir() {
        val dir = WalletStorage.documentsDir(context)
        assertTrue(
            "wallet storage must be under noBackupFilesDir, was ${dir.absolutePath}",
            dir.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath),
        )
        assertTrue(WalletStorage.isUnderNoBackup(context, dir))
    }

    @Test
    fun ordinaryFilesDir_isNotTreatedAsNoBackup() {
        // Guards against isUnderNoBackup trivially returning true for everything.
        assertFalse(WalletStorage.isUnderNoBackup(context, context.filesDir))
    }
}
