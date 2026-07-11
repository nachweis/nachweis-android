package com.quellkern.nachweis.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.Date
import org.junit.Test

/**
 * End-to-end validation of the **real deployed** demo WRPRC against the **real bundled** demo
 * trust anchor, both fetched from the public sandbox (`verifier-sandbox.nachweis.tech/trust`) and
 * committed as public test fixtures. This is the ground-truth interoperability check: the offline
 * generator (Workstream A) and the in-app validator (D1) agree on the V1.2.1 artifact shape.
 *
 * The status list is not yet fetched at runtime (fails closed in the app), so the status source is
 * stubbed with the fixture's own status entry as good — the one input the deployed cache would
 * later supply. Everything else (signature, x5c chain to the bundled demo root, profile markers,
 * temporal validity, parsed registration) is exercised against the untouched public artifact.
 */
class DeployedWrprcFixtureTest {

    private fun resource(path: String): String =
        checkNotNull(javaClass.getResourceAsStream(path)) { "missing test resource $path" }
            .bufferedReader().use { it.readText() }

    private fun demoRootTrust(): TrustStore =
        TrustStore.fromPem(resource("/trust/demo-root.cert.pem"))

    // A moment inside the fixture's validity window (iat 1783724899 .. exp 1799276899).
    private val withinValidity = Date(1_790_000_000_000L)

    @Test
    fun deployedValidWrprc_passesAgainstBundledDemoRoot() {
        val wrprc = resource("/trust/wrprc-valid.jwt").trim()

        // First parse the payload to learn the fixture's own status pointer, then seed the stub
        // status source with exactly that entry as good — mirroring a populated cache.
        val parsed = checkNotNull(WrprcExtractor.parsePayload(payloadOf(wrprc))) { "payload did not parse" }
        val statusSource = CachedWrprcStatusSource(good = setOf(CachedWrprcStatusSource.keyOf(parsed.statusRef)))

        val validator = WrprcValidator(providerTrust = demoRootTrust(), statusSource = statusSource)
        val result = validator.validate(wrprc, withinValidity)

        assertTrue("expected Valid, got $result", result is WrprcValidation.Valid)
        val v = (result as WrprcValidation.Valid).wrprc
        assertTrue(
            "WRP identifier NTRDE-NACHWEIS-DEMO-0001 expected, got ${v.wrpIdentifiers}",
            v.wrpIdentifiers.any { it.equals("NTRDE-NACHWEIS-DEMO-0001", ignoreCase = true) },
        )
        assertEquals(listOf("urn:eudi:pid:de:1"), v.registeredCredentials.map { it.vct })
        assertTrue("policy_id 0.4.0.19475.3.1 expected", "0.4.0.19475.3.1" in v.policyIds)
    }

    @Test
    fun deployedValidWrprc_failsClosedWhenStatusUnknown() {
        val wrprc = resource("/trust/wrprc-valid.jwt").trim()
        val validator = WrprcValidator(providerTrust = demoRootTrust(), statusSource = CachedWrprcStatusSource())
        val result = validator.validate(wrprc, withinValidity)
        assertEquals(WrprcValidation.Invalid(WrprcRejection.StatusUnavailable), result)
    }

    @Test
    fun deployedValidWrprc_failsAgainstEmptyAnchorSet() {
        val wrprc = resource("/trust/wrprc-valid.jwt").trim()
        val parsed = checkNotNull(WrprcExtractor.parsePayload(payloadOf(wrprc)))
        val statusSource = CachedWrprcStatusSource(good = setOf(CachedWrprcStatusSource.keyOf(parsed.statusRef)))
        val validator = WrprcValidator(providerTrust = TrustStore(emptyList()), statusSource = statusSource)
        val result = validator.validate(wrprc, withinValidity)
        assertEquals(WrprcValidation.Invalid(WrprcRejection.ProviderUntrusted), result)
    }

    /** Decode the JWT payload segment (URL-safe base64, no padding) into its JSON string. */
    private fun payloadOf(compactJws: String): String {
        val payloadSegment = compactJws.split('.')[1]
        return String(java.util.Base64.getUrlDecoder().decode(payloadSegment))
    }
}
