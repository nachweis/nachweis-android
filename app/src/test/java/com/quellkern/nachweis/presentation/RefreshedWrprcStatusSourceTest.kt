package com.quellkern.nachweis.presentation

import org.junit.Assert.assertEquals
import java.util.Date
import org.junit.Test

/**
 * The consent-time read side: given verified, decoded status lists with freshness deadlines,
 * [RefreshedWrprcStatusSource] resolves a WRPRC's pointer without any network and fails closed on
 * three independent grounds (no list, stale list, index out of range).
 */
class RefreshedWrprcStatusSourceTest {

    private val provider = PresentationFixtures.newCa("nachweis Test Status Provider CA")
    private val signer = PresentationFixtures.newLeaf(provider, sanDns = "status.test.invalid")
    private val trust = TrustStore(listOf(provider.certificate))
    private val uri = "https://verifier-sandbox.nachweis.tech/trust/status/list-1.jwt"

    // A verified list where index 5 is revoked and index 0 is valid.
    private fun list(): VerifiedStatusList {
        val token = PresentationFixtures.statusListJwt(signer, sub = uri, revokedIndices = setOf(5))
        val result = StatusListVerifier(trust).verify(token, expectedSub = uri, now = Date())
        return (result as StatusListVerification.Valid).list
    }

    private fun source(freshUntil: Long, now: Long): RefreshedWrprcStatusSource =
        RefreshedWrprcStatusSource(
            lists = mapOf(uri to RefreshedWrprcStatusSource.CachedStatusList(list(), freshUntil)),
            clock = { now },
        )

    @Test
    fun freshValidEntry_isGood() {
        val src = source(freshUntil = 2_000, now = 1_000)
        assertEquals(CertStatus.Good, src.statusOf(WrprcStatusRef(uri, 0)))
    }

    @Test
    fun freshRevokedEntry_isRevoked() {
        val src = source(freshUntil = 2_000, now = 1_000)
        assertEquals(CertStatus.Revoked, src.statusOf(WrprcStatusRef(uri, 5)))
    }

    @Test
    fun staleEntry_failsClosed() {
        val src = source(freshUntil = 1_000, now = 5_000)
        assertEquals(CertStatus.Unknown, src.statusOf(WrprcStatusRef(uri, 0)))
    }

    @Test
    fun unknownUri_failsClosed() {
        val src = source(freshUntil = 2_000, now = 1_000)
        assertEquals(CertStatus.Unknown, src.statusOf(WrprcStatusRef("https://elsewhere.invalid/list.jwt", 0)))
    }

    @Test
    fun indexOutOfRange_failsClosed() {
        val src = source(freshUntil = 2_000, now = 1_000)
        assertEquals(CertStatus.Unknown, src.statusOf(WrprcStatusRef(uri, 100_000)))
    }

    @Test
    fun emptySource_failsClosed() {
        val src = RefreshedWrprcStatusSource(emptyMap(), clock = { 1_000 })
        assertEquals(CertStatus.Unknown, src.statusOf(WrprcStatusRef(uri, 0)))
    }

    @Test
    fun swappable_startsUnknown_thenReflectsSwap() {
        val swappable = SwappableWrprcStatusSource()
        assertEquals(CertStatus.Unknown, swappable.statusOf(WrprcStatusRef(uri, 0)))
        swappable.set(source(freshUntil = 2_000, now = 1_000))
        assertEquals(CertStatus.Good, swappable.statusOf(WrprcStatusRef(uri, 0)))
        assertEquals(CertStatus.Revoked, swappable.statusOf(WrprcStatusRef(uri, 5)))
    }
}
