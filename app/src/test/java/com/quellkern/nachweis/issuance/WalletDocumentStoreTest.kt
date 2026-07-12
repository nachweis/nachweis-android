package com.quellkern.nachweis.issuance

import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.document.Outcome
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

/**
 * [WalletDocumentStore.delete] maps wallet-core's delete [Outcome] onto a plain success flag. The
 * wallet is a Mockito mock (an interface, so no inline maker is needed); the outcome is built with
 * the JVM-visible Outcome.success/failure factories, confirming the type does not resist JVM
 * construction. Only the delegation is under test here; the on-device paths are covered by
 * DeleteCredentialInstrumentedTest.
 */
class WalletDocumentStoreTest {

    @Test
    fun `delete returns true when wallet-core reports success`() {
        val wallet = Mockito.mock(EudiWallet::class.java)
        Mockito.`when`(wallet.deleteDocumentById("doc-1")).thenReturn(Outcome.success(ByteArray(0)))

        assertTrue(WalletDocumentStore(wallet).delete("doc-1"))
    }

    @Test
    fun `delete returns false when wallet-core reports failure`() {
        val wallet = Mockito.mock(EudiWallet::class.java)
        Mockito.`when`(wallet.deleteDocumentById("doc-9"))
            .thenReturn(Outcome.failure(IllegalStateException("no such document")))

        assertFalse(WalletDocumentStore(wallet).delete("doc-9"))
    }
}
