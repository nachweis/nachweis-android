package com.quellkern.nachweis.wallet

import android.content.Context
import eu.europa.ec.eudi.wallet.EudiWalletConfig
import java.io.File

/**
 * Assembles the [EudiWalletConfig] that encodes the app's security posture.
 *
 * The assembly is split so the security-relevant decisions are testable without a device:
 * [build] is a pure function of a storage directory and a [WalletSecurityPolicy];
 * [create] only adds the Android-specific step of resolving that directory under
 * noBackupFilesDir.
 */
object WalletConfigFactory {

    /** Document-manager identifier for this app's single store. */
    const val DOCUMENT_MANAGER_ID: String = "nachweis-documents"

    /**
     * Pure assembly: encode [policy] and the [storageFile] into an [EudiWalletConfig].
     * [storageFile] is the SQLite database *file* wallet-core opens (see
     * [WalletStorage.databaseFile]), not a directory. No Android context is touched, so this
     * is exercised directly in JVM unit tests.
     */
    fun build(storageFile: File, policy: WalletSecurityPolicy): EudiWalletConfig =
        EudiWalletConfig()
            .configureDocumentManager(storageFile.absolutePath, DOCUMENT_MANAGER_ID)
            .configureDocumentKeyCreation(
                userAuthenticationRequired = policy.userAuthenticationRequired,
                userAuthenticationTimeout = policy.userAuthenticationTimeout,
                useStrongBoxForKeys = policy.useStrongBox,
            )
            .configureLogging(policy.logLevel)

    /**
     * Resolve the wallet storage directory under noBackupFilesDir and build the config.
     * Per-flavor endpoints/trust roots (from [com.quellkern.nachweis.config.AppConfig]) are
     * folded in as the issuance and presentation slices land; the security posture already
     * does not vary by flavor.
     */
    fun create(context: Context, policy: WalletSecurityPolicy): EudiWalletConfig =
        build(WalletStorage.databaseFile(context), policy)
}
