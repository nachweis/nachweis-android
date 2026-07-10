package com.quellkern.nachweis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.quellkern.nachweis.ui.HomeScreen
import com.quellkern.nachweis.ui.theme.NachweisTheme
import com.quellkern.nachweis.wallet.WalletController

/**
 * Single entry-point activity. It observes the wallet lifecycle and renders it; feature
 * slices (issuance, presentation) will be added as the app grows past this foundation.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val controller: WalletController = (application as NachweisApp).walletController
        controller.initialize(this)

        setContent {
            NachweisTheme {
                val state by controller.state.collectAsState()
                HomeScreen(state = state)
            }
        }
    }
}
