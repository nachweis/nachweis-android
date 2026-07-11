package com.quellkern.nachweis.presentation

import java.util.Date

/**
 * Fetches the compact `statuslist+jwt` served at [uri], or null on any failure. Injected so the
 * refresher is exercised in JVM tests with committed public fixtures and no real network. The
 * production implementation ([HttpStatusListFetcher]) performs a plain HTTPS GET of the public
 * artifact and sends no verifier-identifying parameters.
 */
fun interface StatusListFetcher {
    fun fetch(uri: String): String?
}

/** What a refresh round did, for logging and tests. Carries no secret or verifier-keyed data. */
data class StatusRefreshOutcome(
    val requested: Int,
    val verified: Int,
    val failed: List<String>,
)

/**
 * The out-of-band WRPRC status-refresh path (dev-plan.md D1 client side). On an explicit trigger
 * from app scaffolding — app start and before a presentation begins, never during consent — it
 * fetches each configured signed status list, verifies it with [StatusListVerifier], and publishes
 * the verified bits into [target] so consent-time lookups resolve against a fresh, signed list.
 *
 * Freshness is bounded by the smaller of a conservative [maxCacheAgeMillis] and the token's own
 * `ttl`/`exp`, so an aggressive publisher TTL wins and the app fails closed sooner rather than
 * later. A fetch or verification failure leaves the previous entry in place to age out on its own
 * deadline; it is never replaced with a "good" it could not prove. Nothing is ever cached as fresh
 * past its signed lifetime.
 */
class StatusListRefresher(
    private val fetcher: StatusListFetcher,
    private val statusVerifier: StatusListVerifier,
    private val statusListUris: List<String>,
    private val target: SwappableWrprcStatusSource,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val maxCacheAgeMillis: Long = DEFAULT_MAX_CACHE_AGE_MILLIS,
) {
    // The authoritative accumulated map; each refresh overlays freshly verified lists onto it and
    // publishes a snapshot. Guarded by the method-level lock on refresh().
    @Volatile
    private var current: Map<String, RefreshedWrprcStatusSource.CachedStatusList> = emptyMap()

    /** True when there is nothing configured to refresh (production, until lists are published). */
    fun hasWork(): Boolean = statusListUris.isNotEmpty()

    /**
     * Fetch, verify, and publish every configured status list as of [now]. Safe to call
     * repeatedly; concurrent calls are serialized. Returns a summary of the round.
     */
    @Synchronized
    fun refresh(now: Date = Date(clock())): StatusRefreshOutcome {
        if (statusListUris.isEmpty()) return StatusRefreshOutcome(0, 0, emptyList())

        val next = current.toMutableMap()
        val failed = ArrayList<String>()
        var verified = 0

        for (uri in statusListUris) {
            val token = fetcher.fetch(uri)
            if (token == null) {
                failed.add(uri)
                continue
            }
            when (val result = statusVerifier.verify(token, expectedSub = uri, now = now)) {
                is StatusListVerification.Valid -> {
                    next[uri] = RefreshedWrprcStatusSource.CachedStatusList(
                        list = result.list,
                        freshUntilEpochMillis = freshUntil(result.list, now),
                    )
                    verified++
                }
                is StatusListVerification.Invalid -> failed.add(uri)
            }
        }

        current = next
        target.set(RefreshedWrprcStatusSource(next.toMap(), clock))
        return StatusRefreshOutcome(requested = statusListUris.size, verified = verified, failed = failed)
    }

    /**
     * The instant this fetched list stops being fresh: the earliest of the local cap
     * ([maxCacheAgeMillis] from now), the token's `ttl` from now, and the token's absolute `exp`.
     */
    private fun freshUntil(list: VerifiedStatusList, now: Date): Long {
        var deadline = now.time + maxCacheAgeMillis
        list.ttlSeconds?.let { deadline = minOf(deadline, now.time + it * 1000) }
        list.expiresAtEpochSeconds?.let { deadline = minOf(deadline, it * 1000) }
        return deadline
    }

    companion object {
        /** Conservative upper bound on cache freshness when a token carries no shorter `ttl`/`exp`. */
        const val DEFAULT_MAX_CACHE_AGE_MILLIS: Long = 24L * 60 * 60 * 1000
    }
}
