package com.quellkern.nachweis.wallet

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns wallet initialization and exposes its lifecycle as [state]. This is the single
 * object the UI observes; feature slices reach the [EudiWallet] through [WalletState.Ready].
 *
 * Construction never does work — [initialize] is explicit and idempotent — so the controller
 * can be created eagerly (e.g. in Application) while the actual Keystore/storage touch is
 * deferred and, on failure, mapped to a typed [WalletInitError] rather than crashing.
 */
class WalletController(
    private val provider: WalletProvider,
) {
    private val _state = MutableStateFlow<WalletState>(WalletState.Uninitialized)
    val state: StateFlow<WalletState> = _state.asStateFlow()

    /**
     * Initialize the wallet if not already ready or in progress. Returns the resulting
     * state. Any thrown exception is caught and mapped; this function does not throw.
     */
    fun initialize(context: Context): WalletState {
        val current = _state.value
        if (current is WalletState.Ready || current is WalletState.Initializing) return current

        _state.value = WalletState.Initializing
        val next = try {
            WalletState.Ready(provider.create(context.applicationContext))
        } catch (t: Throwable) {
            WalletState.Failed(WalletInitError.fromThrowable(t))
        }
        _state.value = next
        return next
    }

    /** Reset to [WalletState.Uninitialized], allowing a fresh [initialize] after a failure. */
    fun reset() {
        _state.value = WalletState.Uninitialized
    }
}
