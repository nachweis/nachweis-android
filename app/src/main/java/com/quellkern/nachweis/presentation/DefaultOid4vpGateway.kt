package com.quellkern.nachweis.presentation

import eu.europa.ec.eudi.iso18013.transfer.TransferEvent
import eu.europa.ec.eudi.iso18013.transfer.response.DisclosedDocument
import eu.europa.ec.eudi.iso18013.transfer.response.DisclosedDocuments
import eu.europa.ec.eudi.iso18013.transfer.response.DocItem
import eu.europa.ec.eudi.iso18013.transfer.response.RequestProcessor
import eu.europa.ec.eudi.iso18013.transfer.response.Response
import eu.europa.ec.eudi.iso18013.transfer.response.ResponseResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.multipaz.crypto.Algorithm
import org.multipaz.securearea.AndroidKeystoreKeyUnlockData
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The real [Oid4vpGateway], backed by wallet-core's presentation manager through the injected
 * [PresentationTransport] (the [WalletPresentationTransport] adapter, so the wallet-core and
 * Android surface stays out of this class and its JVM tests). Split into two honestly-different
 * halves:
 *
 *  - [obtainSignedRequest] is **real and exercised**: it lifts the signed request object out
 *    of the deep link — either a `request` value carried inline, or the body fetched from a
 *    `request_uri`. That fetch is request *arrival*; the trust and status checks that follow
 *    (in [PresentationRequestValidator]) read only local caches, so consent still makes no
 *    verifier-keyed network calls (dev-plan.md D1).
 *
 *  - [sendResponse] drives wallet-core's `startRemotePresentation` → `generateResponse` →
 *    `sendResponse` cycle. By the time it runs, the security decision is already made: the
 *    request was validated and the user consented. Presentation-time key-unlock (mirroring
 *    issuance's `DocumentRequiresUserAuth`) runs here through the injected [authenticator]: the
 *    PID signing key is per-use device-auth protected, so the key-binding JWT is signed only
 *    after the user authenticates. wallet-core must be configured for OpenID4VP
 *    ([com.quellkern.nachweis.wallet.WalletConfigFactory.build]) or `startRemotePresentation`
 *    rejects every request with `error("Not supported scheme")`.
 */
class DefaultOid4vpGateway(
    private val transport: PresentationTransport,
    private val authenticator: PresentationAuthenticator,
) : Oid4vpGateway {

    // The request URI of the in-flight presentation, retained between obtain and send. Safe as
    // a single field because the controller runs exactly one presentation at a time.
    @Volatile
    private var lastRequestUri: String? = null

    override suspend fun obtainSignedRequest(requestUri: String): SignedPresentationRequest {
        lastRequestUri = requestUri
        queryParameter(requestUri, "request")?.let { inline ->
            return SignedPresentationRequest(inline)
        }
        val byReference = queryParameter(requestUri, "request_uri")
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
        // Each disclosed document carries its own key-unlock data: the PID signing key is minted
        // under per-use device authentication (WalletSecurityPolicy), so the key-binding JWT that
        // proves holder binding cannot be signed until the user authenticates. keyUnlockDataFor
        // returns null for a key that needs no auth, in which case nothing is unlocked.
        val disclosed = success.requestedDocuments.map { requested ->
            val items: List<DocItem> = requested.requestedItems.keys.toList()
            val unlock = transport.keyUnlockDataFor(requested.documentId)
            DisclosedDocument(requested, items, unlock)
        }
        // Authenticate once per signing key, on the exact unlock-data instances embedded above,
        // before generateResponse consumes them. Mirrors issuance's DocumentRequiresUserAuth step.
        authenticator.authenticate(disclosed.mapNotNull { it.keyUnlockData as? AndroidKeystoreKeyUnlockData })
        val response = when (val result = success.generateResponse(DisclosedDocuments(disclosed), Algorithm.ESP256)) {
            is ResponseResult.Success -> result.getOrThrow()
            is ResponseResult.Failure -> throw result.throwable
        }
        dispatchAndAwait(response)
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
        transport.addTransferEventListener(listener)
        return try {
            transport.startRemotePresentation(requestUri)
            deferred.await()
        } finally {
            transport.removeTransferEventListener(listener)
        }
    }

    /**
     * Dispatch [response] and suspend until wallet-core reports the verifier's answer.
     * wallet-core's `sendResponse` is fire-and-forget — it POSTs the (for `direct_post.jwt`,
     * encrypted) `vp_token` on a background job and reports the outcome only through a
     * [TransferEvent]. Awaiting it here means a verifier *rejection* surfaces as a thrown error
     * (→ `PresentationError.Unexpected` → "Request rejected") instead of a false "Shared": the
     * success state is shown only after the verifier accepts (`ResponseSent`/`Redirect`).
     */
    private suspend fun dispatchAndAwait(response: Response) {
        val deferred = CompletableDeferred<Unit>()
        val listener = TransferEvent.Listener { event ->
            when (event) {
                is TransferEvent.ResponseSent -> deferred.complete(Unit)
                is TransferEvent.Redirect -> deferred.complete(Unit)
                is TransferEvent.Error -> deferred.completeExceptionally(event.error)
                else -> Unit
            }
        }
        transport.addTransferEventListener(listener)
        try {
            transport.sendResponse(response)
            deferred.await()
        } finally {
            transport.removeTransferEventListener(listener)
        }
    }

    override fun reject() {
        runCatching { transport.rejectRemotePresentation() }
    }

    /**
     * Extract the first `name` query parameter from [raw], reproducing
     * [android.net.Uri.getQueryParameter] for presentation deep links so the SDK's `Uri` type stays
     * out of this class (and its JVM tests): the query is the span after the first `?` up to the
     * first following `#`; parameters split on `&`, name from value on the first `=`; the first
     * matching name wins; the value is percent-decoded as UTF-8. A literal `+` is left as `+` (not
     * turned into a space) — matching `Uri.decode`; the request/request_uri values are percent- and
     * base64url-encoded, so `+` never occurs literally in practice. Returns null when absent, `""`
     * for a present-but-valueless parameter.
     */
    private fun queryParameter(raw: String, name: String): String? {
        val q = raw.indexOf('?')
        if (q < 0) return null
        val hash = raw.indexOf('#', q + 1)
        val query = if (hash < 0) raw.substring(q + 1) else raw.substring(q + 1, hash)
        val length = query.length
        var start = 0
        while (true) {
            val amp = query.indexOf('&', start)
            val end = if (amp != -1) amp else length
            var separator = query.indexOf('=', start)
            if (separator == -1 || separator > end) separator = end
            if (separator - start == name.length && query.regionMatches(start, name, 0, name.length)) {
                return if (separator == end) "" else percentDecodeUtf8(query.substring(separator + 1, end))
            }
            if (amp != -1) start = amp + 1 else break
        }
        return null
    }

    /** Percent-decode [s] as UTF-8, accumulating consecutive `%XX` bytes; non-escape chars pass through. */
    private fun percentDecodeUtf8(s: String): String {
        if (s.indexOf('%') < 0) return s
        val out = StringBuilder(s.length)
        val bytes = ByteArrayOutputStream()
        fun flush() {
            if (bytes.size() > 0) {
                out.append(String(bytes.toByteArray(), Charsets.UTF_8))
                bytes.reset()
            }
        }
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val hi = Character.digit(s[i + 1], 16)
                val lo = Character.digit(s[i + 2], 16)
                if (hi >= 0 && lo >= 0) {
                    bytes.write((hi shl 4) + lo)
                    i += 3
                    continue
                }
            }
            flush()
            out.append(c)
            i++
        }
        flush()
        return out.toString()
    }
}
