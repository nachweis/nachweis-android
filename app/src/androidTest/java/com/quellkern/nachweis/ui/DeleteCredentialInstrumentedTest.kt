package com.quellkern.nachweis.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.quellkern.nachweis.issuance.CredentialClaim
import com.quellkern.nachweis.issuance.CredentialDetail
import com.quellkern.nachweis.issuance.DocumentSummary
import com.quellkern.nachweis.ui.theme.NachweisTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * In-UI deletion from the credential detail view: the confirmation gate (confirm/cancel) on
 * [CredentialDetailScreen], and the navigation outcome (success returns to the list, failure keeps
 * the credential open) driven through [WalletReadyContent] with an injected delete seam.
 */
class DeleteCredentialInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val warning = "This cannot be undone. You would need to get it from the issuer again."
    private val summary = DocumentSummary(id = "doc-1", name = "Personalausweis (PID)", typeLabel = "urn:eudi:pid:de:1")
    private val detail = CredentialDetail(
        id = "doc-1",
        name = "Personalausweis (PID)",
        typeLabel = "urn:eudi:pid:de:1",
        claims = listOf(CredentialClaim("given_name", "Erika")),
    )

    @Test
    fun confirmingTheDialogInvokesDelete() {
        var deleted = false
        composeRule.setContent {
            NachweisTheme { CredentialDetailScreen(detail = detail, onBack = {}, onDelete = { deleted = true }) }
        }

        composeRule.onNodeWithContentDescription("Delete credential").performClick()
        composeRule.onNodeWithText(warning).assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()

        assertTrue("confirming must invoke onDelete", deleted)
    }

    @Test
    fun cancellingTheDialogDoesNotDelete() {
        var deleted = false
        composeRule.setContent {
            NachweisTheme { CredentialDetailScreen(detail = detail, onBack = {}, onDelete = { deleted = true }) }
        }

        composeRule.onNodeWithContentDescription("Delete credential").performClick()
        composeRule.onNodeWithText(warning).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        assertFalse("cancelling must not invoke onDelete", deleted)
        composeRule.onNodeWithText(warning).assertDoesNotExist()
    }

    @Test
    fun successfulDeleteReturnsToTheList() {
        composeRule.setContent {
            NachweisTheme {
                WalletReadyContent(
                    documents = listOf(summary),
                    onScanned = {},
                    loadDetail = { detail },
                    onDelete = { true },
                    scanner = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithContentDescription("double-tap to open", substring = true).performClick()
        composeRule.onNodeWithContentDescription("given_name: Erika").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Delete credential").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        // Back on the list: the detail's claim is gone, the card is shown again.
        composeRule.onNodeWithContentDescription("given_name: Erika").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("double-tap to open", substring = true).assertIsDisplayed()
    }

    @Test
    fun failedDeleteKeepsTheDetailOpen() {
        composeRule.setContent {
            NachweisTheme {
                WalletReadyContent(
                    documents = listOf(summary),
                    onScanned = {},
                    loadDetail = { detail },
                    onDelete = { false },
                    scanner = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithContentDescription("double-tap to open", substring = true).performClick()
        composeRule.onNodeWithContentDescription("Delete credential").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        // The delete failed, so the credential is still shown.
        composeRule.onNodeWithContentDescription("given_name: Erika").assertIsDisplayed()
    }
}
