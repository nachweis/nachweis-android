package com.quellkern.nachweis.presentation

/**
 * A [WrprcStatusSource] backed by verified status lists that [StatusListRefresher] fetched out of
 * band. Each list is held with a freshness deadline; a consent-time lookup reads only the decoded
 * bits and the clock (no network), so the D1 rule "consent makes zero network calls" is preserved
 * while the answer still reflects a recently refreshed signed status list.
 *
 * Fail-closed is retained on three independent grounds, each returning [CertStatus.Unknown]:
 * no list cached for the pointer's URI, the cached list is past its freshness deadline, or the
 * pointer's index falls outside the list. Only a fresh, covered, zero-valued entry is
 * [CertStatus.Good]; any non-zero entry is [CertStatus.Revoked].
 */
class RefreshedWrprcStatusSource(
    private val lists: Map<String, CachedStatusList>,
    private val clock: () -> Long,
) : WrprcStatusSource {

    /** A verified status list plus the epoch-millis instant after which it is considered stale. */
    data class CachedStatusList(
        val list: VerifiedStatusList,
        val freshUntilEpochMillis: Long,
    )

    override fun statusOf(ref: WrprcStatusRef): CertStatus {
        val cached = lists[ref.uri] ?: return CertStatus.Unknown
        if (clock() > cached.freshUntilEpochMillis) return CertStatus.Unknown
        val value = cached.list.statusAt(ref.idx) ?: return CertStatus.Unknown
        return if (value == 0) CertStatus.Good else CertStatus.Revoked
    }
}

/**
 * A stable [WrprcStatusSource] handed to the validator once, whose backing delegate the refresher
 * swaps atomically after each successful refresh. This lets the presentation graph wire the status
 * source before any list is fetched: it starts as an empty [CachedWrprcStatusSource] (every lookup
 * Unknown, fail-closed) and becomes a [RefreshedWrprcStatusSource] as soon as a refresh completes.
 */
class SwappableWrprcStatusSource(
    initial: WrprcStatusSource = CachedWrprcStatusSource(),
) : WrprcStatusSource {

    @Volatile
    private var delegate: WrprcStatusSource = initial

    /** Replace the backing source; subsequent lookups read [next]. */
    fun set(next: WrprcStatusSource) {
        delegate = next
    }

    override fun statusOf(ref: WrprcStatusRef): CertStatus = delegate.statusOf(ref)
}
