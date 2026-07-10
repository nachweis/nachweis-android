package com.quellkern.nachweis.wallet

import android.content.Context
import eu.europa.ec.eudi.wallet.EudiWallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.withSettings

/**
 * State-machine behavior of [WalletController] with a fake provider. No device, Keystore,
 * or real wallet: the provider either returns a stand-in wallet or throws, and we assert
 * the resulting [WalletState] and error mapping.
 */
class WalletControllerTest {

    // A no-op EudiWallet stand-in. We only hold the reference; no methods are invoked.
    private val fakeWallet: EudiWallet = mock(EudiWallet::class.java)

    // A Context stand-in whose applicationContext returns itself.
    private val fakeContext: Context = mock(Context::class.java, withSettings().defaultAnswer { invocation ->
        if (invocation.method.name == "getApplicationContext") invocation.mock else null
    })

    @Test
    fun initialize_success_reachesReadyWithWallet() {
        val controller = WalletController { fakeWallet }
        val result = controller.initialize(fakeContext)
        assertTrue(result is WalletState.Ready)
        assertSame(fakeWallet, (controller.state.value as WalletState.Ready).wallet)
    }

    @Test
    fun initialize_failure_mapsToTypedError() {
        val controller = WalletController { throw IllegalStateException("StrongBox not available") }
        val result = controller.initialize(fakeContext)
        assertEquals(WalletState.Failed(WalletInitError.StrongBoxUnavailable), result)
    }

    @Test
    fun initialize_isIdempotentOnceReady() {
        var calls = 0
        val controller = WalletController { calls++; fakeWallet }
        controller.initialize(fakeContext)
        controller.initialize(fakeContext)
        assertEquals("provider must not run again once ready", 1, calls)
    }

    @Test
    fun reset_allowsReinitializationAfterFailure() {
        var attempt = 0
        val controller = WalletController {
            attempt++
            if (attempt == 1) throw RuntimeException("transient") else fakeWallet
        }
        assertTrue(controller.initialize(fakeContext) is WalletState.Failed)
        controller.reset()
        assertEquals(WalletState.Uninitialized, controller.state.value)
        assertTrue(controller.initialize(fakeContext) is WalletState.Ready)
    }
}
