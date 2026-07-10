package com.quellkern.nachweis.wallet

import eu.europa.ec.eudi.wallet.EudiWallet

/**
 * Lifecycle state of the wallet, consumed by the UI. Kept deliberately small; feature
 * slices (issuance, presentation) observe [Ready] and drive their own sub-states from the
 * [EudiWallet] it carries.
 */
sealed interface WalletState {
    /** No initialization attempted yet. */
    data object Uninitialized : WalletState

    /** Initialization in progress. */
    data object Initializing : WalletState

    /** Wallet is ready for use. */
    data class Ready(val wallet: EudiWallet) : WalletState

    /** Initialization failed with a typed, display-safe reason. */
    data class Failed(val error: WalletInitError) : WalletState
}
