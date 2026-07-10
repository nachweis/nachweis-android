package com.quellkern.nachweis.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkIntakeTest {

    @Test
    fun `offer schemes route to credential offer`() {
        val a = DeepLinkIntake.classify("openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fx")
        assertTrue(a is DeepLinkAction.CredentialOffer)
        assertTrue(DeepLinkIntake.classify("haip-vci://issue?x=1") is DeepLinkAction.CredentialOffer)
    }

    @Test
    fun `presentation schemes route to presentation`() {
        for (scheme in listOf("openid4vp", "eudi-openid4vp", "mdoc-openid4vp", "haip-vp")) {
            assertTrue(
                "$scheme should be a presentation",
                DeepLinkIntake.classify("$scheme://authorize?request=1") is DeepLinkAction.Presentation,
            )
        }
    }

    @Test
    fun `own redirect scheme with authorization host is an auth callback`() {
        val a = DeepLinkIntake.classify("com.quellkern.nachweis://authorization?code=abc&state=xyz")
        assertTrue(a is DeepLinkAction.AuthorizationCallback)
        assertEquals("com.quellkern.nachweis://authorization?code=abc&state=xyz", (a as DeepLinkAction.AuthorizationCallback).uri)
    }

    @Test
    fun `redirect scheme with a different host is not an auth callback`() {
        assertEquals(DeepLinkAction.Unknown, DeepLinkIntake.classify("com.quellkern.nachweis://elsewhere?code=abc"))
    }

    @Test
    fun `the EU reference scheme is never ours`() {
        assertEquals(DeepLinkAction.Unknown, DeepLinkIntake.classify("eu.europa.ec.euidi://authorization?code=abc"))
    }

    @Test
    fun `case in scheme and host is ignored`() {
        assertTrue(DeepLinkIntake.classify("OpenID4VP://x") is DeepLinkAction.Presentation)
        assertTrue(
            DeepLinkIntake.classify("com.quellkern.nachweis://AUTHORIZATION?code=1") is DeepLinkAction.AuthorizationCallback,
        )
    }

    @Test
    fun `blank or unschemed input is unknown`() {
        assertEquals(DeepLinkAction.Unknown, DeepLinkIntake.classify(null as String?))
        assertEquals(DeepLinkAction.Unknown, DeepLinkIntake.classify(""))
        assertEquals(DeepLinkAction.Unknown, DeepLinkIntake.classify("not-a-uri"))
    }
}
