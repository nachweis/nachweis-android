package com.quellkern.nachweis.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quellkern.nachweis.ui.components.CredentialCard
import com.quellkern.nachweis.ui.components.EmptyState
import com.quellkern.nachweis.ui.components.ErrorState
import com.quellkern.nachweis.ui.components.PrimaryButton
import com.quellkern.nachweis.ui.components.SecondaryButton
import com.quellkern.nachweis.ui.components.StatusBanner
import com.quellkern.nachweis.ui.components.StatusChip
import com.quellkern.nachweis.ui.components.StatusKind
import com.quellkern.nachweis.ui.theme.MonoTextStyle
import com.quellkern.nachweis.ui.theme.NachweisTheme

/**
 * Debug-only gallery of the neo-brutal design system on real device rendering. It exists purely
 * to capture screenshot evidence of the C-late surfaces — chiefly the D1 consent verdict banner,
 * which has no live-verifier path yet — and never ships (it lives in the `debug` source set).
 * Launch with:
 * `adb shell am start -n com.quellkern.nachweis.demo/com.quellkern.nachweis.debug.PreviewGalleryActivity`
 */
class PreviewGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NachweisTheme { Gallery() } }
    }
}

@Composable
private fun Gallery() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("nachweis", style = MaterialTheme.typography.displaySmall)

            SectionLabel("Consent verdict — D1 flagship")
            StatusBanner(
                kind = StatusKind.OutsideRegistration,
                message = "Stadtwerke Demo — outside this verifier's registration. " +
                    "Outside its registration: \$.address.street_address, \$.birthdate",
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("This verifier is asking for:", style = MaterialTheme.typography.titleSmall)
                listOf("\$.family_name", "\$.given_name", "\$.address.street_address", "\$.birthdate")
                    .forEach { Text(it, style = MonoTextStyle, modifier = Modifier.fillMaxWidth()) }
            }

            SectionLabel("Verdict — within registration")
            StatusBanner(
                kind = StatusKind.Verified,
                message = "Bürgeramt Demo — within this verifier's registration",
            )

            SectionLabel("Status chips")
            StatusChip(StatusKind.Verified)
            StatusChip(StatusKind.Untrusted)
            StatusChip(StatusKind.Failed)
            StatusChip(StatusKind.OutsideRegistration)
            StatusChip(StatusKind.Selected)
            StatusChip(StatusKind.Pending)

            SectionLabel("Credential card")
            CredentialCard(
                title = "Personalausweis (PID)",
                issuer = "Bundesdruckerei Demo",
                status = StatusKind.Verified,
                monoSummary = "urn:eudi:pid:de:1",
            )

            SectionLabel("Buttons")
            PrimaryButton(label = "Share", onClick = {})
            SecondaryButton(label = "Decline", onClick = {})
            PrimaryButton(label = "Unavailable", onClick = {}, enabled = false)

            SectionLabel("Empty state")
            EmptyState(
                title = "No credentials yet",
                body = "Scan an issuer's QR code to add your first one.",
                actionLabel = "Scan QR",
                onAction = {},
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Error state")
            ErrorState(
                title = "Couldn't reach the issuer",
                reason = "Check your connection and try again.",
                actionLabel = "Retry",
                onAction = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge)
}
