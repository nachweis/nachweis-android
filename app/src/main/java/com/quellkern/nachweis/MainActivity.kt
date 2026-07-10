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
        route(DeepLinkIntake.classify(intent.data), controllerIfReady())
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
                documents = emptyList(),
                onScanned = {},
                onConfirm = {},
                onDecline = {},
                onDismissResult = {},
            )
            return
        }

        val controller = remember(ready.wallet) { app.issuanceController(ready.wallet) }
        val store = remember(ready.wallet) { app.documentStore(ready.wallet) }
        val issuanceState by controller.state.collectAsState()
        var documents by remember { mutableStateOf(store.list()) }

        // Replay a deep link that arrived before the wallet was ready, exactly once.
        LaunchedEffect(controller) {
            pendingDeepLink?.let { action ->
                pendingDeepLink = null
                route(action, controller)
            }
        }
        // Refresh the list whenever a credential is issued.
        LaunchedEffect(issuanceState) {
            if (issuanceState is IssuanceState.Issued) documents = store.list()
        }

        WalletScreen(
            walletState = walletState,
            issuanceState = issuanceState,
            documents = documents,
            onScanned = { value -> route(DeepLinkIntake.classify(android.net.Uri.parse(value)), controller) },
            onConfirm = { txCode -> controller.confirm(txCode) },
            onDecline = { controller.reset() },
            onDismissResult = { controller.reset() },
        )
    }

    private fun controllerIfReady(): IssuanceController? =
        (walletController.state.value as? WalletState.Ready)?.let { app.issuanceController(it.wallet) }

    // Dispatch a classified deep link. Offers and presentations need a ready controller; if
    // one isn't available yet the offer is stashed and replayed when the wallet becomes ready.
    private fun route(action: DeepLinkAction, controller: IssuanceController?) {
        when (action) {
            is DeepLinkAction.CredentialOffer ->
                if (controller != null) controller.offer(action.offerUri) else pendingDeepLink = action
            is DeepLinkAction.Presentation ->
                if (controller != null) controller.onPresentation() else pendingDeepLink = action
            is DeepLinkAction.AuthorizationCallback ->
                controller?.onAuthorizationCallback(android.net.Uri.parse(action.uri))
            DeepLinkAction.Unknown -> Unit
        }
    }
}
