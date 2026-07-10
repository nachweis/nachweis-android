package com.quellkern.nachweis.issuance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.UnknownHostException

class IssuanceErrorTest {

    @Test
    fun `network exceptions map to Network`() {
        assertEquals(IssuanceError.Network, IssuanceError.fromThrowable(UnknownHostException("api-sandbox")))
        assertEquals(IssuanceError.Network, IssuanceError.fromThrowable(ConnectException("refused")))
    }

    @Test
    fun `authentication failures map to UserAuthFailed`() {
        assertEquals(IssuanceError.UserAuthFailed, IssuanceError.fromThrowable(UserAuthException("cancelled")))
        assertEquals(
            IssuanceError.UserAuthFailed,
            IssuanceError.fromThrowable(IllegalStateException("Key user not authenticated")),
        )
    }

    @Test
    fun `unknown causes fall through to Unexpected and never leak the message`() {
        val secret = "PID surname=Mustermann token=eyJ..."
        val error = IssuanceError.fromThrowable(RuntimeException(secret))
        assertTrue(error is IssuanceError.Unexpected)
        assertFalse("public message must not echo the cause", error.publicMessage.contains("Mustermann"))
        assertFalse(error.publicMessage.contains("eyJ"))
    }
}
