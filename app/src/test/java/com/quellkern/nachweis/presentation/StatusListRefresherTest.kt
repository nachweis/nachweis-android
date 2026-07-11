package com.quellkern.nachweis.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.util.Date
import org.junit.Test

/**
 * The orchestration seam: [StatusListRefresher] fetches each configured list, verifies it, and
 * publishes the verified bits into a [SwappableWrprcStatusSource]. Uses an in-memory fetcher and
 * a fixed clock so no network is touched; the freshness/fail-closed rules are asserted directly.
 */
class StatusListRefresherTest {

    private val provider = PresentationFixtures.newCa("nachweis Test Status Provider CA")
    private val signer = PresentationFixtures.newLeaf(provider, sanDns = "status.test.invalid")
    private val trust = TrustStore(listOf(provider.certificate))
    private val verifier = StatusListVerifier(trust)

    private val validUri = "https://verifier-sandbox.nachweis.tech/trust/status/valid.jwt"
    private val revokedUri = "https://verifier-sandbox.nachweis.tech/trust/status/revoked.jwt"

    private val nowMillis = 1_800_000_000_000L
    private val now = Date(nowMillis)
    private val iat = nowMillis / 1000 - 60

    private fun fetcherOf(vararg pairs: Pair<String, String?>): StatusListFetcher {
        val map = pairs.toMap()
        return StatusListFetcher { uri -> map[uri] }
    }

    @Test
    fun refresh_publishesVerifiedGoodAndRevoked() {
        val validToken = PresentationFixtures.statusListJwt(signer, sub = validUri, iatEpochSeconds = iat)
        val revokedToken = PresentationFixtures.statusListJwt(signer, sub = revokedUri, revokedIndices = setOf(1), iatEpochSeconds = iat)
        val target = SwappableWrprcStatusSource()
        val refresher = StatusListRefresher(
            fetcher = fetcherOf(validUri to validToken, revokedUri to revokedToken),
            statusVerifier = verifier,
            statusListUris = listOf(validUri, revokedUri),
            target = target,
            clock = { nowMillis },
        )

        val outcome = refresher.refresh(now)

        assertEquals(2, outcome.requested)
        assertEquals(2, outcome.verified)
        assertTrue(outcome.failed.isEmpty())
        assertEquals(CertStatus.Good, target.statusOf(WrprcStatusRef(validUri, 0)))
        assertEquals(CertStatus.Revoked, target.statusOf(WrprcStatusRef(revokedUri, 1)))
    }

    @Test
    fun ttlBoundsFreshness_soAStaleClockFailsClosed() {
        // ttl 300s; a source read 301s after the refresh is stale → Unknown.
        val token = PresentationFixtures.statusListJwt(signer, sub = validUri, ttlSeconds = 300, iatEpochSeconds = iat)
        var clockMillis = nowMillis
        val target = SwappableWrprcStatusSource()
        val refresher = StatusListRefresher(
            fetcher = fetcherOf(validUri to token),
            statusVerifier = verifier,
            statusListUris = listOf(validUri),
            target = target,
            clock = { clockMillis },
        )

        refresher.refresh(now)
        assertEquals(CertStatus.Good, target.statusOf(WrprcStatusRef(validUri, 0)))

        clockMillis = nowMillis + 301_000
        assertEquals(CertStatus.Unknown, target.statusOf(WrprcStatusRef(validUri, 0)))
    }

    @Test
    fun failedFetch_leavesNoGoodAndReportsFailure() {
        val target = SwappableWrprcStatusSource()
        val refresher = StatusListRefresher(
            fetcher = fetcherOf(validUri to null),
            statusVerifier = verifier,
            statusListUris = listOf(validUri),
            target = target,
            clock = { nowMillis },
        )

        val outcome = refresher.refresh(now)

        assertEquals(0, outcome.verified)
        assertEquals(listOf(validUri), outcome.failed)
        assertEquals(CertStatus.Unknown, target.statusOf(WrprcStatusRef(validUri, 0)))
    }

    @Test
    fun invalidToken_isNotPublishedAsGood() {
        // A token whose sub does not match the requested URI must not populate the cache.
        val wrongSub = PresentationFixtures.statusListJwt(signer, sub = "https://verifier-sandbox.nachweis.tech/trust/status/other.jwt", iatEpochSeconds = iat)
        val target = SwappableWrprcStatusSource()
        val refresher = StatusListRefresher(
            fetcher = fetcherOf(validUri to wrongSub),
            statusVerifier = verifier,
            statusListUris = listOf(validUri),
            target = target,
            clock = { nowMillis },
        )

        val outcome = refresher.refresh(now)

        assertEquals(0, outcome.verified)
        assertEquals(listOf(validUri), outcome.failed)
        assertEquals(CertStatus.Unknown, target.statusOf(WrprcStatusRef(validUri, 0)))
    }

    @Test
    fun noConfiguredUris_hasNoWork() {
        val refresher = StatusListRefresher(
            fetcher = fetcherOf(),
            statusVerifier = verifier,
            statusListUris = emptyList(),
            target = SwappableWrprcStatusSource(),
            clock = { nowMillis },
        )
        assertFalse(refresher.hasWork())
        assertEquals(0, refresher.refresh(now).requested)
    }
}
