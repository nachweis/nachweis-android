package com.quellkern.nachweis.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.util.Date
import org.junit.Test

/**
 * Exhaustive checks of [StatusListVerifier] against locally minted `statuslist+jwt` tokens. Every
 * key is generated per run (see [PresentationFixtures]); nothing here is a real anchor. Each test
 * targets one gate so a regression names the exact broken check.
 */
class StatusListVerifierTest {

    private val provider = PresentationFixtures.newCa("nachweis Test Status Provider CA")
    private val signer = PresentationFixtures.newLeaf(provider, sanDns = "status.test.invalid")
    private val trust = TrustStore(listOf(provider.certificate))
    private val now = Date()
    private val sub = "https://verifier-sandbox.nachweis.tech/trust/status/list-1.jwt"

    private fun verifier(anchor: TrustStore = trust) = StatusListVerifier(anchor)

    @Test
    fun validToken_verifiesAndExposesBits() {
        val token = PresentationFixtures.statusListJwt(signer, sub = sub, revokedIndices = setOf(3))
        val result = verifier().verify(token, expectedSub = sub, now = now)
        assertTrue("expected Valid, got $result", result is StatusListVerification.Valid)
        val list = (result as StatusListVerification.Valid).list
        assertEquals(sub, list.uri)
        assertEquals(1, list.bits)
        assertEquals(300L, list.ttlSeconds)
        assertEquals(0, list.statusAt(0))   // clear = valid
        assertEquals(1, list.statusAt(3))   // set = revoked
        assertNull("index past the list is unknown", list.statusAt(9_999))
    }

    @Test
    fun wrongType_isRejected() {
        val token = PresentationFixtures.statusListJwt(signer, sub = sub, typ = "jwt")
        assertEquals(
            StatusListVerification.Invalid(StatusListRejection.NotStatusList),
            verifier().verify(token, expectedSub = sub, now = now),
        )
    }

    @Test
    fun subjectMismatch_isRejected() {
        val token = PresentationFixtures.statusListJwt(signer, sub = sub)
        assertEquals(
            StatusListVerification.Invalid(StatusListRejection.SubjectMismatch),
            verifier().verify(token, expectedSub = "https://verifier-sandbox.nachweis.tech/trust/status/other.jwt", now = now),
        )
    }

    @Test
    fun missingSigningCertificate_isRejected() {
        val token = PresentationFixtures.statusListJwt(signer, sub = sub, includeX5c = false)
        assertEquals(
            StatusListVerification.Invalid(StatusListRejection.NoSigningCertificate),
            verifier().verify(token, expectedSub = sub, now = now),
        )
    }

    @Test
    fun tamperedSignature_isRejected() {
        val token = PresentationFixtures.corruptSignature(PresentationFixtures.statusListJwt(signer, sub = sub))
        assertEquals(
            StatusListVerification.Invalid(StatusListRejection.BadSignature),
            verifier().verify(token, expectedSub = sub, now = now),
        )
    }

    @Test
    fun untrustedAnchor_isRejected() {
        val token = PresentationFixtures.statusListJwt(signer, sub = sub)
        assertEquals(
            StatusListVerification.Invalid(StatusListRejection.UntrustedSigner),
            verifier(TrustStore(emptyList())).verify(token, expectedSub = sub, now = now),
        )
    }

    @Test
    fun expiredSigner_isRejected() {
        val expiredSigner = PresentationFixtures.newLeaf(
            provider, sanDns = "status.test.invalid", notBeforeDays = -10, notAfterDays = -1,
        )
        val token = PresentationFixtures.statusListJwt(expiredSigner, sub = sub)
        assertEquals(
            StatusListVerification.Invalid(StatusListRejection.UntrustedSigner),
            verifier().verify(token, expectedSub = sub, now = now),
        )
    }

    @Test
    fun futureDatedToken_isRejected() {
        val future = now.time / 1000 + 3600
        val token = PresentationFixtures.statusListJwt(signer, sub = sub, iatEpochSeconds = future)
        assertEquals(
            StatusListVerification.Invalid(StatusListRejection.Expired),
            verifier().verify(token, expectedSub = sub, now = now),
        )
    }

    @Test
    fun expiredToken_isRejected() {
        val past = now.time / 1000 - 60
        val token = PresentationFixtures.statusListJwt(
            signer, sub = sub, iatEpochSeconds = now.time / 1000 - 7200, expEpochSeconds = past,
        )
        assertEquals(
            StatusListVerification.Invalid(StatusListRejection.Expired),
            verifier().verify(token, expectedSub = sub, now = now),
        )
    }

    @Test
    fun garbage_isMalformed() {
        assertEquals(
            StatusListVerification.Invalid(StatusListRejection.Malformed),
            verifier().verify("not-a-jwt", expectedSub = sub, now = now),
        )
    }
}
