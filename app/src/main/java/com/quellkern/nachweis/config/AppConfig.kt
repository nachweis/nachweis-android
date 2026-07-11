package com.quellkern.nachweis.config

import com.quellkern.nachweis.BuildConfig

/**
 * Environment configuration surfaced from the active product flavor's BuildConfig.
 * Endpoints and trust roots differ per flavor (demo vs production) and must never be
 * shared or hard-coded outside the flavor configuration in app/build.gradle.kts.
 */
object AppConfig {
    val issuerBaseUrl: String = BuildConfig.ISSUER_BASE_URL
    val verifierBaseUrl: String = BuildConfig.VERIFIER_BASE_URL
    val trustAnchorsResourceName: String = BuildConfig.TRUST_ANCHORS_RES
    val wrprcTrustAnchorsResourceName: String = BuildConfig.WRPRC_TRUST_ANCHORS_RES
    val applicationId: String = BuildConfig.APPLICATION_ID

    /**
     * Public signed WRPRC status-list URLs the app refreshes out of band, parsed from the
     * comma-separated flavor field. Empty when none are published (production placeholder), in
     * which case every WRPRC status lookup fails closed.
     */
    val wrprcStatusListUrls: List<String> =
        BuildConfig.WRPRC_STATUS_LIST_URLS.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Public WRPAC revocation list (CRL) URL refreshed out of band, and the raw-resource name of
     * the public WRPAC-provider issuer certificate used to verify it. Both empty on the production
     * placeholder, in which case the access-cert revocation check fails closed.
     */
    val wrpacCrlUrl: String = BuildConfig.WRPAC_CRL_URL
    val wrpacIssuerCertResourceName: String = BuildConfig.WRPAC_ISSUER_CERT_RES

    /**
     * Structural validity of the resolved flavor configuration: both endpoints are
     * https, issuer and verifier are distinct hosts, and a trust-root resource is named.
     */
    fun isValid(): Boolean =
        issuerBaseUrl.startsWith("https://") &&
            verifierBaseUrl.startsWith("https://") &&
            issuerBaseUrl != verifierBaseUrl &&
            trustAnchorsResourceName.isNotBlank()
}
