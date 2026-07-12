package com.quellkern.nachweis.ui

import androidx.compose.runtime.saveable.listSaver

/**
 * Gate state for the credential detail view. Tracks which credential the user opened ([openId]) and
 * which one they have passed the device-auth gate for ([unlockedId]). Claims are read and shown only
 * while [visibleId] is non-null — i.e. the open credential has been unlocked — so viewing stored
 * claim values always crosses a fresh gate, matching the per-use posture of key operations.
 *
 * Pure and immutable so the transitions are unit-tested on the JVM off-device; [WalletReadyContent]
 * holds one instance in rememberSaveable, which keeps a configuration change or process recreation
 * from silently skipping the gate.
 */
data class CredentialViewGate(
    val openId: String? = null,
    val unlockedId: String? = null,
) {
    /** The credential to authenticate for, or null when none is open or the open one is unlocked. */
    val pendingId: String? get() = openId?.takeIf { it != unlockedId }

    /** The credential whose claims may be composed, or null while the gate is still closed. */
    val visibleId: String? get() = openId?.takeIf { it == unlockedId }

    /** Open [id], re-locking so its claims stay hidden until the gate is passed for it. */
    fun open(id: String): CredentialViewGate = CredentialViewGate(openId = id, unlockedId = null)

    /** Record that the gate was passed for [id]; ignored if a different credential is now open. */
    fun unlock(id: String): CredentialViewGate = if (openId == id) copy(unlockedId = id) else this

    /** Return to the list, dropping the unlock so re-opening the same credential re-gates. */
    fun close(): CredentialViewGate = Closed

    companion object {
        val Closed = CredentialViewGate()

        /** Persist both ids so a rebuild restores the same gate rather than skipping it. */
        val Saver = listSaver<CredentialViewGate, String?>(
            save = { listOf(it.openId, it.unlockedId) },
            restore = { CredentialViewGate(openId = it[0], unlockedId = it[1]) },
        )
    }
}
