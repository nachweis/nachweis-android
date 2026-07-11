package com.quellkern.nachweis.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * Regression coverage for the transient "registration status can't be confirmed" rejection seen in
 * rehearsal: 1 of 4 live presentations failed while every status endpoint returned 200, and an
 * immediate retry succeeded.
 *
 * Root cause is a cold-cache / stale-TTL race. The WRPRC status list is refreshed out of band and
 * read during consent from cache only ([RefreshedWrprcStatusSource]); a not-yet-populated or
 * just-expired entry resolves to [CertStatus.Unknown], which the validator correctly fails closed
 * ([WrprcRejection.StatusUnavailable] → [PresentationError.RegistrationStatusUnavailable]). On
 * arrival the refresh was fired but **not awaited**, so when [DefaultRegistrationEvaluator] read the
 * cache before that refresh landed, it saw Unknown and rejected — a spurious rejection that the next
 * attempt cleared because the refresh had since published.
 *
 * The fix routes the arrival refresh through the controller's `prepareStatus` hook, which the
 * controller **awaits** (bounded, in the pre-consent Resolving phase) before registration
 * evaluation. These tests pin both the failure path and the fix, without any network.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationStatusRefreshRaceTest {

    private val now = Date()

    private val wrpacCa = PresentationFixtures.newCa(commonName = "nachweis Test WRPAC CA")
    private val wrpac = PresentationFixtures.newLeaf(wrpacCa, organizationIdentifier = PresentationFixtures.WRP_ID)
    private val providerCa = PresentationFixtures.newCa(commonName = "nachweis Test WRPRC Provider CA")
    private val provider = PresentationFixtures.newLeaf(providerCa, sanDns = "wrprc.nachweis.tech")

    private class RecordingGateway(private val signed: String) : Oid4vpGateway {
        var obtainCalls = 0
        var sendCalls = 0
        override suspend fun obtainSignedRequest(requestUri: String) =
            SignedPresentationRequest(signed).also { obtainCalls++ }
        override suspend fun sendResponse(request: ValidatedPresentationRequest, disclosedClaims: List<RequestedClaim>) { sendCalls++ }
        override fun reject() {}
    }

    private fun signedRequest(): String = PresentationFixtures.signedRequest(
        leaf = wrpac,
        dcqlJson = PresentationFixtures.pidDcql(claims = listOf("given_name", "family_name")),
        verifierInfoJson = PresentationFixtures.verifierInfoJwt(PresentationFixtures.wrprcJwt(provider)),
    )

    private fun controller(
        scope: CoroutineScope,
        gateway: Oid4vpGateway,
        wrprcStatus: WrprcStatusSource,
        prepareStatus: suspend () -> Unit = {},
    ): PresentationController {
        val validator = PresentationRequestValidator(
            TrustStore(listOf(wrpacCa.certificate)),
            PresentationFixtures.statusGood(wrpac),
        )
        val evaluator = DefaultRegistrationEvaluator(
            WrprcValidator(TrustStore(listOf(providerCa.certificate)), wrprcStatus),
            clock = { now },
        )
        return PresentationController(
            gateway, validator, scope, clock = { now },
            registrationEvaluator = evaluator, prepareStatus = prepareStatus,
        )
    }

    /**
     * The bug's failure path: the arrival refresh has not populated the cache (empty snapshot,
     * every lookup Unknown), so an otherwise-valid, in-scope request is rejected with the exact
     * user-facing reason from the rehearsal.
     */
    @Test
    fun `an empty WRPRC status cache yields the transient registration-status rejection`() = runTest {
        val gateway = RecordingGateway(signedRequest())
        val c = controller(this, gateway, wrprcStatus = SwappableWrprcStatusSource())
        c.onRequest("openid4vp://r"); advanceUntilIdle()

        val state = c.state.value
        assertTrue("expected Rejected but was $state", state is PresentationState.Rejected)
        assertEquals(PresentationError.RegistrationStatusUnavailable, (state as PresentationState.Rejected).error)
        assertEquals(
            "This verifier's registration status can't be confirmed right now.",
            state.error.publicMessage,
        )
    }

    /**
     * The fix: the same empty cache, but the arrival refresh (modelled by `prepareStatus`) publishes
     * the verified status list into the swappable source before returning. Because the controller
     * awaits `prepareStatus` before evaluating, the status lookup now reads Good and the request
     * reaches consent inside its registration — the retry-succeeds behaviour, now on the first try.
     */
    @Test
    fun `awaiting the arrival refresh lets the same request pass on the first try`() = runTest {
        val gateway = RecordingGateway(signedRequest())
        val cache = SwappableWrprcStatusSource()
        val c = controller(this, gateway, wrprcStatus = cache) {
            // Stand-in for the out-of-band refresh landing: publish a verified, Good status list.
            cache.set(PresentationFixtures.wrprcStatusGood())
        }
        c.onRequest("openid4vp://r"); advanceUntilIdle()

        val state = c.state.value
        assertTrue("expected AwaitingConsent but was $state", state is PresentationState.AwaitingConsent)
        assertEquals(
            RegistrationVerdict.InsideRegistration,
            (state as PresentationState.AwaitingConsent).request.registrationVerdict,
        )
        // Consent was reached with only the single arrival fetch — no network during consent.
        assertEquals(1, gateway.obtainCalls)
        assertEquals(0, gateway.sendCalls)
    }

    /**
     * Proves the ordering guarantee that closes the race: the controller does not evaluate the
     * registration until the arrival refresh has completed. While the refresh is still in flight the
     * flow stays in Resolving (not a premature Unknown-driven rejection); only once the refresh
     * publishes and completes does it advance to consent.
     */
    @Test
    fun `evaluation waits for an in-flight refresh instead of reading the cache early`() = runTest {
        val gateway = RecordingGateway(signedRequest())
        val cache = SwappableWrprcStatusSource()
        val refreshLanded = CompletableDeferred<Unit>()
        val c = controller(this, gateway, wrprcStatus = cache) {
            refreshLanded.await()
            cache.set(PresentationFixtures.wrprcStatusGood())
        }

        c.onRequest("openid4vp://r")
        advanceUntilIdle()
        // Request fetched and access-layer validated, but the refresh has not landed: the controller
        // is parked awaiting it rather than having read the cold cache as Unknown and rejected.
        assertEquals(PresentationState.Resolving, c.state.value)

        refreshLanded.complete(Unit)
        advanceUntilIdle()
        val state = c.state.value
        assertTrue("expected AwaitingConsent but was $state", state is PresentationState.AwaitingConsent)
        assertEquals(
            RegistrationVerdict.InsideRegistration,
            (state as PresentationState.AwaitingConsent).request.registrationVerdict,
        )
    }
}
