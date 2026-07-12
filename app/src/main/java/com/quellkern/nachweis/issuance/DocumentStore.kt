package com.quellkern.nachweis.issuance

import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.DocumentFormat
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcClaim
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcData
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat

/**
 * Reads stored credentials from wallet-core's document manager and maps them to
 * [DocumentSummary] for the list UI and to [CredentialDetail] for the on-demand detail view. Kept
 * behind an interface so the list and detail screens can be previewed and unit-reasoned about
 * without a live wallet.
 */
interface DocumentStore {
    /** All stored documents, newest-first is not guaranteed by wallet-core; caller may sort. */
    fun list(): List<DocumentSummary>

    /**
     * The full claim set for one stored credential, or null if it is absent or not an issued,
     * claim-bearing document. Resolved only on explicit request; the returned values are
     * display-only and never flow back into [DocumentSummary] or any log.
     */
    fun details(id: String): CredentialDetail?
}

/** Backs [DocumentStore] with a ready [EudiWallet]. */
class WalletDocumentStore(private val wallet: EudiWallet) : DocumentStore {
    override fun list(): List<DocumentSummary> =
        wallet.getDocuments().map { document ->
            DocumentSummary(
                id = document.id,
                name = document.name,
                typeLabel = formatLabel(document.format),
            )
        }

    override fun details(id: String): CredentialDetail? {
        val document = wallet.getDocumentById(id) as? IssuedDocument ?: return null
        val data = document.data
        val nodes = when (data) {
            is SdJwtVcData -> data.claims.map { it.toNode() }
            else -> data.claims.map { ClaimNode(it.identifier, it.value?.toString()) }
        }
        return CredentialDetail(
            id = document.id,
            name = document.name,
            typeLabel = formatLabel(data.format),
            claims = flattenClaims(nodes),
        )
    }

    private fun formatLabel(format: DocumentFormat): String = when (format) {
        is SdJwtVcFormat -> format.vct
        is MsoMdocFormat -> format.docType
        else -> "Credential"
    }

    /** Map an SD-JWT VC claim (and its selectively-disclosable children) to the pure tree type. */
    private fun SdJwtVcClaim.toNode(): ClaimNode =
        ClaimNode(
            identifier = identifier,
            value = if (children.isEmpty()) value?.toString() else null,
            children = children.map { it.toNode() },
        )
}
