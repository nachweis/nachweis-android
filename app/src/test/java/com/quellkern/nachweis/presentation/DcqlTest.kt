package com.quellkern.nachweis.presentation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The constrained DCQL subset parser (dev-plan.md D1): exactly one SD-JWT credential with a
 * simple claim set is [DcqlResult.Supported]; everything else is [DcqlResult.Unsupported],
 * never narrowed or unioned.
 */
class DcqlTest {

    private fun parse(json: String): DcqlResult = Dcql.parse(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun `a single SD-JWT credential with simple claims is supported`() {
        val result = parse(
            """{"credentials":[{"id":"pid","format":"dc+sd-jwt",
               "meta":{"vct_values":["urn:eudi:pid:de:1"]},
               "claims":[{"path":["given_name"]},{"path":["address","locality"]}]}]}""",
        )
        assertTrue(result is DcqlResult.Supported)
        result as DcqlResult.Supported
        assertEquals("urn:eudi:pid:de:1", result.vct)
        assertEquals(listOf("given_name", "address.locality"), result.claims.map { it.path })
    }

    @Test
    fun `the vc+sd-jwt format spelling is also supported`() {
        val result = parse(
            """{"credentials":[{"id":"pid","format":"vc+sd-jwt",
               "meta":{"vct_values":["urn:eudi:pid:de:1"]},"claims":[{"path":["given_name"]}]}]}""",
        )
        assertTrue(result is DcqlResult.Supported)
    }

    @Test
    fun `an mdoc format is unsupported`() {
        assertUnsupported(
            """{"credentials":[{"id":"pid","format":"mso_mdoc",
               "meta":{"doctype_value":"eu.europa.ec.eudi.pid.1"},"claims":[{"path":["given_name"]}]}]}""",
        )
    }

    @Test
    fun `multiple true is unsupported`() {
        assertUnsupported(
            """{"credentials":[{"id":"pid","format":"dc+sd-jwt","multiple":true,
               "meta":{"vct_values":["urn:eudi:pid:de:1"]},"claims":[{"path":["given_name"]}]}]}""",
        )
    }

    @Test
    fun `a claim path with an integer index is unsupported`() {
        assertUnsupported(
            """{"credentials":[{"id":"pid","format":"dc+sd-jwt",
               "meta":{"vct_values":["urn:eudi:pid:de:1"]},"claims":[{"path":["degrees",0,"name"]}]}]}""",
        )
    }

    @Test
    fun `an empty claims array is unsupported`() {
        assertUnsupported(
            """{"credentials":[{"id":"pid","format":"dc+sd-jwt",
               "meta":{"vct_values":["urn:eudi:pid:de:1"]},"claims":[]}]}""",
        )
    }

    @Test
    fun `two vct values are unsupported (ambiguous target)`() {
        assertUnsupported(
            """{"credentials":[{"id":"pid","format":"dc+sd-jwt",
               "meta":{"vct_values":["urn:eudi:pid:de:1","urn:eudi:pid:1"]},"claims":[{"path":["given_name"]}]}]}""",
        )
    }

    private fun assertUnsupported(json: String) {
        assertTrue(parse(json) is DcqlResult.Unsupported)
    }
}
