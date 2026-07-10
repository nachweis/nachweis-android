package com.quellkern.nachweis

import android.app.Application
import com.quellkern.nachweis.wallet.DefaultWalletProvider
import com.quellkern.nachweis.wallet.SecureWalletLogger
import com.quellkern.nachweis.wallet.WalletController
import com.quellkern.nachweis.wallet.WalletSecurityPolicy

/**
 * Composition root. Wires the wallet security policy, logger, provider, and controller by
 * hand. A DI container (Koin, per the plan) is deliberately deferred until issuance and
 * presentation introduce a second and third graph worth wiring; a single controller does
 * not yet justify the indirection, and keeping the graph explicit here keeps the
 * security-relevant wiring in one auditable place.
 */
class NachweisApp : Application() {

    /** Process-wide wallet controller. The UI observes [WalletController.state]. */
    lateinit var walletController: WalletController
        private set

    override fun onCreate() {
        super.onCreate()
        val debuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val policy = WalletSecurityPolicy.secure(debuggable)
        val logger = SecureWalletLogger(debuggable)
        walletController = WalletController(DefaultWalletProvider(policy, logger))
    }
}
