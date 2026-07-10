package com.quellkern.nachweis.issuance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferEvaluationTest {

    private val allowlist = IssuerAllowlist(listOf("https://api-sandbox.nachweis.tech"))

    private fun pid(vct: String = OfferEvaluation.EXPECTED_VCT) = OfferedCredential(
        configurationIdentifier = "pid-no-key",
        vct = vct,
        docType = null,
        displayName = "PID",
    )

    private fun offer(
        issuer: String = "https://api-sandbox.nachweis.tech/issuers/nachweis",
        creds: List<OfferedCredential> = listOf(pid()),
    ) = ResolvedOffer(issuer, creds, requiresTransactionCode = false)

    @Test
    fun `allowlisted offer with expected PID is acceptable`() {
        val decision = OfferEvaluation.evaluate(offer(), allowlist)
        assertTrue(decision is OfferDecision.Acceptable)
        assertEquals("pid-no-key", (decision as OfferDecision.Acceptable).credential.configurationIdentifier)
    }

    @Test
    fun `offer from an unlisted issuer is declined before format is considered`() {
        val decision = OfferEvaluation.evaluate(offer(issuer = "https://evil.example.com/x"), allowlist)
        assertTrue(decision is OfferDecision.NotAllowlisted)
    }

    @Test
    fun `allowlisted issuer without the expected PID is unsupported`() {
        val other = pid(vct = "urn:eudi:pid:1") // the patched vct we must NOT accept
        val decision = OfferEvaluation.evaluate(offer(creds = listOf(other)), allowlist)
        assertTrue(decision is OfferDecision.UnsupportedCredential)
        assertEquals(listOf("urn:eudi:pid:1"), (decision as OfferDecision.UnsupportedCredential).offeredVcts)
    }

    @Test
    fun `mdoc-only offer is unsupported for this SD-JWT-first slice`() {
        val mdoc = OfferedCredential("pid-mdoc", vct = null, docType = "eu.europa.ec.eudi.pid.1", displayName = "PID mdoc")
        val decision = OfferEvaluation.evaluate(offer(creds = listOf(mdoc)), allowlist)
        assertTrue(decision is OfferDecision.UnsupportedCredential)
    }

    @Test
    fun `the expected PID is selected even when mixed with other credentials`() {
        val mdoc = OfferedCredential("pid-mdoc", vct = null, docType = "eu.europa.ec.eudi.pid.1", displayName = "PID mdoc")
        val decision = OfferEvaluation.evaluate(offer(creds = listOf(mdoc, pid())), allowlist)
        assertTrue(decision is OfferDecision.Acceptable)
        assertEquals(OfferEvaluation.EXPECTED_VCT, (decision as OfferDecision.Acceptable).credential.vct)
    }
}
