package com.quellkern.nachweis

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.quellkern.nachweis.deeplink.DeepLinkAction
import com.quellkern.nachweis.deeplink.DeepLinkIntake
import com.quellkern.nachweis.issuance.IssuanceController
import com.quellkern.nachweis.issuance.IssuanceState
import com.quellkern.nachweis.presentation.PresentationController
import com.quellkern.nachweis.presentation.PresentationState
import com.quellkern.nachweis.ui.WalletScreen
import com.quellkern.nachweis.ui.theme.NachweisTheme
import com.quellkern.nachweis.wallet.WalletController
import com.quellkern.nachweis.wallet.WalletState

/**
 * Single entry-point activity. It observes the wallet lifecycle, hosts the credential list
 * and QR scanner, and routes inbound deep links (credential offers, presentation requests,
 * and our own auth-code redirect) to the issuance controller once the wallet is ready.
 *
 * A [FragmentActivity] (not a bare ComponentActivity) because the issuance device-auth step
 * uses BiometricPrompt, which requires one.
 */
class MainActivity : FragmentActivity() {

    private val app: NachweisApp get() = application as NachweisApp
    private val walletController: WalletController get() = app.walletController

    // The offer/presentation URI captured before the wallet is ready, replayed once it is.
    private var pendingDeepLink: DeepLinkAction? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        walletController.initialize(this)
        pendingDeepLink = DeepLinkIntake.classify(intent?.data)

        setContent {
            NachweisTheme {
                val walletState by walletController.state.collectAsState()
                WalletHost(walletState)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val ready = walletController.state.value as? WalletState.Ready
        route(
            DeepLinkIntake.classify(intent.data),
            ready?.let { app.issuanceController(it.wallet) },
            ready?.let { app.presentationController(it.wallet) },
        )
    }

    override fun onResume() {
        super.onResume()
        app.setForegroundActivity(this)
    }

    override fun onPause() {
        app.setForegroundActivity(null)
        super.onPause()
    }

    @androidx.compose.runtime.Composable
    private fun WalletHost(walletState: WalletState) {
        val ready = walletState as? WalletState.Ready
        if (ready == null) {
            WalletScreen(
                walletState = walletState,
                issuanceState = IssuanceState.Idle,
                presentationState = PresentationState.Idle,
                documents = emptyList(),
                onScanned = {},
                onConfirm = {},
                onDecline = {},
                onDismissResult = {},
                onPresentationConfirm = {},
                onPresentationDecline = {},
                onPresentationDismiss = {},
            )
            return
        }

        val controller = remember(ready.wallet) { app.issuanceController(ready.wallet) }
        val presentation = remember(ready.wallet) { app.presentationController(ready.wallet) }
        val store = remember(ready.wallet) { app.documentStore(ready.wallet) }
        val issuanceState by controller.state.collectAsState()
        val presentationState by presentation.state.collectAsState()
        var documents by remember { mutableStateOf(store.list()) }

        // Replay a deep link that arrived before the wallet was ready, exactly once.
        LaunchedEffect(controller, presentation) {
            pendingDeepLink?.let { action ->
                pendingDeepLink = null
                route(action, controller, presentation)
            }
        }
        // Refresh the list whenever a credential is issued.
        LaunchedEffect(issuanceState) {
            if (issuanceState is IssuanceState.Issued) documents = store.list()
        }

        WalletScreen(
            walletState = walletState,
            issuanceState = issuanceState,
            presentationState = presentationState,
            documents = documents,
            onScanned = { value ->
                route(DeepLinkIntake.classify(android.net.Uri.parse(value)), controller, presentation)
            },
            onConfirm = { txCode -> controller.confirm(txCode) },
            onDecline = { controller.reset() },
            onDismissResult = { controller.reset() },
            onPresentationConfirm = { presentation.confirm() },
            onPresentationDecline = { presentation.decline() },
            onPresentationDismiss = { presentation.reset() },
            loadDetail = { id -> store.details(id) },
            viewGate = { app.authenticateToViewClaims() },
            onDelete = { id ->
                val deleted = store.delete(id)
                if (deleted) documents = store.list()
                deleted
            },
        )
    }

    // Dispatch a classified deep link. Offers and presentations need their ready controller;
    // if the wallet isn't ready the action is stashed and replayed once it is.
    private fun route(
        action: DeepLinkAction,
        issuance: IssuanceController?,
        presentation: PresentationController?,
    ) {
        when (action) {
            is DeepLinkAction.CredentialOffer ->
                if (issuance != null) issuance.offer(action.offerUri) else pendingDeepLink = action
            is DeepLinkAction.Presentation ->
                // The presentation controller now triggers and *awaits* the out-of-band status
                // refresh itself (bounded) in its pre-consent Resolving phase, so the arrival refresh
                // is ordered before registration evaluation reads the cache. Consent still reads only
                // the cache and touches no network. When the wallet is not yet ready the request is
                // stashed and replayed once it is.
                if (presentation != null) presentation.onRequest(action.requestUri) else pendingDeepLink = action
            is DeepLinkAction.AuthorizationCallback ->
                issuance?.onAuthorizationCallback(android.net.Uri.parse(action.uri))
            DeepLinkAction.Unknown -> Unit
        }
    }
}
