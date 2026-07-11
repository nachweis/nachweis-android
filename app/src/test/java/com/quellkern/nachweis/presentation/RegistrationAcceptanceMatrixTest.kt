package com.quellkern.nachweis.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * The dev-plan.md D1 acceptance-test matrix, end to end through the full presentation pipeline:
 * B5's access-layer [PresentationRequestValidator] plus D1's [DefaultRegistrationEvaluator],
 * driven by the real [PresentationController]. This is the normative matrix — including the
 * WRPAC cases (owned by B5) and, most importantly, that **consent evaluation makes zero network
 * calls**: the gateway is touched once on arrival and never again before the user acts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationAcceptanceMatrixTest {

    private val now = Date()

    // WRPAC actor: the verifier's access certificate, carrying the WRP identifier.
    private val wrpacCa = PresentationFixtures.newCa(commonName = "nachweis Test WRPAC CA")
    private val wrpac = PresentationFixtures.newLeaf(wrpacCa, organizationIdentifier = PresentationFixtures.WRP_ID)

    // WRPRC actor: a DISTINCT provider certification authority.
    private val providerCa = PresentationFixtures.newCa(commonName = "nachweis Test WRPRC Provider CA")
    private val provider = PresentationFixtures.newLeaf(providerCa, sanDns = "wrprc.nachweis.tech")

    private class RecordingGateway(private val signed: String) : Oid4vpGateway {
        var obtainCalls = 0
        var sendCalls = 0
        override suspend fun obtainSignedRequest(requestUri: String) = SignedPresentationRequest(signed).also { obtainCalls++ }
        override suspend fun sendResponse(request: ValidatedPresentationRequest, disclosedClaims: List<RequestedClaim>) { sendCalls++ }
        override fun reject() {}
    }

    private fun signedRequest(
        wrprcJwt: String? = PresentationFixtures.wrprcJwt(provider),
        requestedClaims: List<String> = listOf("given_name", "family_name"),
    ): String = PresentationFixtures.signedRequest(
        leaf = wrpac,
        dcqlJson = PresentationFixtures.pidDcql(claims = requestedClaims),
        verifierInfoJson = wrprcJwt?.let { PresentationFixtures.verifierInfoJwt(it) },
    )

    private fun controller(
        scope: CoroutineScope,
        gateway: Oid4vpGateway,
        wrpacStatus: RequestStatusSource = PresentationFixtures.statusGood(wrpac),
        wrprcStatus: WrprcStatusSource = PresentationFixtures.wrprcStatusGood(),
    ): PresentationController {
        val validator = PresentationRequestValidator(TrustStore(listOf(wrpacCa.certificate)), wrpacStatus)
        val evaluator = DefaultRegistrationEvaluator(
            WrprcValidator(TrustStore(listOf(providerCa.certificate)), wrprcStatus),
            clock = { now },
        )
        return PresentationController(gateway, validator, scope, clock = { now }, registrationEvaluator = evaluator)
    }

    private fun consentVerdict(state: PresentationState): RegistrationVerdict {
        assertTrue("expected AwaitingConsent but was $state", state is PresentationState.AwaitingConsent)
        return (state as PresentationState.AwaitingConsent).request.registrationVerdict
    }

    private fun rejection(state: PresentationState): PresentationError {
        assertTrue("expected Rejected but was $state", state is PresentationState.Rejected)
        return (state as PresentationState.Rejected).error
    }

    @Test
    fun `valid WRPAC valid WRPRC cached valid status passes`() = runTest {
        val gateway = RecordingGateway(signedRequest())
        val c = controller(this, gateway)
        c.onRequest("openid4vp://r"); advanceUntilIdle()
        assertEquals(RegistrationVerdict.InsideRegistration, consentVerdict(c.state.value))
    }

    @Test
    fun `revoked WRPRC fails`() = runTest {
        val gateway = RecordingGateway(signedRequest())
        val c = controller(this, gateway, wrprcStatus = PresentationFixtures.wrprcStatusRevoked())
        c.onRequest("openid4vp://r"); advanceUntilIdle()
        assertEquals(PresentationError.RegistrationRevoked, rejection(c.state.value))
    }

    @Test
    fun `revoked WRPAC fails`() = runTest {
        val gateway = RecordingGateway(signedRequest())
        val c = controller(this, gateway, wrpacStatus = PresentationFixtures.statusRevoked(wrpac))
        c.onRequest("openid4vp://r"); advanceUntilIdle()
        assertEquals(PresentationError.Revoked, rejection(c.state.value))
    }

    @Test
    fun `expired WRPAC fails`() = runTest {
        val expiredWrpac = PresentationFixtures.newLeaf(
            wrpacCa, notBeforeDays = -10, notAfterDays = -1, organizationIdentifier = PresentationFixtures.WRP_ID,
        )
        val signed = PresentationFixtures.signedRequest(
            leaf = expiredWrpac,
            verifierInfoJson = PresentationFixtures.verifierInfoJwt(PresentationFixtures.wrprcJwt(provider)),
        )
        val gateway = RecordingGateway(signed)
        val c = controller(this, gateway, wrpacStatus = PresentationFixtures.statusGood(expiredWrpac))
        c.onRequest("openid4vp://r"); advanceUntilIdle()
        assertEquals(PresentationError.CertificateExpired, rejection(c.state.value))
    }

    @Test
    fun `expired WRPRC fails`() = runTest {
        val past = System.currentTimeMillis() / 1000 - 100
        val expired = PresentationFixtures.wrprcJwt(provider, iatEpochSeconds = past - 3600, expEpochSeconds = past)
        val gateway = RecordingGateway(signedRequest(wrprcJwt = expired))
        val c = controller(this, gateway)
        c.onRequest("openid4vp://r"); advanceUntilIdle()
        assertEquals(PresentationError.RegistrationExpired, rejection(c.state.value))
    }

    @Test
    fun `unrelated WRP identifier fails binding`() = runTest {
        // The WRPRC names a different WRP identifier than the WRPAC's organizationIdentifier.
        val mismatchedWrprc = PresentationFixtures.wrprcJwt(provider, wrpIds = listOf(PresentationFixtures.OTHER_WRP_ID))
        val gateway = RecordingGateway(signedRequest(wrprcJwt = mismatchedWrprc))
        val c = controller(this, gateway)
        c.onRequest("openid4vp://r"); advanceUntilIdle()
        assertEquals(PresentationError.RegistrationBindingMismatch, rejection(c.state.value))
    }

    @Test
    fun `same WRP identifier on a different WRPAC instance passes`() = runTest {
        // A freshly minted WRPAC (different key/serial) with the same organizationIdentifier.
        val otherWrpac = PresentationFixtures.newLeaf(
            wrpacCa, sanDns = "verifier-sandbox.nachweis.tech", organizationIdentifier = PresentationFixtures.WRP_ID,
        )
        val signed = PresentationFixtures.signedRequest(
            leaf = otherWrpac,
            verifierInfoJson = PresentationFixtures.verifierInfoJwt(PresentationFixtures.wrprcJwt(provider)),
        )
        val gateway = RecordingGateway(signed)
        val c = controller(this, gateway, wrpacStatus = PresentationFixtures.statusGood(otherWrpac))
        c.onRequest("openid4vp://r"); advanceUntilIdle()
        assertEquals(RegistrationVerdict.InsideRegistration, consentVerdict(c.state.value))
    }

    @Test
    fun `a claim outside the registration is labeled outside registration`() = runTest {
        val gateway = RecordingGateway(signedRequest(requestedClaims = listOf("given_name", "birth_date")))
        val c = controller(this, gateway)
        c.onRequest("openid4vp://r"); advanceUntilIdle()
        val verdict = consentVerdict(c.state.value)
        assertTrue(verdict is RegistrationVerdict.OutsideRegistration)
        assertEquals(listOf("birth_date"), (verdict as RegistrationVerdict.OutsideRegistration).claimsOutside)
    }

    @Test
    fun `consent evaluation makes zero network calls`() = runTest {
        val gateway = RecordingGateway(signedRequest())
        val c = controller(this, gateway)
        c.onRequest("openid4vp://r"); advanceUntilIdle()
        // Reached a verdict (inside registration) with the WRPRC fully verified and bound...
        assertEquals(RegistrationVerdict.InsideRegistration, consentVerdict(c.state.value))
        // ...yet the only gateway/network touch was the single request arrival.
        assertEquals(1, gateway.obtainCalls)
        assertEquals(0, gateway.sendCalls)
    }
}
