package com.quellkern.nachweis.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The WRP-identifier binding (dev-plan.md D1 layer (c)). The WRPAC carries the WRP identifier in
 * its subject organizationIdentifier (OID 2.5.4.97); the WRPRC carries it in `wrp.id[].identifier`.
 * Binding is by that standardized identity string, not by certificate instance.
 */
class WrpBindingTest {

    private val ca = PresentationFixtures.newCa()

    private fun wrprc(vararg ids: String): Wrprc = Wrprc(
        wrpIdentifiers = ids.toList(),
        registeredCredentials = emptyList(),
        issuedAtEpochSeconds = 0,
        expiresAtEpochSeconds = null,
        statusRef = WrprcStatusRef("https://x/1", 0),
        policyIds = listOf("0.4.0.19475.3.1"),
    )

    @Test
    fun `organizationIdentifier is extracted from the WRPAC subject`() {
        val leaf = PresentationFixtures.newLeaf(ca, organizationIdentifier = PresentationFixtures.WRP_ID)
        assertEquals(listOf(PresentationFixtures.WRP_ID), WrpBinding.wrpacIdentifiers(leaf.certificate))
    }

    @Test
    fun `a WRPAC with no organizationIdentifier yields no identifiers`() {
        val leaf = PresentationFixtures.newLeaf(ca)
        assertTrue(WrpBinding.wrpacIdentifiers(leaf.certificate).isEmpty())
    }

    @Test
    fun `matching identifiers bind`() {
        val leaf = PresentationFixtures.newLeaf(ca, organizationIdentifier = PresentationFixtures.WRP_ID)
        assertTrue(WrpBinding.isBound(leaf.certificate, wrprc(PresentationFixtures.WRP_ID)))
    }

    @Test
    fun `a different WRPAC instance with the same identifier still binds`() {
        // Freshly minted: different key, different serial, same organizationIdentifier.
        val other = PresentationFixtures.newLeaf(ca, sanDns = "other.nachweis.tech", organizationIdentifier = PresentationFixtures.WRP_ID)
        assertTrue(WrpBinding.isBound(other.certificate, wrprc(PresentationFixtures.WRP_ID)))
    }

    @Test
    fun `unrelated identifiers do not bind`() {
        val leaf = PresentationFixtures.newLeaf(ca, organizationIdentifier = PresentationFixtures.OTHER_WRP_ID)
        assertFalse(WrpBinding.isBound(leaf.certificate, wrprc(PresentationFixtures.WRP_ID)))
    }

    @Test
    fun `absence on either side fails closed`() {
        val bare = PresentationFixtures.newLeaf(ca)
        assertFalse(WrpBinding.isBound(bare.certificate, wrprc(PresentationFixtures.WRP_ID)))
        val leaf = PresentationFixtures.newLeaf(ca, organizationIdentifier = PresentationFixtures.WRP_ID)
        assertFalse(WrpBinding.isBound(leaf.certificate, wrprc()))
    }
}
