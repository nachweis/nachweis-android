package com.quellkern.nachweis.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.quellkern.nachweis.issuance.CredentialClaim
import com.quellkern.nachweis.issuance.CredentialDetail
import com.quellkern.nachweis.issuance.DocumentSummary
import com.quellkern.nachweis.ui.theme.NachweisTheme
import org.junit.Rule
import org.junit.Test

/**
 * Local navigation into and out of the credential detail view, mirroring
 * [ScannerBackNavigationInstrumentedTest]. Tapping a card opens the detail; system Back returns to
 * the list rather than propagating to the activity (which, as the app's only root, would exit to
 * the launcher). The detail is fed from an injected [loadDetail] fixture, so no live wallet is
 * needed.
 */
class CredentialDetailNavigationInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val summary = DocumentSummary(id = "doc-1", name = "Personalausweis (PID)", typeLabel = "urn:eudi:pid:de:1")
    private val detail = CredentialDetail(
        id = "doc-1",
        name = "Personalausweis (PID)",
        typeLabel = "urn:eudi:pid:de:1",
        claims = listOf(CredentialClaim("given_name", "Erika")),
    )

    @Test
    fun tappingCardOpensDetailAndBackReturnsToList() {
        composeRule.setContent {
            NachweisTheme {
                WalletReadyContent(
                    documents = listOf(summary),
                    onScanned = {},
                    loadDetail = { id -> if (id == "doc-1") detail else null },
                    scanner = { _, _, _ -> },
                )
            }
        }

        // The list shows the tappable card; tapping it opens the detail (claim value visible).
        composeRule.onNodeWithContentDescription("double-tap to open", substring = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("given_name: Erika").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("double-tap to open", substring = true).performClick()
        composeRule.onNodeWithContentDescription("given_name: Erika").assertIsDisplayed()

        // System Back returns to the list, not to the launcher.
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithContentDescription("given_name: Erika").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("double-tap to open", substring = true).assertIsDisplayed()
    }
}
