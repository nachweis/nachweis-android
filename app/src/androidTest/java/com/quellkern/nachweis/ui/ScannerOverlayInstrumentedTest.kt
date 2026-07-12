package com.quellkern.nachweis.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.quellkern.nachweis.ui.theme.NachweisTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The scanner chrome ([ScannerOverlay]) in isolation, without the camera. The overlay carries no
 * camera or analyzer state, so it composes on its own: the test proves the instruction strip and
 * viewfinder render and that Cancel is wired to the [onCancel] seam the scanner already exposes.
 */
class ScannerOverlayInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun overlayShowsInstructionAndViewfinderAndCancelInvokesSeam() {
        var cancelled = false
        composeRule.setContent {
            NachweisTheme {
                ScannerOverlay(onCancel = { cancelled = true })
            }
        }

        composeRule.onNodeWithText("Point the camera at the issuer's QR code").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Scanning viewfinder").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Cancel").performClick()
        assertTrue("Cancel must invoke the onCancel seam", cancelled)
    }
}
