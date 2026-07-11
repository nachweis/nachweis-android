package com.quellkern.nachweis.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WRPRC extraction from `verifier_info` and payload parsing. The payload parser accepts both the
 * v1.1.1 clone shape (`sub`, `claim`, string `meta`) and the aspirational/V1.2.1-aligned shape
 * (`wrp`, `claims`, `meta.vct_values`) — dev-plan.md treats the local clone as fixtures, not
 * authority, so the parser reads a V1.2.1 fixture sitting between the two.
 */
class WrprcExtractorTest {

    @Test
    fun `a JWT WRPRC entry is selected`() {
        val result = WrprcExtractor.extract("""[{"format":"rc-wrp+jwt","data":"a.b.c"}]""")
        assertTrue(result is VerifierInfoResult.JwtWrprc)
        assertEquals("a.b.c", (result as VerifierInfoResult.JwtWrprc).compactJws)
    }

    @Test
    fun `the deployed registration_cert format with a compact JWS is selected`() {
        // The shape the deployed EUDI verifiers (augenmass) actually send: the OpenID4VP
        // verifier_info entry format is "registration_cert", carrying the compact JWS WRPRC.
        val result = WrprcExtractor.extract(
            """[{"format":"registration_cert","data":"aa-bb_cc.dd.ee"}]""",
        )
        assertTrue(result is VerifierInfoResult.JwtWrprc)
        assertEquals("aa-bb_cc.dd.ee", (result as VerifierInfoResult.JwtWrprc).compactJws)
    }

    @Test
    fun `a registration_cert entry whose data is not a compact JWS is rejected`() {
        // A CBOR/CWT registration certificate carried under the same outer format must not pass.
        val result = WrprcExtractor.extract(
            """[{"format":"registration_cert","data":"a1b2c3cborbytes"}]""",
        )
        assertEquals(VerifierInfoResult.UnsupportedEncoding, result)
    }

    @Test
    fun `a CWT entry ahead of a JWT still rejects as unsupported`() {
        // A verifier must not smuggle an unsupported CWT past the check by trailing a JWT.
        val result = WrprcExtractor.extract(
            """[{"format":"rc-wrp+cwt","data":"cbor"},{"format":"rc-wrp+jwt","data":"a.b.c"}]""",
        )
        assertEquals(VerifierInfoResult.UnsupportedEncoding, result)
    }

    @Test
    fun `no registration certificate is absent`() {
        assertEquals(VerifierInfoResult.Absent, WrprcExtractor.extract("""[{"format":"something_else","data":"x"}]"""))
        assertEquals(VerifierInfoResult.Absent, WrprcExtractor.extract(null))
        assertEquals(VerifierInfoResult.Absent, WrprcExtractor.extract("not json"))
    }

    @Test
    fun `parses the aspirational shape (wrp, claims, vct_values)`() {
        val payload = """
            {"wrp":{"legal_name":["X"],"id":[{"type":"LEI","identifier":"LEIXG-1"}]},
             "policy_id":["0.4.0.19475.3.1"],"iat":1751100000,
             "status":{"status_list":{"idx":3,"uri":"https://r/1"}},
             "credentials":[{"format":"dc+sd-jwt","meta":{"vct_values":["urn:eudi:pid:de:1"]},
               "claims":[{"path":["given_name"]},{"path":["address","locality"]}]}]}
        """.trimIndent()
        val wrprc = WrprcExtractor.parsePayload(payload)!!
        assertEquals(listOf("LEIXG-1"), wrprc.wrpIdentifiers)
        assertEquals(WrprcStatusRef("https://r/1", 3), wrprc.statusRef)
        assertEquals(setOf("given_name", "address.locality"), wrprc.registeredCredentials.single().claimPaths)
        assertEquals("urn:eudi:pid:de:1", wrprc.registeredCredentials.single().vct)
    }

    @Test
    fun `parses the v1_1_1 shape (sub, claim, string meta)`() {
        val payload = """
            {"sub":{"legal_name":["X"],"id":[{"type":"LEI","identifier":"LEIXG-2"}]},
             "policy_id":"0.4.0.19475.3.1","iat":1751100000,
             "status":{"status_list":{"idx":0,"uri":"https://r/1"}},
             "credentials":[{"format":"dc+sd-jwt","meta":"urn:eudi:pid:de:1",
               "claim":[{"path":"given_name"},{"path":"family_name"}]}]}
        """.trimIndent()
        val wrprc = WrprcExtractor.parsePayload(payload)!!
        assertEquals(listOf("LEIXG-2"), wrprc.wrpIdentifiers)
        assertEquals(setOf("given_name", "family_name"), wrprc.registeredCredentials.single().claimPaths)
        assertEquals("urn:eudi:pid:de:1", wrprc.registeredCredentials.single().vct)
    }

    @Test
    fun `missing required fields yield null`() {
        // No status pointer.
        assertNull(
            WrprcExtractor.parsePayload(
                """{"wrp":{"id":[{"identifier":"LEIXG-1"}]},"policy_id":["p"],"iat":1,
                   "credentials":[{"format":"dc+sd-jwt","meta":{"vct_values":["v"]},"claims":[{"path":["a"]}]}]}""",
            ),
        )
        // No identifiers.
        assertNull(
            WrprcExtractor.parsePayload(
                """{"wrp":{"id":[]},"policy_id":["p"],"iat":1,"status":{"status_list":{"idx":0,"uri":"u"}},
                   "credentials":[{"format":"dc+sd-jwt","meta":{"vct_values":["v"]},"claims":[{"path":["a"]}]}]}""",
            ),
        )
    }
}
