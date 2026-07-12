package com.quellkern.nachweis.presentation

import com.quellkern.nachweis.issuance.UserAuthException
import com.sun.net.httpserver.HttpServer
import eu.europa.ec.eudi.iso18013.transfer.TransferEvent
import eu.europa.ec.eudi.iso18013.transfer.response.DisclosedDocuments
import eu.europa.ec.eudi.iso18013.transfer.response.DocItem
import eu.europa.ec.eudi.iso18013.transfer.response.Request
import eu.europa.ec.eudi.iso18013.transfer.response.RequestProcessor
import eu.europa.ec.eudi.iso18013.transfer.response.RequestedDocument
import eu.europa.ec.eudi.iso18013.transfer.response.RequestedDocuments
import eu.europa.ec.eudi.iso18013.transfer.response.Response
import eu.europa.ec.eudi.iso18013.transfer.response.ResponseResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.multipaz.crypto.Algorithm
import org.multipaz.securearea.AndroidKeystoreKeyUnlockData
import org.multipaz.securearea.KeyUnlockData
import java.net.InetSocketAddress
import java.net.URI

/**
 * JVM coverage for [DefaultOid4vpGateway], the presentation obtain/send path. The wallet-core and
 * Android surface is behind [PresentationTransport], so a hand-written [FakeTransport] drives the
 * listener/CompletableDeferred choreography with no device: the transport's `onStart`/`onSend`
 * hooks emit [TransferEvent]s exactly where wallet-core would. Obtain-side tests exercise the real
 * `request`/`request_uri` extraction (including the pure query parser) against a loopback
 * `HttpServer`; response-side tests exercise resolve → unlock → authenticate → generate → dispatch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultOid4vpGatewayTest {

    // ---- fakes ----

    private class FakeTransport : PresentationTransport {
        val listeners = mutableListOf<TransferEvent.Listener>()
        var startCalls = 0
        var sendCalls = 0
        var rejectCalls = 0
        var rejectThrows = false
        val unlockByDoc = mutableMapOf<String, KeyUnlockData?>()
        val keyUnlockQueried = mutableListOf<String>()
        var onStart: () -> Unit = {}
        var onSend: () -> Unit = {}

        override fun addTransferEventListener(listener: TransferEvent.Listener) { listeners += listener }
        override fun removeTransferEventListener(listener: TransferEvent.Listener) { listeners -= listener }
        override fun startRemotePresentation(requestUri: String) { startCalls++; onStart() }
        override fun sendResponse(response: Response) { sendCalls++; onSend() }
        override fun rejectRemotePresentation() { rejectCalls++; if (rejectThrows) throw IllegalStateException("reject boom") }
        override fun keyUnlockDataFor(documentId: String): KeyUnlockData? {
            keyUnlockQueried += documentId
            return unlockByDoc[documentId]
        }

        /** Deliver [event] to every currently-registered listener (mirrors wallet-core's dispatch). */
        fun emit(event: TransferEvent) = listeners.toList().forEach { it.onTransferEvent(event) }
    }

    private class FakeAuthenticator : PresentationAuthenticator {
        var calls = 0
        var lastArg: List<AndroidKeystoreKeyUnlockData>? = null
        var onCall: () -> Unit = {}
        var toThrow: Throwable? = null
        override suspend fun authenticate(unlockData: List<AndroidKeystoreKeyUnlockData>) {
            calls++
            lastArg = unlockData
            onCall()
            toThrow?.let { throw it }
        }
    }

    // ---- helpers ----

    private val fakeRequest = object : Request {}

    private fun response(): Response = object : Response {}

    private fun docItem(): DocItem = object : DocItem {}

    private fun requestedDocs(vararg ids: String): RequestedDocuments =
        RequestedDocuments(ids.map { id -> RequestedDocument(id, mapOf(docItem() to true), readerAuth = null) })

    /** A subclassable [RequestProcessor.ProcessedRequest.Success] recording the disclosed docs. */
    private class FakeSuccess(
        docs: RequestedDocuments,
        private val result: (DisclosedDocuments) -> ResponseResult,
        private val onGenerate: () -> Unit = {},
    ) : RequestProcessor.ProcessedRequest.Success(docs) {
        var receivedDisclosed: DisclosedDocuments? = null
        override fun generateResponse(disclosedDocuments: DisclosedDocuments, signatureAlgorithm: Algorithm?): ResponseResult {
            receivedDisclosed = disclosedDocuments
            onGenerate()
            return result(disclosedDocuments)
        }
    }

    private fun received(processed: RequestProcessor.ProcessedRequest) =
        TransferEvent.RequestReceived(processed, fakeRequest)

    private fun gateway(transport: FakeTransport, auth: FakeAuthenticator) =
        DefaultOid4vpGateway(transport, auth)

    private fun validated() = ValidatedPresentationRequest(
        verifierIdentity = "verifier.example",
        responseOrigin = "https://verifier.example",
        responseUri = "https://verifier.example/response",
        purpose = null,
        vct = "urn:eudi:pid:1",
        requestedClaims = listOf(RequestedClaim("given_name")),
        nonce = "n",
        verifierInfo = null,
    )

    /** Prime the gateway's in-flight request URI via obtain, so sendResponse has something to answer. */
    private suspend fun DefaultOid4vpGateway.prime() {
        obtainSignedRequest("openid4vp://x?request=primed")
    }

    // ---- obtain side ----

    @Test
    fun `an inline request value is returned percent-decoded without a fetch`() = runTest {
        val transport = FakeTransport()
        // %2E decodes to '.'; no HttpServer is started, so any fetch attempt would fail the test.
        val signed = gateway(transport, FakeAuthenticator())
            .obtainSignedRequest("openid4vp://consume?request=a%2Eb%2Ec")
        assertEquals("a.b.c", signed.compactJws)
    }

    @Test
    fun `a request_uri body is fetched with the JWT accept header`() = runTest {
        var seenAccept: String? = null
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/req") { exchange ->
                seenAccept = exchange.requestHeaders.getFirst("Accept")
                val body = "SIGNED.JWT.BODY".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        try {
            val port = server.address.port
            val signed = gateway(FakeTransport(), FakeAuthenticator())
                .obtainSignedRequest("openid4vp://x?request_uri=http://127.0.0.1:$port/req")
            assertEquals("SIGNED.JWT.BODY", signed.compactJws)
            assertEquals("application/oauth-authz-req+jwt, application/jwt", seenAccept)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `an inline request wins over a request_uri and stops at the fragment`() = runTest {
        var hits = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/req") { exchange -> hits++; exchange.sendResponseHeaders(500, -1); exchange.close() }
            start()
        }
        try {
            val port = server.address.port
            val signed = gateway(FakeTransport(), FakeAuthenticator()).obtainSignedRequest(
                "openid4vp://x?request_uri=http://127.0.0.1:$port/req&request=INLINE#request=ignored",
            )
            assertEquals("INLINE", signed.compactJws)
            assertEquals("request_uri must not be fetched when an inline request is present", 0, hits)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `neither request nor request_uri raises IllegalArgumentException`() = runTest {
        val ex = runCatching {
            gateway(FakeTransport(), FakeAuthenticator()).obtainSignedRequest("openid4vp://x?foo=bar")
        }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException but was $ex", ex is IllegalArgumentException)
    }

    @Test
    fun `a non-2xx request_uri raises IllegalStateException`() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/req") { exchange -> exchange.sendResponseHeaders(404, -1); exchange.close() }
            start()
        }
        try {
            val port = server.address.port
            val ex = runCatching {
                gateway(FakeTransport(), FakeAuthenticator())
                    .obtainSignedRequest("openid4vp://x?request_uri=http://127.0.0.1:$port/req")
            }.exceptionOrNull()
            assertTrue("expected IllegalStateException but was $ex", ex is IllegalStateException)
            assertTrue("message should carry the status: ${ex?.message}", ex?.message?.contains("404") == true)
        } finally {
            server.stop(0)
        }
    }

    // ---- response side ----

    @Test
    fun `sendResponse without a prior obtain fails fast`() = runTest {
        val transport = FakeTransport()
        val ex = runCatching { gateway(transport, FakeAuthenticator()).sendResponse(validated(), emptyList()) }
            .exceptionOrNull()
        assertTrue("expected IllegalStateException but was $ex", ex is IllegalStateException)
        assertEquals("no in-flight presentation to answer", ex?.message)
        assertEquals(0, transport.startCalls)
    }

    @Test
    fun `per-document key unlock data is fetched and passed to generateResponse`() = runTest {
        val transport = FakeTransport()
        val kA = object : KeyUnlockData {}
        val kB = object : KeyUnlockData {}
        transport.unlockByDoc["a"] = kA
        transport.unlockByDoc["b"] = kB
        val success = FakeSuccess(requestedDocs("a", "b"), result = { ResponseResult.Success(response()) })
        transport.onStart = { transport.emit(received(success)) }
        transport.onSend = { transport.emit(TransferEvent.ResponseSent) }
        val g = gateway(transport, FakeAuthenticator())
        g.prime()

        g.sendResponse(validated(), emptyList())

        assertEquals(listOf("a", "b"), transport.keyUnlockQueried)
        val disclosed = success.receivedDisclosed
        assertNotNull("generateResponse should have received disclosed documents", disclosed)
        assertSame(kA, disclosed!![0].keyUnlockData)
        assertSame(kB, disclosed[1].keyUnlockData)
    }

    @Test
    fun `the user is authenticated before the response is generated`() = runTest {
        val transport = FakeTransport()
        val order = mutableListOf<String>()
        val auth = FakeAuthenticator().apply { onCall = { order += "auth" } }
        val success = FakeSuccess(
            requestedDocs("a"),
            result = { ResponseResult.Success(response()) },
            onGenerate = { order += "gen" },
        )
        transport.onStart = { transport.emit(received(success)) }
        transport.onSend = { transport.emit(TransferEvent.ResponseSent) }
        val g = gateway(transport, auth)
        g.prime()

        g.sendResponse(validated(), emptyList())

        assertEquals(listOf("auth", "gen"), order)
    }

    @Test
    fun `an authentication failure aborts before any response is generated`() = runTest {
        val transport = FakeTransport()
        val auth = FakeAuthenticator().apply { toThrow = UserAuthException("declined") }
        var generated = false
        val success = FakeSuccess(
            requestedDocs("a"),
            result = { ResponseResult.Success(response()) },
            onGenerate = { generated = true },
        )
        transport.onStart = { transport.emit(received(success)) }
        val g = gateway(transport, auth)
        g.prime()

        val ex = runCatching { g.sendResponse(validated(), emptyList()) }.exceptionOrNull()

        assertTrue("expected UserAuthException but was $ex", ex is UserAuthException)
        assertTrue("generateResponse must not run after an auth failure", !generated)
        assertEquals("no response should be dispatched", 0, transport.sendCalls)
    }

    @Test
    fun `a generateResponse failure surfaces and nothing is dispatched`() = runTest {
        val transport = FakeTransport()
        val boom = IllegalStateException("generate boom")
        val success = FakeSuccess(requestedDocs("a"), result = { ResponseResult.Failure(boom) })
        transport.onStart = { transport.emit(received(success)) }
        val g = gateway(transport, FakeAuthenticator())
        g.prime()

        val ex = runCatching { g.sendResponse(validated(), emptyList()) }.exceptionOrNull()

        assertSame(boom, ex)
        assertEquals(0, transport.sendCalls)
    }

    @Test
    fun `dispatch completes only when ResponseSent arrives`() = runTest {
        val transport = FakeTransport()
        val success = FakeSuccess(requestedDocs("a"), result = { ResponseResult.Success(response()) })
        transport.onStart = { transport.emit(received(success)) }
        transport.onSend = { transport.emit(TransferEvent.ResponseSent) }
        val g = gateway(transport, FakeAuthenticator())
        g.prime()

        g.sendResponse(validated(), emptyList()) // returns normally

        assertEquals(1, transport.sendCalls)
    }

    @Test
    fun `a Redirect also completes the dispatch`() = runTest {
        val transport = FakeTransport()
        val success = FakeSuccess(requestedDocs("a"), result = { ResponseResult.Success(response()) })
        transport.onStart = { transport.emit(received(success)) }
        transport.onSend = { transport.emit(TransferEvent.Redirect(URI.create("https://verifier.example/redirect"))) }
        val g = gateway(transport, FakeAuthenticator())
        g.prime()

        g.sendResponse(validated(), emptyList()) // returns normally

        assertEquals(1, transport.sendCalls)
    }

    @Test
    fun `a TransferEvent Error fails the dispatch with the same throwable`() = runTest {
        val transport = FakeTransport()
        val boom = RuntimeException("verifier rejected")
        val success = FakeSuccess(requestedDocs("a"), result = { ResponseResult.Success(response()) })
        transport.onStart = { transport.emit(received(success)) }
        transport.onSend = { transport.emit(TransferEvent.Error(boom)) }
        val g = gateway(transport, FakeAuthenticator())
        g.prime()

        val ex = runCatching { g.sendResponse(validated(), emptyList()) }.exceptionOrNull()

        // The verifier's error surfaces as a thrown failure (never a false "Shared"). Identity is not
        // asserted: crossing the CompletableDeferred.await triggers coroutine stacktrace recovery,
        // which rethrows a copy with the original type and message preserved.
        assertTrue("expected the RuntimeException to surface but was $ex", ex is RuntimeException)
        assertEquals("verifier rejected", ex?.message)
    }

    @Test
    fun `no transfer event leaves the dispatch pending without a false success`() = runTest {
        val transport = FakeTransport()
        val success = FakeSuccess(requestedDocs("a"), result = { ResponseResult.Success(response()) })
        transport.onStart = { transport.emit(received(success)) }
        // onSend emits nothing: wallet-core has POSTed but the verifier's answer has not arrived.
        val g = gateway(transport, FakeAuthenticator())
        g.prime()

        val job = launch { runCatching { g.sendResponse(validated(), emptyList()) } }
        advanceUntilIdle()

        assertTrue("dispatch must stay suspended until an event arrives", job.isActive)
        assertEquals("the response was dispatched, just not yet acknowledged", 1, transport.sendCalls)
        job.cancel()
    }

    @Test
    fun `a synchronously emitted RequestReceived is caught and listeners are cleaned up`() = runTest {
        val transport = FakeTransport()
        val success = FakeSuccess(requestedDocs("a"), result = { ResponseResult.Success(response()) })
        // Emit RequestReceived synchronously inside startRemotePresentation, before await runs: only
        // works because the listener is registered before startRemotePresentation is called.
        transport.onStart = { transport.emit(received(success)) }
        transport.onSend = { transport.emit(TransferEvent.ResponseSent) }
        val g = gateway(transport, FakeAuthenticator())
        g.prime()

        g.sendResponse(validated(), emptyList())

        assertEquals(1, transport.sendCalls)
        assertTrue("every listener must be removed in the finally blocks", transport.listeners.isEmpty())
    }

    @Test
    fun `a resolve-phase Error aborts before unlock or authentication`() = runTest {
        val transport = FakeTransport()
        val auth = FakeAuthenticator()
        val boom = RuntimeException("resolve failed")
        transport.onStart = { transport.emit(TransferEvent.Error(boom)) }
        val g = gateway(transport, auth)
        g.prime()

        val ex = runCatching { g.sendResponse(validated(), emptyList()) }.exceptionOrNull()

        // As above, identity is not asserted across the await (stacktrace recovery); type + message are.
        assertTrue("expected the resolve error to surface but was $ex", ex is RuntimeException)
        assertEquals("resolve failed", ex?.message)
        assertEquals("no key unlock before the request resolves", emptyList<String>(), transport.keyUnlockQueried)
        assertEquals("no authentication before the request resolves", 0, auth.calls)
        assertEquals(0, transport.sendCalls)
    }

    @Test
    fun `a ProcessedRequest Failure aborts before unlock or authentication`() = runTest {
        val transport = FakeTransport()
        val auth = FakeAuthenticator()
        val boom = IllegalStateException("request processing failed")
        transport.onStart = { transport.emit(received(RequestProcessor.ProcessedRequest.Failure(boom))) }
        val g = gateway(transport, auth)
        g.prime()

        val ex = runCatching { g.sendResponse(validated(), emptyList()) }.exceptionOrNull()

        assertSame(boom, ex)
        assertEquals(emptyList<String>(), transport.keyUnlockQueried)
        assertEquals(0, auth.calls)
        assertEquals(0, transport.sendCalls)
    }

    @Test
    fun `reject swallows a transport failure and is best-effort`() {
        val transport = FakeTransport().apply { rejectThrows = true }
        try {
            gateway(transport, FakeAuthenticator()).reject()
        } catch (t: Throwable) {
            fail("reject must be best-effort, but threw $t")
        }
        assertEquals(1, transport.rejectCalls)
    }
}
