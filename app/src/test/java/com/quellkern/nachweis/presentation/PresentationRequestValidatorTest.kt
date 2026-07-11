package com.quellkern.nachweis.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * The B5 access/request-layer validation matrix (dev-plan.md D1 layer (a)). Every case feeds
 * the pure [PresentationRequestValidator] a signed request built from throwaway keys plus a
 * local trust store and status source; no network, no wallet-core, no Android. The validator
 * has no network seam at all, so "zero network calls during consent" holds by construction —
 * [PresentationControllerTest] additionally asserts the flow makes none between arrival and
 * disclosure.
 */
class PresentationRequestValidatorTest {

    private val now = Date()
    private val ca = PresentationFixtures.newCa()
    private val leaf = PresentationFixtures.newLeaf(ca)
    private val trust = TrustStore(listOf(ca.certificate))

    private fun validator(status: RequestStatusSource = PresentationFixtures.statusGood(leaf)) =
        PresentationRequestValidator(trust, status)

    private fun validate(
        jws: String,
        status: RequestStatusSource = PresentationFixtures.statusGood(leaf),
    ): PresentationValidation = validator(status).validate(SignedPresentationRequest(jws), now)

    // --- passing case -------------------------------------------------------------------

    @Test
    fun `valid signed request with trusted current cert and good status passes`() {
        val result = validate(PresentationFixtures.signedRequest(leaf))
        assertTrue(result is PresentationValidation.Valid)
        val request = (result as PresentationValidation.Valid).request
        assertEquals("verifier-sandbox.nachweis.tech", request.verifierIdentity)
        assertEquals(PresentationRequestValidator.SUPPORTED_VCT, request.vct)
        assertEquals(listOf("given_name", "family_name"), request.requestedClaims.map { it.path })
        assertEquals("https://verifier-sandbox.nachweis.tech", request.responseOrigin)
        // B5 never evaluates registration; that is D1's job.
        assertEquals(RegistrationVerdict.NotEvaluated, request.registrationVerdict)
    }

    @Test
    fun `x509_hash client id binding passes when the hash matches the leaf`() {
        val jws = PresentationFixtures.signedRequest(leaf, clientId = PresentationFixtures.x509HashClientId(leaf))
        assertTrue(validate(jws) is PresentationValidation.Valid)
    }

    // --- signature / structure ----------------------------------------------------------

    @Test
    fun `an unsigned token is rejected as not signed`() {
        assertRejected(validate(PresentationFixtures.unsignedToken()), PresentationError.NotSigned)
    }

    @Test
    fun `a non-JWS blob is rejected as not signed`() {
        assertRejected(validate("this is not a request object"), PresentationError.NotSigned)
    }

    @Test
    fun `a tampered signature is rejected`() {
        val jws = PresentationFixtures.corruptSignature(PresentationFixtures.signedRequest(leaf))
        assertRejected(validate(jws), PresentationError.BadSignature)
    }

    // --- trust / validity / status ------------------------------------------------------

    @Test
    fun `a leaf from an untrusted CA is rejected`() {
        val otherCa = PresentationFixtures.newCa(commonName = "Rogue CA")
        val rogueLeaf = PresentationFixtures.newLeaf(otherCa)
        // Signed correctly, but its chain does not reach our anchor.
        assertRejected(validate(PresentationFixtures.signedRequest(rogueLeaf)), PresentationError.Untrusted)
    }

    @Test
    fun `an expired leaf is rejected`() {
        val expired = PresentationFixtures.newLeaf(ca, notBeforeDays = -10, notAfterDays = -1)
        assertRejected(validate(PresentationFixtures.signedRequest(expired)), PresentationError.CertificateExpired)
    }

    @Test
    fun `a revoked WRPAC is rejected`() {
        assertRejected(
            validate(PresentationFixtures.signedRequest(leaf), status = PresentationFixtures.statusRevoked(leaf)),
            PresentationError.Revoked,
        )
    }

    @Test
    fun `unknown status fails closed`() {
        assertRejected(
            validate(PresentationFixtures.signedRequest(leaf), status = PresentationFixtures.statusUnknown()),
            PresentationError.StatusUnavailable,
        )
    }

    @Test
    fun `an empty trust store trusts nothing`() {
        val emptyValidator = PresentationRequestValidator(TrustStore(emptyList()), PresentationFixtures.statusGood(leaf))
        val result = emptyValidator.validate(SignedPresentationRequest(PresentationFixtures.signedRequest(leaf)), now)
        assertRejected(result, PresentationError.Untrusted)
    }

    // --- client-id binding --------------------------------------------------------------

    @Test
    fun `a client id whose SAN does not match the certificate is rejected`() {
        val jws = PresentationFixtures.signedRequest(leaf, clientId = "x509_san_dns:attacker.example.com")
        assertRejected(validate(jws), PresentationError.ClientIdMismatch)
    }

