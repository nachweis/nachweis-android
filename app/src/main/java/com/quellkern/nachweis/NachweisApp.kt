package com.quellkern.nachweis

import android.app.Application
import androidx.fragment.app.FragmentActivity
import com.quellkern.nachweis.config.AppConfig
import com.quellkern.nachweis.issuance.BiometricUserAuthenticator
import com.quellkern.nachweis.issuance.DefaultOid4vciGateway
import com.quellkern.nachweis.issuance.DocumentStore
import com.quellkern.nachweis.issuance.IssuanceController
import com.quellkern.nachweis.issuance.IssuerAllowlist
import com.quellkern.nachweis.issuance.WalletDocumentStore
import com.quellkern.nachweis.presentation.CachedStatusSource
import com.quellkern.nachweis.presentation.DefaultOid4vpGateway
import com.quellkern.nachweis.presentation.PresentationController
import com.quellkern.nachweis.presentation.PresentationRequestValidator
import com.quellkern.nachweis.presentation.TrustStore
import com.quellkern.nachweis.wallet.DefaultWalletProvider
import com.quellkern.nachweis.wallet.SecureWalletLogger
import com.quellkern.nachweis.wallet.WalletController
import com.quellkern.nachweis.wallet.WalletSecurityPolicy
import eu.europa.ec.eudi.wallet.EudiWallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.lang.ref.WeakReference

/**
 * Composition root. Wires the wallet security policy, logger, provider, and controller by
 * hand, plus the issuance stack (gateway, allowlist, controller) that a ready wallet enables.
 * Keeping the graph explicit here keeps the security-relevant wiring — key policy, issuer
 * allowlist, device-auth seam — in one auditable place; a DI container is still deferred.
 */
class NachweisApp : Application() {

    /** Process-wide wallet controller. The UI observes [WalletController.state]. */
    lateinit var walletController: WalletController
        private set

    // App-scoped coroutine scope for issuance work. Main.immediate so state updates and the
    // BiometricPrompt (which requires the main thread) run without extra hops; suspending
    // network calls inside wallet-core use their own dispatchers.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // The foreground activity, needed by BiometricPrompt. Weakly held and cleared on stop so
    // a backgrounded activity is never used to raise an authentication prompt.
    private var activityRef = WeakReference<FragmentActivity>(null)

    private var issuance: IssuanceController? = null
    private var presentation: PresentationController? = null

    override fun onCreate() {
        super.onCreate()
        val debuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val policy = WalletSecurityPolicy.secure(debuggable)
        val logger = SecureWalletLogger(debuggable)
        walletController = WalletController(DefaultWalletProvider(policy, logger))
    }

    /** Set (or clear) the foreground activity used to raise device-authentication prompts. */
    fun setForegroundActivity(activity: FragmentActivity?) {
        activityRef = WeakReference(activity)
    }

    /** Lazily build (once) the issuance controller for a ready [wallet]. */
    fun issuanceController(wallet: EudiWallet): IssuanceController =
        issuance ?: run {
            val authenticator = BiometricUserAuthenticator { activityRef.get() }
            val gateway = DefaultOid4vciGateway(
                wallet = wallet,
                issuerUrl = effectiveIssuerUrl(),
                clientId = BuildConfig.OID4VCI_CLIENT_ID,
                authenticator = authenticator,
                authScope = appScope,
            )
            IssuanceController(gateway, buildAllowlist(), appScope).also { issuance = it }
        }

    /**
     * Lazily build (once) the presentation controller for a ready [wallet]. The validator is
     * wired with the flavor's locally bundled demo trust anchors and a status source seeded
     * from the cached status list; with no status cache yet published (Workstream A), every
     * status lookup is Unknown and fails closed — an honest default, not a silent pass.
     */
    fun presentationController(wallet: EudiWallet): PresentationController =
        presentation ?: run {
            val validator = PresentationRequestValidator(
                trustStore = loadTrustStore(),
                statusSource = CachedStatusSource(),
            )
            PresentationController(
                gateway = DefaultOid4vpGateway(wallet),
                validator = validator,
                scope = appScope,
            ).also { presentation = it }
        }

    /** Document reader for a ready [wallet]. */
    fun documentStore(wallet: EudiWallet): DocumentStore = WalletDocumentStore(wallet)

    /** Load the active flavor's bundled demo trust anchors (public certificates only). */
    private fun loadTrustStore(): TrustStore {
        val resId = resources.getIdentifier(AppConfig.trustAnchorsResourceName, "raw", packageName)
        if (resId == 0) return TrustStore(emptyList())
        return resources.openRawResource(resId).use { TrustStore(TrustStore.parsePemCertificates(it)) }
    }

    // The configured issuer, or the developer-local override when the demo flavor sets one.
    private fun effectiveIssuerUrl(): String =
        BuildConfig.LOCAL_ISSUER_OVERRIDE.ifBlank { AppConfig.issuerBaseUrl }

    // Permit the configured issuer, plus the local override when present. Production leaves
    // LOCAL_ISSUER_OVERRIDE empty, so its allowlist is exactly the configured issuer.
    private fun buildAllowlist(): IssuerAllowlist {
        val origins = buildList {
            add(AppConfig.issuerBaseUrl)
            if (BuildConfig.LOCAL_ISSUER_OVERRIDE.isNotBlank()) add(BuildConfig.LOCAL_ISSUER_OVERRIDE)
        }
        return IssuerAllowlist(origins)
    }
}
