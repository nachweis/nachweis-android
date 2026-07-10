package com.quellkern.nachweis.wallet

import android.content.Context
import java.io.File

/**
 * Single source of truth for where wallet data lives on disk.
 *
 * The directory is a child of [Context.getNoBackupFilesDir]. That placement is the
 * enforcement point behind the "wallet DB is never backed up" acceptance criterion:
 * anything under noBackupFiles is excluded from auto-backup and device-to-device
 * transfer by the platform, independently of the manifest backup rules (which exclude
 * it a second time as defense in depth).
 */
object WalletStorage {
    /** Subdirectory name for wallet-core's document store. */
    const val DIR_NAME: String = "wallet"

    /** The wallet document-store directory, always under noBackupFilesDir. */
    fun documentsDir(context: Context): File =
        File(context.noBackupFilesDir, DIR_NAME).apply { mkdirs() }

    /**
     * True when [path] is contained in this context's noBackupFilesDir subtree. Used by
     * instrumented tests to prove the storage location; kept here so the check and the
     * location share one definition.
     */
    fun isUnderNoBackup(context: Context, path: File): Boolean {
        val root = context.noBackupFilesDir.canonicalFile
        var current: File? = path.canonicalFile
        while (current != null) {
            if (current == root) return true
            current = current.parentFile
        }
        return false
    }
}
