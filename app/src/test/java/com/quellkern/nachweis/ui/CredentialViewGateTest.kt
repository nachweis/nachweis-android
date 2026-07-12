package com.quellkern.nachweis.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The credential detail view's device-auth gate, as a pure state machine (the on-device
 * BiometricPrompt itself is exercised only in [com.quellkern.nachweis.BiometricDeviceAuthGate]).
 * The invariant under test: a credential's claims are visible only after the gate is passed for
 * exactly that credential, and never before.
 */
class CredentialViewGateTest {

    @Test
    fun closedGateShowsNothing() {
        val gate = CredentialViewGate.Closed
        assertNull(gate.pendingId)
        assertNull(gate.visibleId)
    }

    @Test
    fun openingRequestsAuthAndHidesClaimsUntilUnlocked() {
        val gate = CredentialViewGate.Closed.open("doc-1")
        assertEquals("doc-1", gate.pendingId)
        assertNull("claims stay hidden until the gate is passed", gate.visibleId)
    }

    @Test
    fun unlockingTheOpenCredentialMakesItVisible() {
        val gate = CredentialViewGate.Closed.open("doc-1").unlock("doc-1")
        assertNull(gate.pendingId)
        assertEquals("doc-1", gate.visibleId)
    }

    @Test
    fun unlockingADifferentCredentialIsIgnored() {
        val gate = CredentialViewGate.Closed.open("doc-1").unlock("doc-2")
        assertEquals("doc-1", gate.pendingId)
        assertNull(gate.visibleId)
    }

    @Test
    fun openingAnotherCredentialReLocks() {
        val gate = CredentialViewGate.Closed.open("doc-1").unlock("doc-1").open("doc-2")
        assertEquals("doc-2", gate.pendingId)
        assertNull("the newly opened credential is not yet unlocked", gate.visibleId)
    }

    @Test
    fun closingResetsTheGate() {
        val gate = CredentialViewGate.Closed.open("doc-1").unlock("doc-1").close()
        assertEquals(CredentialViewGate.Closed, gate)
        assertNull(gate.visibleId)
    }

    @Test
    fun reopeningAfterCloseReGates() {
        // Backing out of a credential must drop its unlock so re-opening it prompts again.
        val gate = CredentialViewGate.Closed.open("doc-1").unlock("doc-1").close().open("doc-1")
        assertEquals("doc-1", gate.pendingId)
        assertNull(gate.visibleId)
    }
}
