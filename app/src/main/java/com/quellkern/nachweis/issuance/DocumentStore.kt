package com.quellkern.nachweis.issuance

import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat

/**
 * Reads stored credentials from wallet-core's document manager and maps them to
 * [DocumentSummary] for the list UI. Kept behind an interface so the list screen can be
 * previewed and unit-reasoned about without a live wallet.
 */
fun interface DocumentStore {
    /** All stored documents, newest-first is not guaranteed by wallet-core; caller may sort. */
    fun list(): List<DocumentSummary>
}

/** Backs [DocumentStore] with a ready [EudiWallet]. */
class WalletDocumentStore(private val wallet: EudiWallet) : DocumentStore {
    override fun list(): List<DocumentSummary> =
        wallet.getDocuments().map { document ->
            DocumentSummary(
                id = document.id,
                name = document.name,
                typeLabel = when (val format = document.format) {
                    is SdJwtVcFormat -> format.vct
                    is MsoMdocFormat -> format.docType
                    else -> "Credential"
                },
            )
        }
}
