package com.quellkern.nachweis.wallet

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exception-to-typed-error mapping. Matching is by type and stable capability keywords;
 * unknown causes must fall through to [WalletInitError.Unexpected], and no public message
 * may echo the raw cause text.
 */
class WalletInitErrorTest {

    @Test
    fun strongBox_shortage_maps() {
        val e = WalletInitError.fromThrowable(IllegalStateException("StrongBox not available on device"))
        assertEquals(WalletInitError.StrongBoxUnavailable, e)
    }

    @Test
    fun userAuth_shortage_maps() {
        val e = WalletInitError.fromThrowable(IllegalStateException("Secure lock screen must be set up for user authentication"))
        assertEquals(WalletInitError.UserAuthUnavailable, e)
    }

    @Test
    fun io_maps_toStorage() {
        val e = WalletInitError.fromThrowable(IOException("disk full"))
        assertEquals(WalletInitError.StorageUnavailable, e)
    }

    @Test
    fun unknown_fallsThrough() {
        val cause = RuntimeException("something else entirely")
        val e = WalletInitError.fromThrowable(cause)
        assertTrue(e is WalletInitError.Unexpected)
        assertEquals(cause, (e as WalletInitError.Unexpected).cause)
    }

    @Test
    fun publicMessage_neverLeaksCause() {
        val e = WalletInitError.fromThrowable(RuntimeException("token=eyJhbGciOi secret"))
        assertFalse(e.publicMessage.contains("eyJhbGciOi"))
    }
}
