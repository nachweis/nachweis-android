package com.quellkern.nachweis.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.quellkern.nachweis.ui.components.PrimaryButton
import com.quellkern.nachweis.ui.components.StatusBanner
import com.quellkern.nachweis.ui.components.StatusKind
import com.quellkern.nachweis.ui.theme.NachweisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Renders the C-late design system on-device: the theme composes (bundled fonts resolve), the D1
 * verdict banner shows the normative wording and never "over-ask", and the primary button keeps a
 * 48dp target and fires its click. Both primitives expose their label through `contentDescription`
 * (merged semantics), so the assertions match on that. This is the theme-swap regression guard.
 */
class DesignSystemInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun outsideRegistrationBannerShowsNormativeWording() {
        composeRule.setContent {
            NachweisTheme {
                StatusBanner(
                    kind = StatusKind.OutsideRegistration,
                    message = "Demo verifier — outside this verifier's registration",
                )
            }
        }
        composeRule.onNodeWithContentDescription(
            "outside this verifier's registration",
            substring = true,
            ignoreCase = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("over-ask", substring = true, ignoreCase = true)
            .assertDoesNotExist()
    }

    @Test
    fun primaryButtonHasAccessibleTargetAndClicks() {
        var clicks = 0
        composeRule.setContent {
            NachweisTheme {
                Column {
                    PrimaryButton(label = "Share", onClick = { clicks++ })
                }
            }
        }
        composeRule.onNodeWithContentDescription("Share").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Share").performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }
}
