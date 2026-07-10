package com.quellkern.nachweis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
 * Foundation home screen. It renders the wallet lifecycle state; there is no credential
 * flow yet. Issuance and presentation slices replace this placeholder in later phases.
 */
@Composable
fun HomeScreen(state: WalletState, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
}

private fun walletStatusText(state: WalletState): String = when (state) {
    WalletState.Uninitialized -> "Starting the wallet…"
    WalletState.Initializing -> "Preparing secure storage…"
    is WalletState.Ready -> "Wallet ready. No credential flow yet."
    is WalletState.Failed -> state.error.publicMessage
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    NachweisTheme {
        HomeScreen(state = WalletState.Uninitialized)
    }
}
