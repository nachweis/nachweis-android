package com.quellkern.nachweis.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * The D1 registration evaluator in isolation (dev-plan.md Workstream D): WRPRC extraction from
 * `verifier_info`, layer-(b) verification, layer-(c) binding, and the DCQL registration diff,
 * producing either a rejection or a consent-time [RegistrationVerdict]. The verdict label for the
 * outside case is fixed — *"outside this verifier's registration"*, never "over-ask".
 */
class RegistrationEvaluatorTest {

    private val providerCa = PresentationFixtures.newCa(commonName = "nachweis Test WRPRC Provider CA")
    private val provider = PresentationFixtures.newLeaf(providerCa, sanDns = "wrprc.nachweis.tech")
    private val now = Date()

    private fun evaluator(status: WrprcStatusSource = PresentationFixtures.wrprcStatusGood()) =
        DefaultRegistrationEvaluator(
            wrprcValidator = WrprcValidator(TrustStore(listOf(providerCa.certificate)), status),
            clock = { now },
        )

    /** A B5-validated request carrying [verifierInfo], the WRPAC identifiers, and requested claims. */
    private fun request(
        verifierInfo: String?,
        wrpIds: List<String> = listOf(PresentationFixtures.WRP_ID),
        requestedClaims: List<String> = listOf("given_name", "family_name"),
        vct: String = PresentationRequestValidator.SUPPORTED_VCT,
    ) = ValidatedPresentationRequest(
        verifierIdentity = "verifier-sandbox.nachweis.tech",
        responseOrigin = "https://verifier-sandbox.nachweis.tech",
        responseUri = "https://verifier-sandbox.nachweis.tech/response",
        purpose = "To confirm your name",
        vct = vct,
        requestedClaims = requestedClaims.map { RequestedClaim(it) },
        nonce = "n-0S6",
        verifierInfo = verifierInfo,
        verifierWrpIdentifiers = wrpIds,
    )

    private fun assertReject(error: PresentationError, outcome: RegistrationOutcome) {
        assertTrue("expected Reject but was $outcome", outcome is RegistrationOutcome.Reject)
        assertEquals(error, (outcome as RegistrationOutcome.Reject).error)
    }

    private fun verdictOf(outcome: RegistrationOutcome): RegistrationVerdict {
        assertTrue("expected Proceed but was $outcome", outcome is RegistrationOutcome.Proceed)
        return (outcome as RegistrationOutcome.Proceed).verdict
    }

    @Test
    fun `within the registered set yields InsideRegistration`() {
        val info = PresentationFixtures.verifierInfoJwt(PresentationFixtures.wrprcJwt(provider))
        val verdict = verdictOf(evaluator().evaluate(request(info)))
        assertEquals(RegistrationVerdict.InsideRegistration, verdict)
    }

    @Test
    fun `a claim beyond the registered set is labeled outside registration`() {
        // WRPRC registers only given_name/family_name; the request also asks for birth_date.
        val info = PresentationFixtures.verifierInfoJwt(PresentationFixtures.wrprcJwt(provider))
        val verdict = verdictOf(
            evaluator().evaluate(request(info, requestedClaims = listOf("given_name", "birth_date"))),
        )
        assertTrue(verdict is RegistrationVerdict.OutsideRegistration)
        assertEquals(listOf("birth_date"), (verdict as RegistrationVerdict.OutsideRegistration).claimsOutside)
    }

    @Test
    fun `a vct not registered puts every requested claim outside`() {
        val info = PresentationFixtures.verifierInfoJwt(
            PresentationFixtures.wrprcJwt(provider, vct = "urn:eudi:pid:de:0"),
        )
        val verdict = verdictOf(evaluator().evaluate(request(info)))
        assertTrue(verdict is RegistrationVerdict.OutsideRegistration)
        assertEquals(listOf("given_name", "family_name"), (verdict as RegistrationVerdict.OutsideRegistration).claimsOutside)
    }

    @Test
    fun `a missing WRPRC is rejected`() =
        assertReject(PresentationError.RegistrationMissing, evaluator().evaluate(request(null)))

    @Test
    fun `a CWT WRPRC is rejected as an unsupported profile`() =
        assertReject(PresentationError.UnsupportedRegistrationProfile, evaluator().evaluate(request(PresentationFixtures.verifierInfoCwt())))

    @Test
    fun `an unrelated WRP identifier fails binding`() {
        val info = PresentationFixtures.verifierInfoJwt(PresentationFixtures.wrprcJwt(provider))
        val outcome = evaluator().evaluate(request(info, wrpIds = listOf(PresentationFixtures.OTHER_WRP_ID)))
        assertReject(PresentationError.RegistrationBindingMismatch, outcome)
    }

    @Test
    fun `a revoked WRPRC is rejected`() {
        val info = PresentationFixtures.verifierInfoJwt(PresentationFixtures.wrprcJwt(provider))
        assertReject(PresentationError.RegistrationRevoked, evaluator(PresentationFixtures.wrprcStatusRevoked()).evaluate(request(info)))
    }

    @Test
    fun `an expired WRPRC is rejected`() {
        val past = System.currentTimeMillis() / 1000 - 100
        val info = PresentationFixtures.verifierInfoJwt(
            PresentationFixtures.wrprcJwt(provider, iatEpochSeconds = past - 3600, expEpochSeconds = past),
        )
        assertReject(PresentationError.RegistrationExpired, evaluator().evaluate(request(info)))
    }

    @Test
    fun `the same WRP identifier on a different WRPAC instance still binds and passes`() {
        // The WRPRC names WRP_ID; the WRPAC (via verifierWrpIdentifiers) is a different instance
        // carrying the same WRP_ID. Binding is by identity, so it passes.
        val info = PresentationFixtures.verifierInfoJwt(PresentationFixtures.wrprcJwt(provider, wrpIds = listOf(PresentationFixtures.WRP_ID)))
        val verdict = verdictOf(evaluator().evaluate(request(info, wrpIds = listOf(PresentationFixtures.WRP_ID))))
        assertEquals(RegistrationVerdict.InsideRegistration, verdict)
    }
}
