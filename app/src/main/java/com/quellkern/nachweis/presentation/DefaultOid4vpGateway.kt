package com.quellkern.nachweis.presentation

import android.net.Uri
import eu.europa.ec.eudi.iso18013.transfer.TransferEvent
import eu.europa.ec.eudi.iso18013.transfer.response.DisclosedDocument
import eu.europa.ec.eudi.iso18013.transfer.response.DisclosedDocuments
import eu.europa.ec.eudi.iso18013.transfer.response.DocItem
import eu.europa.ec.eudi.iso18013.transfer.response.RequestProcessor
import eu.europa.ec.eudi.iso18013.transfer.response.ResponseResult
import eu.europa.ec.eudi.wallet.EudiWallet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.multipaz.crypto.Algorithm
import java.net.HttpURLConnection
import java.net.URL

/**
 * The real [Oid4vpGateway], backed by wallet-core's presentation manager (the [EudiWallet]
 * itself implements it). Split into two honestly-different halves:
 *
 *  - [obtainSignedRequest] is **real and exercised**: it lifts the signed request object out
 *    of the deep link — either a `request` value carried inline, or the body fetched from a
 *    `request_uri`. That fetch is request *arrival*; the trust and status checks that follow
 *    (in [PresentationRequestValidator]) read only local caches, so consent still makes no
 *    verifier-keyed network calls (dev-plan.md D1).
 *
 *  - [sendResponse] drives wallet-core's `startRemotePresentation` → `generateResponse` →
 *    `sendResponse` cycle. Like B4's issuance dispatch, this path **compiles against
 *    wallet-core 0.28.1 but is exercised end-to-end only against a live verifier**, which is
 *    not yet deployed (see docs/presentation-slice.md). By the time it runs, the security
 *    decision is already made: the request was validated and the user consented. Presentation-
 *    time key-unlock (mirroring issuance's `DocumentRequiresUserAuth`) is wired when a live
 *    verifier exists; until then keys are unlocked with null and the path is not claimed to work.
 */
class DefaultOid4vpGateway(
    private val wallet: EudiWallet,
) : Oid4vpGateway {

    // The request URI of the in-flight presentation, retained between obtain and send. Safe as
    // a single field because the controller runs exactly one presentation at a time.
    @Volatile
    private var lastRequestUri: String? = null

    override suspend fun obtainSignedRequest(requestUri: String): SignedPresentationRequest {
        lastRequestUri = requestUri
        val uri = Uri.parse(requestUri)
        uri.getQueryParameter("request")?.let { inline ->
            return SignedPresentationRequest(inline)
        }
        val byReference = uri.getQueryParameter("request_uri")
            ?: throw IllegalArgumentException("no request or request_uri in presentation link")
        return SignedPresentationRequest(fetch(byReference))
    }

    /** Fetch a `request_uri` body (the signed request object). Arrival-time, off the main thread. */
    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/oauth-authz-req+jwt, application/jwt")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("request_uri returned ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }.trim()
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun sendResponse(
        request: ValidatedPresentationRequest,
        disclosedClaims: List<RequestedClaim>,
    ) {
        val requestUri = lastRequestUri ?: throw IllegalStateException("no in-flight presentation to answer")
        val processed = resolveProcessedRequest(requestUri)
        val success = processed.getOrThrow()

        // Disclose exactly the items wallet-core matched from the (already subset-checked)
        // DCQL query; the validator has confirmed the query is the supported SD-JWT PID set.
        val disclosed = success.requestedDocuments.map { requested ->
            val items: List<DocItem> = requested.requestedItems.keys.toList()
            DisclosedDocument(requested, items, null)
        }
        val response = when (val result = success.generateResponse(DisclosedDocuments(disclosed), Algorithm.ESP256)) {
            is ResponseResult.Success -> result.getOrThrow()
            is ResponseResult.Failure -> throw result.throwable
        }
        wallet.sendResponse(response)
    }

    /** Start the remote presentation and await the resolved, processed request. */
    private suspend fun resolveProcessedRequest(requestUri: String): RequestProcessor.ProcessedRequest {
        val deferred = CompletableDeferred<RequestProcessor.ProcessedRequest>()
        val listener = TransferEvent.Listener { event ->
            when (event) {
                is TransferEvent.RequestReceived -> deferred.complete(event.processedRequest)
                is TransferEvent.Error -> deferred.completeExceptionally(event.error)
                else -> Unit
            }
        }
        wallet.addTransferEventListener(listener)
        return try {
            wallet.startRemotePresentation(Uri.parse(requestUri), null)
            deferred.await()
        } finally {
            wallet.removeTransferEventListener(listener)
        }
    }

    override fun reject() {
        runCatching { wallet.rejectRemotePresentation() }
    }
}
