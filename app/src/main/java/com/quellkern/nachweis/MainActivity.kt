package com.quellkern.nachweis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.quellkern.nachweis.ui.HomeScreen
import com.quellkern.nachweis.ui.theme.NachweisTheme

/**
 * Single entry-point activity. The wallet UI is composed from here; feature slices
 * (issuance, presentation) will be added as the app grows past this foundation.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NachweisTheme {
                HomeScreen()
            }
        }
    }
}
