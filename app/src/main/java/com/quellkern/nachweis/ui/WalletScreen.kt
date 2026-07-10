package com.quellkern.nachweis.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.quellkern.nachweis.issuance.DocumentSummary
import com.quellkern.nachweis.issuance.IssuanceState
import com.quellkern.nachweis.wallet.WalletState

/**
 * Top-level wallet UI. Switches between the credential list and the QR scanner, and overlays
 * the issuance consent step and terminal result on top. Deliberately restrained; the
 * distinctive system and richer consent surface are C-late / B5 concerns. State is hoisted:
 * this composable renders [walletState] and [issuanceState] and calls back for every action.
 */
@Composable
fun WalletScreen(
    walletState: WalletState,
    issuanceState: IssuanceState,
    documents: List<DocumentSummary>,
    onScanned: (String) -> Unit,
    onConfirm: (String?) -> Unit,
    onDecline: () -> Unit,
    onDismissResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scanning by remember { mutableStateOf(false) }

    Scaffold(modifier = modifier) { padding ->
        val content = Modifier.padding(padding)
        when (walletState) {
            is WalletState.Ready ->
                if (scanning) {
                    ScanScreen(
                        onScanned = { value ->
                            scanning = false
                            onScanned(value)
                        },
                        onCancel = { scanning = false },
                        modifier = content,
                    )
                } else {
                    DocumentListScreen(
                        documents = documents,
                        onScanClick = { scanning = true },
                        modifier = content,
                    )
                }
            else -> WalletStatus(walletState, modifier = content)
        }
    }

    IssuanceOverlay(
        state = issuanceState,
        onConfirm = onConfirm,
        onDecline = {
            scanning = false
            onDecline()
        },
        onDismissResult = onDismissResult,
    )
}

@Composable
private fun IssuanceOverlay(
    state: IssuanceState,
    onConfirm: (String?) -> Unit,
    onDecline: () -> Unit,
    onDismissResult: () -> Unit,
) {
    when (state) {
        is IssuanceState.AwaitingConsent -> ConsentDialog(state, onConfirm, onDecline)
        is IssuanceState.Resolving -> ProgressDialog("Reading the offer…")
        is IssuanceState.Issuing -> ProgressDialog("Adding the credential…")
        is IssuanceState.AwaitingUserAuth -> ProgressDialog("Confirm with device authentication…")
        is IssuanceState.Issued -> ResultDialog("Credential added", state.name, onDismissResult)
        is IssuanceState.Declined -> ResultDialog("Offer declined", state.reason.publicMessage, onDismissResult)
        is IssuanceState.Failed -> ResultDialog("Couldn't add credential", state.error.publicMessage, onDismissResult)
        is IssuanceState.PresentationNotYetSupported ->
            ResultDialog(
                "Not supported yet",
                "Sharing credentials with a verifier arrives in a later version.",
                onDismissResult,
            )
        IssuanceState.Idle -> Unit
    }
}

@Composable
private fun ConsentDialog(
    state: IssuanceState.AwaitingConsent,
    onConfirm: (String?) -> Unit,
    onDecline: () -> Unit,
) {
    var txCode by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Add this credential?") },
        text = {
            Text(
                "${state.credentialName}\nfrom ${state.issuerIdentifier}",
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(txCode.ifBlank { null }) }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text("Decline") }
        },
    )
}

@Composable
private fun ProgressDialog(message: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Text(
                message,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        },
    )
}

@Composable
private fun ResultDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                message,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
