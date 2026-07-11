package com.quellkern.nachweis.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * The WRPRC supported-profile validator (dev-plan.md D1 layer (b)): JWT `rc-wrp+jwt`, JAdES B-B
 * markers, WRPRC-provider chain, temporal validity, and signed status list. Every check is
 * exercised against locally minted, throwaway fixtures; a generic signed JWT must not pass.
 */
class WrprcValidatorTest {

    private val providerCa = PresentationFixtures.newCa(commonName = "nachweis Test WRPRC Provider CA")
    private val provider = PresentationFixtures.newLeaf(providerCa, sanDns = "wrprc.nachweis.tech")
    private val now = Date()

    private fun validator(status: WrprcStatusSource = PresentationFixtures.wrprcStatusGood()) =
        WrprcValidator(TrustStore(listOf(providerCa.certificate)), status)

    private fun assertRejected(reason: WrprcRejection, jwt: String, status: WrprcStatusSource = PresentationFixtures.wrprcStatusGood()) {
        val result = validator(status).validate(jwt, now)
        assertTrue("expected Invalid but was $result", result is WrprcValidation.Invalid)
        assertEquals(reason, (result as WrprcValidation.Invalid).reason)
    }

    @Test
    fun `a conforming WRPRC validates`() {
        val jwt = PresentationFixtures.wrprcJwt(provider)
        val result = validator().validate(jwt, now)
        assertTrue("expected Valid but was $result", result is WrprcValidation.Valid)
        val wrprc = (result as WrprcValidation.Valid).wrprc
        assertEquals(listOf(PresentationFixtures.WRP_ID), wrprc.wrpIdentifiers)
        assertEquals(setOf("given_name", "family_name"), wrprc.registeredCredentials.single().claimPaths)
    }

    @Test
    fun `a non-rc-wrp typ is rejected as an unsupported profile`() =
        assertRejected(WrprcRejection.NotSupportedProfile, PresentationFixtures.wrprcJwt(provider, typ = "JWT"))

    @Test
    fun `a WRPRC missing the JAdES signing time is rejected`() =
        assertRejected(WrprcRejection.NotJAdES, PresentationFixtures.wrprcJwt(provider, includeSigT = false))

    @Test
    fun `a WRPRC without an included signing certificate is rejected`() =
        assertRejected(WrprcRejection.NotJAdES, PresentationFixtures.wrprcJwt(provider, includeX5c = false))

    @Test
    fun `a WRPRC from an untrusted provider is rejected`() {
        val strangerCa = PresentationFixtures.newCa(commonName = "Stranger CA")
        val stranger = PresentationFixtures.newLeaf(strangerCa, sanDns = "stranger")
        assertRejected(WrprcRejection.ProviderUntrusted, PresentationFixtures.wrprcJwt(stranger))
    }

    @Test
    fun `a tampered WRPRC signature is rejected`() {
        val jwt = PresentationFixtures.corruptSignature(PresentationFixtures.wrprcJwt(provider))
        assertRejected(WrprcRejection.BadSignature, jwt)
    }

    @Test
    fun `an expired WRPRC is rejected`() {
        val past = System.currentTimeMillis() / 1000 - 100
        assertRejected(
            WrprcRejection.Expired,
            PresentationFixtures.wrprcJwt(provider, iatEpochSeconds = past - 3600, expEpochSeconds = past),
        )
    }

    @Test
    fun `a future-dated WRPRC is rejected`() {
        val future = System.currentTimeMillis() / 1000 + 86400
        assertRejected(WrprcRejection.Expired, PresentationFixtures.wrprcJwt(provider, iatEpochSeconds = future))
    }

    @Test
    fun `a revoked WRPRC is rejected`() =
        assertRejected(WrprcRejection.Revoked, PresentationFixtures.wrprcJwt(provider), PresentationFixtures.wrprcStatusRevoked())

    @Test
    fun `an unknown WRPRC status fails closed`() =
        assertRejected(WrprcRejection.StatusUnavailable, PresentationFixtures.wrprcJwt(provider), PresentationFixtures.wrprcStatusUnknown())

    @Test
    fun `a WRPRC missing required fields is rejected`() =
        assertRejected(WrprcRejection.MissingFields, PresentationFixtures.wrprcJwt(provider, includePolicyId = false))

    @Test
    fun `a WRPRC missing its status pointer is rejected`() =
        assertRejected(WrprcRejection.MissingFields, PresentationFixtures.wrprcJwt(provider, includeStatus = false))
}
