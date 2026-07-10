package com.quellkern.nachweis.issuance

/**
 * The UI's view of a stored credential. Deliberately minimal and free of credential
 * material: identifiers and display labels only, never disclosed claim values.
 */
data class DocumentSummary(
    val id: String,
    val name: String,
    /** A short format label, e.g. the SD-JWT vct or the mdoc doctype. */
    val typeLabel: String,
)
