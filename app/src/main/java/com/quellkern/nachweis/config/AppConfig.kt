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
    val applicationId: String = BuildConfig.APPLICATION_ID

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
