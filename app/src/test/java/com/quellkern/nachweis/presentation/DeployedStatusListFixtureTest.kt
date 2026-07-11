package com.quellkern.nachweis.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.Date
import org.junit.Test

/**
 * Ground-truth interoperability for the status-refresh path: the **real deployed** signed status
 * lists and WRPRCs (fetched from `verifier-sandbox.nachweis.tech/trust`, committed verbatim as
 * public fixtures) drive the **real in-app** verifier and refresher against the **real bundled**
 * demo root. This proves the offline generator (Workstream A) and D1's client side agree on the
 * IETF Token Status List shape, and that a fully populated cache flips the earlier honest boundary
 * (valid WRPRC used to fail closed on an empty cache) to a genuine pass — while a revoked WRPRC
 * resolves to Revoked, not merely Unknown.
 */
class DeployedStatusListFixtureTest {

    private fun resource(path: String): String =
        checkNotNull(javaClass.getResourceAsStream(path)) { "missing test resource $path" }
            .bufferedReader().use { it.readText() }.trim()

    // The demo root the app bundles; the deployed status signer chains to it via the WRPRC provider.
    private val demoRoot: TrustStore get() = TrustStore.fromPem(resource("/trust/demo-root.cert.pem"))

    // A moment inside the deployed fixtures' validity window (signer/WRPRC iat 2026-07-10 ..).
    private val withinValidity = Date(1_790_000_000_000L)

    private val validStatusUri = "https://verifier-sandbox.nachweis.tech/trust/status/wrprc-valid.jwt"
    private val revokedStatusUri = "https://verifier-sandbox.nachweis.tech/trust/status/wrprc-revoked.jwt"

    @Test
    fun deployedValidStatusList_verifiesAndMarksIndexZeroValid() {
        val token = resource("/trust/status/wrprc-valid.status.jwt")
        val result = StatusListVerifier(demoRoot).verify(token, expectedSub = validStatusUri, now = withinValidity)
        assertTrue("expected Valid, got $result", result is StatusListVerification.Valid)
        assertEquals(0, (result as StatusListVerification.Valid).list.statusAt(0))
    }

    @Test
    fun deployedRevokedStatusList_marksIndexOneRevoked() {
        val token = resource("/trust/status/wrprc-revoked.status.jwt")
        val result = StatusListVerifier(demoRoot).verify(token, expectedSub = revokedStatusUri, now = withinValidity)
        assertTrue("expected Valid, got $result", result is StatusListVerification.Valid)
        val list = (result as StatusListVerification.Valid).list
        assertEquals("index 1 is revoked in the deployed revoked list", 1, list.statusAt(1))
        assertEquals("index 0 stays valid", 0, list.statusAt(0))
    }

    /**
     * Full client path with a fake fetcher returning the real committed status tokens: refresh
     * populates the swappable source, then the real deployed valid WRPRC validates end-to-end to
     * Valid (status now Good, no stub), and the real deployed revoked WRPRC validates to Revoked.
     */
    @Test
    fun refreshedCache_makesRealValidWrprcPass_andRevokedFail() {
        val validStatus = resource("/trust/status/wrprc-valid.status.jwt")
        val revokedStatus = resource("/trust/status/wrprc-revoked.status.jwt")
        val fetcher = StatusListFetcher { uri ->
            when (uri) {
                validStatusUri -> validStatus
                revokedStatusUri -> revokedStatus
                else -> null
            }
        }
        val target = SwappableWrprcStatusSource()
        val refresher = StatusListRefresher(
            fetcher = fetcher,
            statusVerifier = StatusListVerifier(demoRoot),
            statusListUris = listOf(validStatusUri, revokedStatusUri),
            target = target,
            clock = { withinValidity.time },
        )
        val outcome = refresher.refresh(withinValidity)
        assertEquals("both deployed lists verify", 2, outcome.verified)

        val validator = WrprcValidator(providerTrust = demoRoot, statusSource = target)

        val validWrprc = resource("/trust/wrprc-valid.jwt")
        val validResult = validator.validate(validWrprc, withinValidity)
        assertTrue("real valid WRPRC now passes with a populated cache, got $validResult", validResult is WrprcValidation.Valid)

        val revokedWrprc = resource("/trust/wrprc-revoked.jwt")
        val revokedResult = validator.validate(revokedWrprc, withinValidity)
        assertEquals(WrprcValidation.Invalid(WrprcRejection.Revoked), revokedResult)
    }
}
