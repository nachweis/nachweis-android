package com.quellkern.nachweis.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * The presentation state machine. Uses the real [PresentationRequestValidator] with local
 * fixtures behind a recording fake gateway, so the tests exercise the true validation path
 * while proving the controller's ordering guarantees — most importantly that **no verifier
 * traffic happens during consent**: the gateway is touched once on arrival and again only
 * after the user allows, never in between.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PresentationControllerTest {

    private val ca = PresentationFixtures.newCa()
    private val leaf = PresentationFixtures.newLeaf(ca)
    private val validator = PresentationRequestValidator(TrustStore(listOf(ca.certificate)), PresentationFixtures.statusGood(leaf))
    private val fixedNow = Date()

    /** Records every gateway interaction so the tests can assert exact call ordering/counts. */
    private class RecordingGateway(
        private val signed: String,
        private val failObtain: Boolean = false,
        private val obtainThrowable: Throwable = IllegalStateException("cannot reach request_uri"),
        private val sendThrowable: Throwable? = null,
    ) : Oid4vpGateway {
        var obtainCalls = 0
        var sendCalls = 0
        var rejectCalls = 0
        var lastDisclosed: List<RequestedClaim>? = null

        override suspend fun obtainSignedRequest(requestUri: String): SignedPresentationRequest {
            obtainCalls++
            if (failObtain) throw obtainThrowable
            return SignedPresentationRequest(signed)
        }

        override suspend fun sendResponse(request: ValidatedPresentationRequest, disclosedClaims: List<RequestedClaim>) {
            sendCalls++
            sendThrowable?.let { throw it }
            lastDisclosed = disclosedClaims
        }

        override fun reject() {
            rejectCalls++
        }
    }

    private fun controller(gateway: Oid4vpGateway, scope: kotlinx.coroutines.CoroutineScope) =
        PresentationController(gateway, validator, scope, clock = { fixedNow })

    @Test
    fun `a valid request stops at consent without contacting the verifier`() = runTest {
        val gateway = RecordingGateway(PresentationFixtures.signedRequest(leaf))
        val controller = controller(gateway, this)

        controller.onRequest("openid4vp://?request_uri=https://verifier-sandbox.nachweis.tech/req")
        advanceUntilIdle()

        val state = controller.state.value
        assertTrue("expected AwaitingConsent but was $state", state is PresentationState.AwaitingConsent)
        // Arrival fetched the request exactly once; consent evaluation added no further calls.
        assertEquals(1, gateway.obtainCalls)
        assertEquals(0, gateway.sendCalls)
        assertEquals(0, gateway.rejectCalls)
    }

    @Test
    fun `confirm discloses exactly the requested claims`() = runTest {
        val gateway = RecordingGateway(PresentationFixtures.signedRequest(leaf))
        val controller = controller(gateway, this)

        controller.onRequest("openid4vp://request")
        advanceUntilIdle()
        controller.confirm()
        advanceUntilIdle()

        assertEquals(PresentationState.Sent, controller.state.value)
        assertEquals(1, gateway.sendCalls)
        assertEquals(listOf("given_name", "family_name"), gateway.lastDisclosed?.map { it.path })
    }

    @Test
    fun `decline notifies the verifier and discloses nothing`() = runTest {
        val gateway = RecordingGateway(PresentationFixtures.signedRequest(leaf))
        val controller = controller(gateway, this)

        controller.onRequest("openid4vp://request")
        advanceUntilIdle()
        controller.decline()
        advanceUntilIdle()

        assertEquals(PresentationState.Declined, controller.state.value)
        assertEquals(1, gateway.rejectCalls)
        assertEquals(0, gateway.sendCalls)
    }

    @Test
    fun `an invalid request is rejected before any consent step`() = runTest {
        val gateway = RecordingGateway(PresentationFixtures.corruptSignature(PresentationFixtures.signedRequest(leaf)))
        val controller = controller(gateway, this)

        controller.onRequest("openid4vp://request")
        advanceUntilIdle()

        val state = controller.state.value
        assertTrue(state is PresentationState.Rejected)
        assertEquals(PresentationError.BadSignature, (state as PresentationState.Rejected).error)
        assertEquals(0, gateway.sendCalls)
    }

    @Test
    fun `a failure to obtain the request maps to an unreadable rejection`() = runTest {
        val gateway = RecordingGateway(PresentationFixtures.signedRequest(leaf), failObtain = true)
        val controller = controller(gateway, this)

        controller.onRequest("openid4vp://request")
        advanceUntilIdle()

        val state = controller.state.value
        assertTrue(state is PresentationState.Rejected)
        assertEquals(PresentationError.Unreadable, (state as PresentationState.Rejected).error)
    }

    @Test
    fun `confirm is a no-op when not awaiting consent`() = runTest {
        val gateway = RecordingGateway(PresentationFixtures.signedRequest(leaf))
        val controller = controller(gateway, this)
        controller.confirm()
        advanceUntilIdle()
        assertEquals(PresentationState.Idle, controller.state.value)
        assertEquals(0, gateway.sendCalls)
    }

    @Test
    fun `a network fault while obtaining the request maps to Network, not Unreadable`() = runTest {
        val gateway = RecordingGateway(
            PresentationFixtures.signedRequest(leaf),
            failObtain = true,
            obtainThrowable = java.net.UnknownHostException("verifier-sandbox.nachweis.tech"),
        )
        val controller = controller(gateway, this)

        controller.onRequest("openid4vp://request")
        advanceUntilIdle()

        val state = controller.state.value
        assertTrue(state is PresentationState.Rejected)
        assertEquals(PresentationError.Network, (state as PresentationState.Rejected).error)
    }

    @Test
    fun `a network fault while sending maps to Network`() = runTest {
        val gateway = RecordingGateway(
            PresentationFixtures.signedRequest(leaf),
            sendThrowable = java.net.ConnectException("connection refused"),
        )
        val controller = controller(gateway, this)

        controller.onRequest("openid4vp://request")
        advanceUntilIdle()
        controller.confirm()
        advanceUntilIdle()

        val state = controller.state.value
        assertTrue(state is PresentationState.Rejected)
        assertEquals(PresentationError.Network, (state as PresentationState.Rejected).error)
        assertEquals(1, gateway.sendCalls)
    }

    @Test
    fun `a declined device authentication while sending maps to UserAuthDeclined`() = runTest {
        val gateway = RecordingGateway(
            PresentationFixtures.signedRequest(leaf),
            sendThrowable = com.quellkern.nachweis.issuance.UserAuthException("authentication error 13"),
        )
        val controller = controller(gateway, this)

        controller.onRequest("openid4vp://request")
        advanceUntilIdle()
        controller.confirm()
        advanceUntilIdle()

        val state = controller.state.value
        assertTrue(state is PresentationState.Rejected)
        assertEquals(PresentationError.UserAuthDeclined, (state as PresentationState.Rejected).error)
    }

    @Test
    fun `an unclassified send failure maps to Unexpected without leaking the cause`() = runTest {
        val secret = "family_name=Mustermann"
        val gateway = RecordingGateway(
            PresentationFixtures.signedRequest(leaf),
            sendThrowable = IllegalStateException(secret),
        )
        val controller = controller(gateway, this)

        controller.onRequest("openid4vp://request")
        advanceUntilIdle()
        controller.confirm()
        advanceUntilIdle()

        val state = controller.state.value
        assertTrue(state is PresentationState.Rejected)
        val error = (state as PresentationState.Rejected).error
        assertTrue(error is PresentationError.Unexpected)
        assertTrue("cause is retained for triage", (error as PresentationError.Unexpected).cause is IllegalStateException)
        assertFalse("display copy must not echo the cause", error.publicMessage.contains("Mustermann"))
    }
}
