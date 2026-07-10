package com.quellkern.nachweis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quellkern.nachweis.R
import com.quellkern.nachweis.ui.theme.NachweisTheme
import com.quellkern.nachweis.wallet.WalletState

/**
 * Renders the wallet lifecycle while it is not yet [WalletState.Ready] (starting, or a typed
 * failure). Once ready, [WalletScreen] takes over with the credential list and issuance flow.
 */
@Composable
fun WalletStatus(state: WalletState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = walletStatusText(state),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 8.dp)
                // Announce status changes to TalkBack as they happen.
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

private fun walletStatusText(state: WalletState): String = when (state) {
    WalletState.Uninitialized -> "Starting the wallet…"
    WalletState.Initializing -> "Preparing secure storage…"
    is WalletState.Ready -> "Wallet ready."
    is WalletState.Failed -> state.error.publicMessage
}

@Preview(showBackground = true)
@Composable
private fun WalletStatusPreview() {
    NachweisTheme {
        WalletStatus(state = WalletState.Uninitialized)
    }
}
