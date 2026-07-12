package com.quellkern.nachweis.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quellkern.nachweis.issuance.DocumentSummary
import com.quellkern.nachweis.ui.components.CredentialCard
import com.quellkern.nachweis.ui.components.EmptyState
import com.quellkern.nachweis.ui.components.PrimaryButton
import com.quellkern.nachweis.ui.components.StatusKind

/**
 * The wallet's credential list, or an inviting empty state when there are none. Now carries the
 * neo-brutal visual system (C-late): credential cards with the hard border/shadow, a drawn empty
 * state, and the primary action button. Accessibility holds: 48dp targets, per-card TalkBack
 * labels, text that scales (design-tokens.md §6.1/§6.7).
 */
@Composable
fun DocumentListScreen(
    documents: List<DocumentSummary>,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDocumentClick: (String) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Your credentials",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        if (documents.isEmpty()) {
            EmptyState(
                title = "No credentials yet",
                body = "Scan an issuer's QR code to add your first one.",
                actionLabel = "Scan QR",
                onAction = onScanClick,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(documents, key = { it.id }) { document ->
                    CredentialCard(
                        title = document.name,
                        issuer = document.typeLabel,
                        status = StatusKind.Verified,
                        modifier = Modifier.padding(bottom = 12.dp),
                        onClick = { onDocumentClick(document.id) },
                    )
                }
            }
            PrimaryButton(
                label = "Scan QR to add another",
                onClick = onScanClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }
    }
}
