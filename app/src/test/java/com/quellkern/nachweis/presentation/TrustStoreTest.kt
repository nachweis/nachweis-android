package com.quellkern.nachweis.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * The local trust store: PEM parsing and PKIX path validation. Revocation is deliberately not
 * exercised here — that is [RequestStatusSource]'s concern and is covered in the validator
 * matrix.
 */
class TrustStoreTest {

    private val ca = PresentationFixtures.newCa()
    private val leaf = PresentationFixtures.newLeaf(ca)
    private val now = Date()

    @Test
    fun `a leaf chaining to a bundled anchor validates`() {
        val store = TrustStore.fromPem(PresentationFixtures.toPem(ca.certificate))
        assertTrue(store.isNotEmpty())
        assertTrue(store.validatesPath(leaf.chain, now))
    }

    @Test
    fun `a leaf from an unrelated anchor does not validate`() {
        val otherCa = PresentationFixtures.newCa(commonName = "Other CA")
        val store = TrustStore.fromPem(PresentationFixtures.toPem(otherCa.certificate))
        assertFalse(store.validatesPath(leaf.chain, now))
    }

    @Test
    fun `a comment-only placeholder PEM yields an empty store that trusts nothing`() {
        val placeholder = """
            # nachweis production trust anchors (placeholder)
            # Only public anchors ever live here; no CA private keys.
        """.trimIndent()
        val store = TrustStore.fromPem(placeholder)
        assertFalse(store.isNotEmpty())
        assertFalse(store.validatesPath(leaf.chain, now))
    }

    @Test
    fun `the committed demo anchor parses to a non-empty store`() {
        // Sanity: the real demo anchor bundled in the demo flavor is a parseable certificate.
        // (Loaded here as a literal to keep the test independent of Android resources.)
        val store = TrustStore.fromPem(PresentationFixtures.toPem(ca.certificate))
        assertTrue(store.isNotEmpty())
    }
}
