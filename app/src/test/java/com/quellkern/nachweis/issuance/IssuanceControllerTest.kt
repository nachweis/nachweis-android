package com.quellkern.nachweis.issuance

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IssuanceControllerTest {

    private val allowlist = IssuerAllowlist(listOf("https://api-sandbox.nachweis.tech"))

    private fun pidOffer(issuer: String = "https://api-sandbox.nachweis.tech/issuers/nachweis") =
        ResolvedOffer(
            issuerIdentifier = issuer,
            offeredCredentials = listOf(OfferedCredential("pid-no-key", OfferEvaluation.EXPECTED_VCT, null, "PID")),
            requiresTransactionCode = false,
        )

    /** Configurable fake gateway; no wallet-core, no Android. */
    private class FakeGateway(
        val resolve: (String) -> ResolvedOffer,
        val progress: List<IssuanceProgress> = emptyList(),
    ) : Oid4vciGateway {
        var lastConfigId: String? = null
        override suspend fun resolveOffer(offerUri: String): ResolvedOffer = resolve(offerUri)
        override fun issue(offerUri: String, configurationIdentifier: String, transactionCode: String?): Flow<IssuanceProgress> {
            lastConfigId = configurationIdentifier
            return progress.asFlow()
        }
        override fun resumeWithAuthorization(uri: android.net.Uri) = Unit
    }

    @Test
    fun `acceptable offer stops at consent, then issues on confirm`() = runTest {
        val gateway = FakeGateway(
            resolve = { pidOffer() },
            progress = listOf(
                IssuanceProgress.Started,
                IssuanceProgress.Issued("doc-1", "PID"),
                IssuanceProgress.Finished,
            ),
        )
        val controller = IssuanceController(gateway, allowlist, this)

        controller.offer("openid-credential-offer://x")
        advanceUntilIdle()
        val consent = controller.state.value
        assertTrue(consent is IssuanceState.AwaitingConsent)
        assertEquals(OfferEvaluation.EXPECTED_VCT, (consent as IssuanceState.AwaitingConsent).vct)

        controller.confirm()
        advanceUntilIdle()
        val issued = controller.state.value
        assertTrue(issued is IssuanceState.Issued)
        assertEquals("doc-1", (issued as IssuanceState.Issued).documentId)
        assertEquals("pid-no-key", gateway.lastConfigId)
    }

    @Test
    fun `unlisted issuer is declined and never requests issuance`() = runTest {
        val gateway = FakeGateway(resolve = { pidOffer(issuer = "https://evil.example.com/x") })
        val controller = IssuanceController(gateway, allowlist, this)

        controller.offer("openid-credential-offer://x")
        advanceUntilIdle()
        val state = controller.state.value
        assertTrue(state is IssuanceState.Declined)
        assertEquals(IssuanceError.IssuerNotAllowed, (state as IssuanceState.Declined).reason)
        assertEquals(null, gateway.lastConfigId)
    }

    @Test
    fun `confirm is a no-op unless awaiting consent`() = runTest {
        val gateway = FakeGateway(resolve = { pidOffer() })
        val controller = IssuanceController(gateway, allowlist, this)

        controller.confirm() // nothing pending
        advanceUntilIdle()
        assertEquals(IssuanceState.Idle, controller.state.value)
        assertEquals(null, gateway.lastConfigId)
    }

    @Test
    fun `resolve failure maps to a typed failure`() = runTest {
        val gateway = FakeGateway(resolve = { throw java.net.UnknownHostException("api-sandbox") })
        val controller = IssuanceController(gateway, allowlist, this)

        controller.offer("openid-credential-offer://x")
        advanceUntilIdle()
        val state = controller.state.value
        assertTrue(state is IssuanceState.Failed)
        assertEquals(IssuanceError.Network, (state as IssuanceState.Failed).error)
    }

    @Test
    fun `user-auth step surfaces then resolves to issued`() = runTest {
        val gateway = FakeGateway(
            resolve = { pidOffer() },
            progress = listOf(
                IssuanceProgress.Started,
                IssuanceProgress.AwaitingUserAuth,
                IssuanceProgress.Issued("doc-2", "PID"),
                IssuanceProgress.Finished,
            ),
        )
        val controller = IssuanceController(gateway, allowlist, this)
        controller.offer("openid-credential-offer://x")
        advanceUntilIdle()
        controller.confirm()
        advanceUntilIdle()
        assertTrue(controller.state.value is IssuanceState.Issued)
    }

    @Test
    fun `issuance failure maps to a typed failure`() = runTest {
        val gateway = FakeGateway(
            resolve = { pidOffer() },
            progress = listOf(IssuanceProgress.Started, IssuanceProgress.Failed(java.net.ConnectException("refused"))),
        )
        val controller = IssuanceController(gateway, allowlist, this)
        controller.offer("openid-credential-offer://x")
        advanceUntilIdle()
        controller.confirm()
        advanceUntilIdle()
        val state = controller.state.value
        assertTrue(state is IssuanceState.Failed)
        assertEquals(IssuanceError.Network, (state as IssuanceState.Failed).error)
    }

    @Test
    fun `presentation deep link sets the not-yet-supported state`() = runTest {
        val controller = IssuanceController(FakeGateway(resolve = { pidOffer() }), allowlist, this)
        controller.onPresentation()
        assertEquals(IssuanceState.PresentationNotYetSupported, controller.state.value)
    }
}
