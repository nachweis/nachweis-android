package com.quellkern.nachweis.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.quellkern.nachweis.ui.theme.OverDeep
import com.quellkern.nachweis.ui.theme.MonoTextStyle

/**
 * Credential card (design-tokens.md §6.1): a paper face with a 4dp ink border and the hard
 * shadow, holding the credential type, issuer, a status chip, and an optional mono claim summary.
 * A single focusable node announcing "{type}, {issuer}, {status}". [onClick] is optional so the
 * card also serves as a static summary tile.
 */
@Composable
fun CredentialCard(
    title: String,
    issuer: String,
    status: StatusKind,
    modifier: Modifier = Modifier,
    monoSummary: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val description = buildString {
        append(title); append(", "); append(issuer); append(", "); append(status.label)
        if (onClick != null) append(", double-tap to open")
    }
    val body: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = issuer,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 2.dp),
            )
            StatusChip(kind = status, modifier = Modifier.padding(top = 12.dp))
            if (monoSummary != null) {
                Text(
                    text = monoSummary,
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
    if (onClick != null) {
        NeoClickableSurface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = description
            },
            content = body,
        )
    } else {
        NeoSurface(
            modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = description
            },
            content = body,
        )
    }
}

/**
 * Empty state (design-tokens.md §6.7): a decorative glyph, an inviting title and one-line body,
 * and a single primary action. An empty screen is an invitation to act, not an apology.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusGlyph(StatusKind.Pending, modifier = Modifier.size(40.dp).clearAndSetSemantics { })
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        PrimaryButton(
            label = actionLabel,
            onClick = onAction,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

/**
 * Error state (design-tokens.md §6.8): a failure octagon-x glyph, a title, the reason in the
 * text-safe deep red, and a retry action. Errors say what happened and how to fix it, in the
 * interface's voice — never a vague apology.
 */
@Composable
fun ErrorState(
    title: String,
    reason: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusGlyph(StatusKind.Failed, modifier = Modifier.size(40.dp).clearAndSetSemantics { })
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = reason,
            color = OverDeep,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        PrimaryButton(
            label = actionLabel,
            onClick = onAction,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}
