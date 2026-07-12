package com.quellkern.nachweis.issuance

/**
 * A stored credential resolved for the detail view: its identifiers, a display label, and its
 * claims as flat, dotted-path rows. Assembled only after an explicit tap and held only in UI
 * state — claim values never enter [DocumentSummary] and are never logged (the list view stays
 * free of credential material; only this on-demand view carries values).
 */
data class CredentialDetail(
    val id: String,
    val name: String,
    val typeLabel: String,
    val claims: List<CredentialClaim>,
)

/** One resolved claim: a dotted path (`address.locality`) and its display value. */
data class CredentialClaim(
    val path: String,
    val value: String,
)

/**
 * A node in a credential's claim tree, decoupled from wallet-core's `SdJwtVcClaim` so the
 * flattening is a pure function with no Android or wallet-core dependency and can be unit-tested
 * on the JVM. A branch (children non-empty) is a container and carries no value of its own; a leaf
 * carries a display [value].
 */
data class ClaimNode(
    val identifier: String,
    val value: String?,
    val children: List<ClaimNode> = emptyList(),
)

/**
 * Flatten a claim tree into leaf rows keyed by dotted path. A branch node is recursed into with
 * its identifier appended to the path prefix and is never emitted itself; a leaf becomes one
 * [CredentialClaim] (a null leaf value renders as the empty string). Rows preserve the tree's
 * depth-first, input order so the detail view is deterministic.
 */
fun flattenClaims(nodes: List<ClaimNode>, prefix: String = ""): List<CredentialClaim> =
    nodes.flatMap { node ->
        val path = if (prefix.isEmpty()) node.identifier else "$prefix.${node.identifier}"
        if (node.children.isNotEmpty()) {
            flattenClaims(node.children, path)
        } else {
            listOf(CredentialClaim(path, node.value ?: ""))
        }
    }
