package com.quellkern.nachweis.wallet

import android.database.sqlite.SQLiteDatabase
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

    /**
     * Regression for the fresh-install "wallet could not be initialized" crash: wallet-core
     * hands the configured storage path straight to multipaz's AndroidStorage, which opens it
     * as a SQLite database file. The path must therefore be an openable file whose parent
     * exists — not a directory (which threw SQLiteCantOpenDatabaseException at
     * AndroidStorage.kt:41 on first launch). This reproduces that exact open with no wallet
     * auth needed.
     */
    @Test
    fun configuredStoragePath_opensAsSqliteDatabase() {
        val policy = WalletSecurityPolicy.secure(debuggable = true)
        val storagePath = requireNotNull(WalletConfigFactory.create(context, policy).documentsStoragePath) {
            "config must carry a storage path"
        }

        assertTrue(
            "configured storage must be under noBackupFilesDir, was $storagePath",
            WalletStorage.isUnderNoBackup(context, java.io.File(storagePath)),
        )
        // The open that AndroidStorage performs; a directory path fails here, a file succeeds.
        val db = SQLiteDatabase.openOrCreateDatabase(storagePath, null)
        try {
            assertTrue("database must be open after init path", db.isOpen)
        } finally {
            db.close()
        }
    }
}
