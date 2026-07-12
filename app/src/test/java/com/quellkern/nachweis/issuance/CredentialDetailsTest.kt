package com.quellkern.nachweis.issuance

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [flattenClaims]: the pure tree-to-rows step the detail view renders. Dotted paths, branch
 * elision, null-leaf handling, and stable order are all fixed here on the JVM, independent of
 * wallet-core's claim type.
 */
class CredentialDetailsTest {

    @Test
    fun `top-level leaves keep their bare identifier as the path`() {
        val rows = flattenClaims(
            listOf(
                ClaimNode("given_name", "Erika"),
                ClaimNode("family_name", "Mustermann"),
            ),
        )
        assertEquals(
            listOf(
                CredentialClaim("given_name", "Erika"),
                CredentialClaim("family_name", "Mustermann"),
            ),
            rows,
        )
    }

    @Test
    fun `nested branches become dotted paths and the branch itself is not emitted`() {
        val rows = flattenClaims(
            listOf(
                ClaimNode(
                    identifier = "address",
                    value = null,
                    children = listOf(
                        ClaimNode("locality", "Berlin"),
                        ClaimNode("postal_code", "10117"),
                    ),
                ),
            ),
        )
        assertEquals(
            listOf(
                CredentialClaim("address.locality", "Berlin"),
                CredentialClaim("address.postal_code", "10117"),
            ),
            rows,
        )
    }

    @Test
    fun `deeply nested paths join every level with a dot`() {
        val rows = flattenClaims(
            listOf(
                ClaimNode(
                    "place_of_birth",
                    null,
                    listOf(
                        ClaimNode(
                            "country",
                            null,
                            listOf(ClaimNode("code", "DE")),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(listOf(CredentialClaim("place_of_birth.country.code", "DE")), rows)
    }

    @Test
    fun `a null leaf value renders as the empty string`() {
        assertEquals(
            listOf(CredentialClaim("nickname", "")),
            flattenClaims(listOf(ClaimNode("nickname", null))),
        )
    }

    @Test
    fun `an empty tree flattens to no rows`() {
        assertEquals(emptyList<CredentialClaim>(), flattenClaims(emptyList()))
    }

    @Test
    fun `order follows the tree depth-first in input order`() {
        val rows = flattenClaims(
            listOf(
                ClaimNode("a", "1"),
                ClaimNode("b", null, listOf(ClaimNode("x", "2"), ClaimNode("y", "3"))),
                ClaimNode("c", "4"),
            ),
        )
        assertEquals(listOf("a", "b.x", "b.y", "c"), rows.map { it.path })
    }
}
