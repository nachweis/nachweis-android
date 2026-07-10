package com.quellkern.nachweis.issuance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IssuerAllowlistTest {

    private val allowlist = IssuerAllowlist(listOf("https://api-sandbox.nachweis.tech"))

    @Test
    fun `matches by origin ignoring path and query`() {
        assertTrue(allowlist.isAllowed("https://api-sandbox.nachweis.tech/issuers/nachweis"))
        assertTrue(allowlist.isAllowed("https://api-sandbox.nachweis.tech/.well-known/x?y=1"))
    }

    @Test
    fun `host mismatch is rejected`() {
        assertFalse(allowlist.isAllowed("https://evil.example.com/issuers/nachweis"))
        assertFalse(allowlist.isAllowed("https://api.nachweis.tech/issuers/nachweis"))
    }

    @Test
    fun `scheme mismatch is rejected`() {
        assertFalse(allowlist.isAllowed("http://api-sandbox.nachweis.tech/issuers/nachweis"))
    }

    @Test
    fun `port is part of the origin`() {
        val local = IssuerAllowlist(listOf("http://10.0.2.2:13002"))
        assertTrue(local.isAllowed("http://10.0.2.2:13002/issuers/nachweis"))
        assertFalse(local.isAllowed("http://10.0.2.2:3002/issuers/nachweis"))
        assertFalse(local.isAllowed("http://10.0.2.2/issuers/nachweis"))
    }

    @Test
    fun `null and blank are rejected`() {
        assertFalse(allowlist.isAllowed(null))
        assertFalse(allowlist.isAllowed(""))
        assertFalse(allowlist.isAllowed("   "))
    }

    @Test
    fun `default https port normalizes equal to no port`() {
        assertTrue(allowlist.isAllowed("https://api-sandbox.nachweis.tech:443/x"))
    }
}
