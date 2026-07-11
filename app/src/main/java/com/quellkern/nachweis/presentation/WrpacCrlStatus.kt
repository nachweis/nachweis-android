package com.quellkern.nachweis.presentation

import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.util.Date

/** Fetches the DER-encoded CRL served at [uri], or null on any failure. Injected for tests. */
fun interface CrlFetcher {
    fun fetch(uri: String): ByteArray?
}

/**
 * A [RequestStatusSource] backed by a verified WRPAC-provider CRL that [WrpacCrlRefresher] fetched
 * out of band — the access-layer counterpart to [RefreshedWrprcStatusSource]. A consent-time
 * lookup reads only the parsed CRL and the clock (no network), so the D1 rule "consent makes zero
 * network calls" holds while the answer reflects a recently refreshed, signature-verified list.
 *
 * Fail-closed ([CertStatus.Unknown]) on three grounds: the CRL is past its freshness deadline, or
 * the certificate was not issued by the CRL's issuer (outside this list's scope). A certificate in
 * scope is [CertStatus.Revoked] iff the CRL lists its serial, else [CertStatus.Good].
 */
class WrpacCrlStatusSource(
    private val crl: X509CRL,
    private val freshUntilEpochMillis: Long,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RequestStatusSource {
    override fun statusOf(certificate: X509Certificate): CertStatus {
        if (clock() > freshUntilEpochMillis) return CertStatus.Unknown
        if (certificate.issuerX500Principal != crl.issuerX500Principal) return CertStatus.Unknown
        return if (crl.isRevoked(certificate)) CertStatus.Revoked else CertStatus.Good
    }
}

/**
 * A stable [RequestStatusSource] handed to the presentation validator once, whose backing delegate
 * the refresher swaps atomically after each successful refresh. Starts empty (every lookup Unknown,
 * fail-closed) and becomes a [WrpacCrlStatusSource] once a refresh verifies a CRL.
 */
class SwappableRequestStatusSource(
    initial: RequestStatusSource = CachedStatusSource(),
) : RequestStatusSource {
    @Volatile
    private var delegate: RequestStatusSource = initial

    fun set(next: RequestStatusSource) {
        delegate = next
    }

    override fun statusOf(certificate: X509Certificate): CertStatus = delegate.statusOf(certificate)
}

/**
 * The out-of-band WRPAC revocation refresh: the access-layer mirror of [StatusListRefresher]. On an
 * explicit trigger (app start, before a presentation; never during consent) it fetches the DER CRL,
 * verifies it — the issuer cert chains to a bundled WRPAC trust anchor, the CRL is signed under that
 * issuer's key and is temporally current — and publishes a [WrpacCrlStatusSource] into [target]. A
 * fetch or verification failure leaves the prior entry to age out on its own deadline; it is never
 * replaced with a value it could not prove.
 *
 * Freshness is bounded by the smaller of a conservative [maxCacheAgeMillis] and the CRL's own
 * `nextUpdate`, so an aggressive publisher wins and the app fails closed sooner rather than later.
 */
class WrpacCrlRefresher(
    private val fetcher: CrlFetcher,
    private val crlUri: String,
    private val issuerCert: X509Certificate?,
    private val trustStore: TrustStore,
    private val target: SwappableRequestStatusSource,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val maxCacheAgeMillis: Long = DEFAULT_MAX_CACHE_AGE_MILLIS,
) {
    /** True when a CRL URL and issuer cert are configured (production leaves both empty). */
    fun hasWork(): Boolean = crlUri.isNotBlank() && issuerCert != null

    /** Fetch, verify, and publish the CRL as of [now]. Returns true on a successful publish. */
    @Synchronized
    fun refresh(now: Date = Date(clock())): Boolean {
        val issuer = issuerCert ?: return false
        if (crlUri.isBlank()) return false

        // The issuer cert must itself chain to a bundled anchor before we trust the CRL it signs.
        if (!trustStore.validatesPath(listOf(issuer), now)) return false

        val der = fetcher.fetch(crlUri) ?: return false
        val crl = try {
            CertificateFactory.getInstance("X.509").generateCRL(ByteArrayInputStream(der)) as X509CRL
        } catch (_: Exception) {
            return false
        }

        // Issued by the trusted issuer and signed under its key.
        if (crl.issuerX500Principal != issuer.subjectX500Principal) return false
        try {
            crl.verify(issuer.publicKey)
        } catch (_: Exception) {
            return false
        }

        // Temporally current: not future-dated, and not past its nextUpdate.
        crl.thisUpdate?.let { if (it.after(now)) return false }
        val nextUpdate = crl.nextUpdate ?: return false
        if (nextUpdate.before(now)) return false

        val freshUntil = minOf(now.time + maxCacheAgeMillis, nextUpdate.time)
        target.set(WrpacCrlStatusSource(crl, freshUntil, clock))
        return true
    }

    companion object {
        /** Conservative upper bound on cache freshness when the CRL's nextUpdate is further out. */
        const val DEFAULT_MAX_CACHE_AGE_MILLIS: Long = 24L * 60 * 60 * 1000
    }
}
