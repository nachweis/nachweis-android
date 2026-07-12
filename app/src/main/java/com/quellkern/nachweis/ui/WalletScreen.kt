package com.quellkern.nachweis.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quellkern.nachweis.issuance.CredentialDetail
import com.quellkern.nachweis.issuance.DocumentSummary
import com.quellkern.nachweis.issuance.IssuanceState
import com.quellkern.nachweis.presentation.PresentationState
import com.quellkern.nachweis.presentation.RegistrationVerdict
import com.quellkern.nachweis.presentation.presentationFailureTitle
import com.quellkern.nachweis.presentation.ValidatedPresentationRequest
import com.quellkern.nachweis.ui.components.StatusBanner
import com.quellkern.nachweis.ui.components.StatusKind
import com.quellkern.nachweis.ui.theme.MonoTextStyle
import com.quellkern.nachweis.wallet.WalletState

/**
 * Top-level wallet UI. Switches between the credential list and the QR scanner, and overlays
 * the issuance consent/result and the presentation consent/result on top. Restrained by
 * design — the distinctive visual system is C-late. State is hoisted: this composable renders
 * [walletState], [issuanceState], and [presentationState] and calls back for every action.
 */
@Composable
fun WalletScreen(
    walletState: WalletState,
    issuanceState: IssuanceState,
    presentationState: PresentationState,
    documents: List<DocumentSummary>,
    onScanned: (String) -> Unit,
    onConfirm: (String?) -> Unit,
    onDecline: () -> Unit,
    onDismissResult: () -> Unit,
    onPresentationConfirm: () -> Unit,
    onPresentationDecline: () -> Unit,
    onPresentationDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    loadDetail: (String) -> CredentialDetail? = { null },
) {
    Scaffold(modifier = modifier) { padding ->
        val content = Modifier.padding(padding)
        when (walletState) {
            is WalletState.Ready ->
                WalletReadyContent(
                    documents = documents,
                    onScanned = onScanned,
                    modifier = content,
                    loadDetail = loadDetail,
                )
            else -> WalletStatus(walletState, modifier = content)
        }
    }

    IssuanceOverlay(
        state = issuanceState,
        onConfirm = onConfirm,
        onDecline = onDecline,
        onDismissResult = onDismissResult,
    )

    PresentationOverlay(
        state = presentationState,
        onConfirm = onPresentationConfirm,
        onDecline = onPresentationDecline,
        onDismiss = onPresentationDismiss,
    )
}

/**
 * The ready wallet's body: the credential list, or the QR scanner while the user has it open.
 * The scanner visibility is local state here, and a [BackHandler] is installed while scanning so
 * the system Back gesture returns to the credential list instead of falling through to the
 * activity — which, as the app's single root, would otherwise exit to the launcher (evidence
 * finding #2). [scanner] is injectable purely so the back-navigation is testable without the
 * camera; production always uses [ScanScreen].
 */
@Composable
internal fun WalletReadyContent(
    documents: List<DocumentSummary>,
    onScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
    loadDetail: (String) -> CredentialDetail? = { null },
    scanner: @Composable (onScanned: (String) -> Unit, onCancel: () -> Unit, modifier: Modifier) -> Unit =
        { onScan, onCancel, scanModifier ->
            ScanScreen(onScanned = onScan, onCancel = onCancel, modifier = scanModifier)
        },
) {
    var scanning by remember { mutableStateOf(false) }
    var openDocumentId by remember { mutableStateOf<String?>(null) }
    // Resolve the tapped credential's claims lazily and only while a detail is open; keyed on the
    // id so it re-resolves after a refresh (e.g. the document was removed) and yields null then.
    val detail = remember(openDocumentId) { openDocumentId?.let(loadDetail) }

    // Only intercept Back while the scanner is showing; on the list, Back keeps its default
    // (leave the app), so this never traps the user on the credential list. The scanner takes
    // precedence over the detail view; each handler is enabled only for its own surface.
    BackHandler(enabled = scanning) { scanning = false }
    BackHandler(enabled = !scanning && detail != null) { openDocumentId = null }

    when {
        scanning -> scanner(
            { value ->
                scanning = false
                onScanned(value)
            },
            { scanning = false },
            modifier,
        )
        detail != null -> CredentialDetailScreen(
            detail = detail,
            onBack = { openDocumentId = null },
            modifier = modifier,
        )
        else -> DocumentListScreen(
            documents = documents,
            onScanClick = { scanning = true },
            modifier = modifier,
            onDocumentClick = { openDocumentId = it },
        )
    }
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
        IssuanceState.Idle -> Unit
    }
}

@Composable
private fun PresentationOverlay(
    state: PresentationState,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is PresentationState.AwaitingConsent -> PresentationConsentDialog(state.request, onConfirm, onDecline)
        PresentationState.Resolving -> ProgressDialog("Checking the request…")
        PresentationState.Sending -> ProgressDialog("Sharing your credential…")
        PresentationState.Sent -> ResultDialog("Shared", "Your credential was shared with the verifier.", onDismiss)
        PresentationState.Declined -> ResultDialog("Request declined", "Nothing was shared.", onDismiss)
        is PresentationState.Rejected ->
            ResultDialog(presentationFailureTitle(state.error), state.error.publicMessage, onDismiss)
        PresentationState.Idle -> Unit
    }
}

/**
 * The presentation consent surface (dev-plan.md D1 surface / design-tokens.md §6.3). Shows the
 * verifier identity, a **trust verdict banner**, and the exact claims requested, then asks the
 * user to allow or decline. The verdict banner is the D1 seam: B5 always produces
 * [RegistrationVerdict.NotEvaluated], so this renders a neutral "sender verified" banner and
 * never the "outside this verifier's registration" wording, which only D1 can establish.
 */
@Composable
private fun PresentationConsentDialog(
    request: ValidatedPresentationRequest,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Share your credential?") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                VerdictBanner(request.registrationVerdict, request.verifierIdentity)
                request.purpose?.takeIf { it.isNotBlank() }?.let { purpose ->
                    Text(
                        text = purpose,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                Text(
                    text = "This verifier is asking for:",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                request.requestedClaims.forEach { claim ->
                    Text(
                        text = claim.path,
                        style = MonoTextStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .semantics { contentDescription = "Requested: ${claim.path}" },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Allow") }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Decline") }
        },
    )
}

/**
 * The trust verdict banner (design-tokens.md §6.3), rendered with the neo-brutal [StatusBanner]:
 * status-hue fill, ink text and glyph, ink border. For B5's [RegistrationVerdict.NotEvaluated] it
 * states only what B5 proved (the sender's certificate is trusted and current) using the verified
 * glyph. The [RegistrationVerdict.OutsideRegistration] copy is fixed by dev-plan.md D1 ("outside
 * this verifier's registration"), shown on the over-red fill with the distinct slash-circle glyph;
 * the word "over-ask" never appears.
 */
@Composable
private fun VerdictBanner(verdict: RegistrationVerdict, verifierIdentity: String) {
    val (kind, message) = when (verdict) {
        RegistrationVerdict.NotEvaluated ->
            StatusKind.Verified to "Verified sender: $verifierIdentity"
        RegistrationVerdict.InsideRegistration ->
            StatusKind.Verified to "$verifierIdentity — within this verifier's registration"
        is RegistrationVerdict.OutsideRegistration ->
            StatusKind.OutsideRegistration to
                "$verifierIdentity — outside this verifier's registration. " +
                "Outside its registration: ${verdict.claimsOutside.joinToString(", ")}"
    }
    StatusBanner(kind = kind, message = message)
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
