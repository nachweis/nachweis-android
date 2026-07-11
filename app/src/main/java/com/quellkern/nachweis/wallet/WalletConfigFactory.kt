package com.quellkern.nachweis.wallet

import android.content.Context
import eu.europa.ec.eudi.wallet.EudiWalletConfig
import eu.europa.ec.eudi.wallet.transfer.openId4vp.ClientIdScheme
import eu.europa.ec.eudi.wallet.transfer.openId4vp.Format
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
     * OpenID4VP URI schemes the wallet accepts, matching the presentation deep-link filters in
     * the manifest (dev-plan.md B3). wallet-core's `startRemotePresentation` rejects any request
     * whose scheme is not listed here (`error("Not supported scheme")`), so this must be a
     * superset of what the manifest routes in.
     */
    val OPENID4VP_SCHEMES: List<String> =
        listOf("openid4vp", "eudi-openid4vp", "mdoc-openid4vp", "haip-vp")

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
            .configureOpenId4Vp {
                // Without this block wallet-core builds no OpenID4VP manager, so the very first
                // disclosure (`startRemotePresentation`) throws `error("Not supported scheme")`
                // and the Allow path fails while validation/consent (which run in our own code)
                // still work. The verifier authenticates by x5c, bound to the leaf via either
                // x509_san_dns or x509_hash, so both schemes are accepted; the authoritative
                // trust/registration checks already ran in PresentationRequestValidator +
                // DefaultRegistrationEvaluator before consent.
                withClientIdSchemes(ClientIdScheme.X509SanDns, ClientIdScheme.X509Hash)
                withSchemes(OPENID4VP_SCHEMES)
                // The PID is an SD-JWT VC signed/key-bound with ES256.
                withFormats(Format.SdJwtVc.ES256)
                // Encryption algorithms/methods stay at the builder defaults (all supported),
                // which cover the ECDH-ES + AES-GCM suite the verifier's `direct_post.jwt`
                // (JARM-encrypted) response mode requires.
            }

    /**
     * Resolve the wallet storage directory under noBackupFilesDir and build the config.
     * Per-flavor endpoints/trust roots (from [com.quellkern.nachweis.config.AppConfig]) are
     * folded in as the issuance and presentation slices land; the security posture already
     * does not vary by flavor.
     */
    fun create(context: Context, policy: WalletSecurityPolicy): EudiWalletConfig =
        build(WalletStorage.databaseFile(context), policy)
}
