package com.quellkern.nachweis.presentation

import com.quellkern.nachweis.issuance.UserAuthException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * [PresentationError.fromThrowable] and [presentationFailureTitle]. Mirrors
 * [com.quellkern.nachweis.issuance.IssuanceErrorTest]: the mapping is by exception type and a
 * small set of stable keywords, never by echoing the cause message, and the phase-honest dialog
 * titles are split so a *refusal* and an *operational failure* read differently.
 */
class PresentationErrorTest {

    @Test
    fun `network exceptions map to Network`() {
        assertEquals(PresentationError.Network, PresentationError.fromThrowable(UnknownHostException("verifier-sandbox")))
        assertEquals(PresentationError.Network, PresentationError.fromThrowable(ConnectException("refused")))
        assertEquals(PresentationError.Network, PresentationError.fromThrowable(SocketTimeoutException("timeout")))
    }

    @Test
    fun `device-auth failures map to UserAuthDeclined`() {
        assertEquals(PresentationError.UserAuthDeclined, PresentationError.fromThrowable(UserAuthException("cancelled")))
        assertEquals(
            PresentationError.UserAuthDeclined,
            PresentationError.fromThrowable(IllegalStateException("Key user not authenticated")),
        )
    }

    @Test
    fun `unknown causes fall through to Unexpected and never leak the message`() {
        val secret = "PID given_name=Erika token=eyJhbGci..."
        val error = PresentationError.fromThrowable(RuntimeException(secret))
        assertTrue(error is PresentationError.Unexpected)
        assertFalse("public message must not echo the cause", error.publicMessage.contains("Erika"))
        assertFalse(error.publicMessage.contains("eyJ"))
    }

    @Test
    fun `every case has pairwise-distinct public copy`() {
        val all: List<PresentationError> = listOf(
            PresentationError.Unreadable,
            PresentationError.NotSigned,
            PresentationError.BadSignature,
            PresentationError.NoCertificate,
            PresentationError.Untrusted,
            PresentationError.CertificateExpired,
            PresentationError.Revoked,
            PresentationError.StatusUnavailable,
            PresentationError.ClientIdMismatch,
            PresentationError.UnsupportedQuery,
            PresentationError.UnsupportedCredential,
            PresentationError.RegistrationMissing,
            PresentationError.UnsupportedRegistrationProfile,
            PresentationError.RegistrationUnverifiable,
            PresentationError.RegistrationRevoked,
            PresentationError.RegistrationExpired,
            PresentationError.RegistrationStatusUnavailable,
            PresentationError.RegistrationBindingMismatch,
            PresentationError.Network,
            PresentationError.UserAuthDeclined,
            PresentationError.Unexpected(RuntimeException("x")),
        )
        val messages = all.map { it.publicMessage }
        assertEquals("no two cases may share display copy", messages.size, messages.toSet().size)
        assertTrue("no case may have blank copy", messages.none { it.isBlank() })
    }

    @Test
    fun `operational failures read as could-not-share, refusals as rejected`() {
        assertEquals("Couldn't share", presentationFailureTitle(PresentationError.Network))
        assertEquals("Couldn't share", presentationFailureTitle(PresentationError.UserAuthDeclined))
        assertEquals("Couldn't share", presentationFailureTitle(PresentationError.Unexpected(RuntimeException("x"))))
        assertEquals("Request rejected", presentationFailureTitle(PresentationError.Unreadable))
        assertEquals("Request rejected", presentationFailureTitle(PresentationError.BadSignature))
        assertEquals("Request rejected", presentationFailureTitle(PresentationError.Untrusted))
        assertEquals("Request rejected", presentationFailureTitle(PresentationError.RegistrationRevoked))
    }
}
