package com.quellkern.nachweis.wallet

import android.content.Context
import eu.europa.ec.eudi.wallet.EudiWallet

/**
 * Constructs an [EudiWallet]. Abstracted behind an interface so [WalletController] can be
 * unit-tested on the JVM with a fake that returns a stub or throws, without booting an
 * emulator or touching the Keystore.
 */
fun interface WalletProvider {
    /** Build a wallet, or throw; [WalletController] maps thrown exceptions to typed errors. */
    fun create(context: Context): EudiWallet
}

/**
 * Production provider: assembles the secure config and builds the real wallet. The
 * attestations provider and configure-builder arguments are left at their wallet-core
 * defaults (null) — this is the minimal secure initialization the foundation needs; live
 * attestation wiring arrives with the issuance slice.
 */
class DefaultWalletProvider(
    private val policy: WalletSecurityPolicy,
    private val walletLogger: SecureWalletLogger,
) : WalletProvider {
    override fun create(context: Context): EudiWallet {
        val config = WalletConfigFactory.create(context, policy)
        return EudiWallet(context, config) {
            withLogger(walletLogger)
        }
    }
}
