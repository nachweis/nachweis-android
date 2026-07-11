package com.quellkern.nachweis.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.quellkern.nachweis.ui.theme.NachweisTheme
import org.junit.Rule
import org.junit.Test

/**
 * Regression guard for evidence finding #2: system Back from the QR scanner used to fall through
 * to the single root activity and eject the user to the launcher. [WalletReadyContent] now installs
 * a [androidx.activity.compose.BackHandler] while the scanner is open, so Back returns to the
 * credential list instead. The scanner is stubbed (the real one needs the camera); only the
 * navigation is under test.
 */
class ScannerBackNavigationInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun systemBackFromScannerReturnsToListNotLauncher() {
        composeRule.setContent {
            NachweisTheme {
                WalletReadyContent(
                    documents = emptyList(),
                    onScanned = {},
                    scanner = { _, _, _ -> Text("SCANNER-STUB") },
                )
            }
        }

        // Start on the credential list (empty state).
        composeRule.onNodeWithText("No credentials yet").assertIsDisplayed()

        // Open the scanner.
        composeRule.onNodeWithContentDescription("Scan QR").performClick()
        composeRule.onNodeWithText("SCANNER-STUB").assertIsDisplayed()

        // System Back must return to the list, not propagate to the activity (which, as the app's
        // only root, would exit to the launcher).
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithText("No credentials yet").assertIsDisplayed()
        composeRule.onNodeWithText("SCANNER-STUB").assertDoesNotExist()
    }
}