    @Test
    fun `a wrong x509_hash client id is rejected`() {
        val jws = PresentationFixtures.signedRequest(leaf, clientId = "x509_hash:AAAAdefinitelyNotTheHash")
        assertRejected(validate(jws), PresentationError.ClientIdMismatch)
    }

    // --- DCQL subset --------------------------------------------------------------------

    @Test
    fun `a DCQL query with two credentials is rejected as unsupported`() {
        val twoCreds = """
            {"credentials":[
            {"id":"a","format":"dc+sd-jwt","meta":{"vct_values":["${PresentationRequestValidator.SUPPORTED_VCT}"]},"claims":[{"path":["given_name"]}]},
            {"id":"b","format":"dc+sd-jwt","meta":{"vct_values":["${PresentationRequestValidator.SUPPORTED_VCT}"]},"claims":[{"path":["family_name"]}]}]}
        """.trimIndent().replace("\n", "")
        assertRejected(validate(PresentationFixtures.signedRequest(leaf, dcqlJson = twoCreds)), PresentationError.UnsupportedQuery)
    }

    @Test
    fun `a DCQL query with claim_sets is rejected as unsupported`() {
        val withClaimSets = """
            {"credentials":[{"id":"pid","format":"dc+sd-jwt","meta":{"vct_values":["${PresentationRequestValidator.SUPPORTED_VCT}"]},
            "claims":[{"id":"n","path":["given_name"]}],"claim_sets":[["n"]]}]}
        """.trimIndent().replace("\n", "")
        assertRejected(validate(PresentationFixtures.signedRequest(leaf, dcqlJson = withClaimSets)), PresentationError.UnsupportedQuery)
    }

    @Test
    fun `a DCQL query with credential_sets is rejected as unsupported`() {
        val withCredentialSets = """
            {"credentials":[{"id":"pid","format":"dc+sd-jwt","meta":{"vct_values":["${PresentationRequestValidator.SUPPORTED_VCT}"]},"claims":[{"path":["given_name"]}]}],
            "credential_sets":[{"options":[["pid"]]}]}
        """.trimIndent().replace("\n", "")
        assertRejected(validate(PresentationFixtures.signedRequest(leaf, dcqlJson = withCredentialSets)), PresentationError.UnsupportedQuery)
    }

    @Test
    fun `a DCQL query with a per-claim value restriction is rejected as unsupported`() {
        val withValues = """
            {"credentials":[{"id":"pid","format":"dc+sd-jwt","meta":{"vct_values":["${PresentationRequestValidator.SUPPORTED_VCT}"]},
            "claims":[{"path":["nationality"],"values":["DE"]}]}]}
        """.trimIndent().replace("\n", "")
        assertRejected(validate(PresentationFixtures.signedRequest(leaf, dcqlJson = withValues)), PresentationError.UnsupportedQuery)
    }

    @Test
    fun `a request for a different vct is rejected as unsupported credential`() {
        val otherVct = PresentationFixtures.pidDcql(vct = "urn:eudi:pid:1")
        assertRejected(validate(PresentationFixtures.signedRequest(leaf, dcqlJson = otherVct)), PresentationError.UnsupportedCredential)
    }

    // --- verifier_info passthrough (D1 seam) --------------------------------------------

    @Test
    fun `verifier_info is carried through verbatim for D1 but does not affect the B5 verdict`() {
        val verifierInfo = """[{"format":"rc-wrp+jwt","data":"eyJ...opaque"}]"""
        val result = validate(PresentationFixtures.signedRequest(leaf, verifierInfoJson = verifierInfo))
        assertTrue(result is PresentationValidation.Valid)
        val request = (result as PresentationValidation.Valid).request
        assertTrue(request.verifierInfo != null && request.verifierInfo!!.contains("rc-wrp+jwt"))
        assertEquals(RegistrationVerdict.NotEvaluated, request.registrationVerdict)
    }

    @Test
    fun `a valid request without verifier_info still passes with a null passthrough`() {
        val result = validate(PresentationFixtures.signedRequest(leaf, verifierInfoJson = null))
        assertTrue(result is PresentationValidation.Valid)
        assertEquals(null, (result as PresentationValidation.Valid).request.verifierInfo)
    }

    private fun assertRejected(result: PresentationValidation, expected: PresentationError) {
        assertTrue("expected Invalid but was $result", result is PresentationValidation.Invalid)
        assertEquals(expected, (result as PresentationValidation.Invalid).error)
    }

    @Test
    fun `the passing and failing cases are genuinely distinct`() {
        // Guards against a fixture that trivially passes or fails everything.
        assertTrue(validate(PresentationFixtures.signedRequest(leaf)) is PresentationValidation.Valid)
        assertFalse(validate("nonsense") is PresentationValidation.Valid)
    }
}
