package com.quellkern.nachweis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quellkern.nachweis.issuance.DocumentSummary

/**
 * The wallet's credential list, or an inviting empty state when there are none. Restrained
 * by design — the distinctive visual system is C-late; this satisfies the state contract
 * (list item, empty state) with accessible defaults: 48dp targets, per-row TalkBack labels,
 * text that scales.
 */
@Composable
fun DocumentListScreen(
    documents: List<DocumentSummary>,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Your credentials",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (documents.isEmpty()) {
            EmptyDocuments(onScanClick = onScanClick, modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(documents, key = { it.id }) { document -> DocumentRow(document) }
            }
            Button(
                onClick = onScanClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(top = 12.dp),
            ) { Text("Scan QR to add another") }
        }
    }
}

@Composable
private fun DocumentRow(document: DocumentSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 12.dp)
            .semantics {
                contentDescription = "${document.name}, ${document.typeLabel}"
            },
    ) {
        Text(text = document.name, style = MaterialTheme.typography.titleMedium)
        Text(
            text = document.typeLabel,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    HorizontalDivider()
}

@Composable
private fun EmptyDocuments(onScanClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No credentials yet",
            style = MaterialTheme.typography.titleLarge,
            // Decorative heading text is announced with the body below as one thought.
            modifier = Modifier.semantics { },
        )
        Text(
            text = "Scan an issuer's QR code to add your first one.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = onScanClick,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(top = 16.dp)
                .semantics { contentDescription = "Scan QR to add a credential" },
        ) { Text("Scan QR", modifier = Modifier.clearAndSetSemantics { }) }
    }
}
