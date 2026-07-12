package com.quellkern.nachweis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quellkern.nachweis.issuance.CredentialClaim
import com.quellkern.nachweis.issuance.CredentialDetail
import com.quellkern.nachweis.ui.components.NeoSurface
import com.quellkern.nachweis.ui.components.SecondaryButton
import com.quellkern.nachweis.ui.theme.MonoTextStyle

/**
 * A stored credential's full claim set, shown after an explicit tap from the list. Stateless: it
 * renders the [detail] it is handed and calls [onBack] for navigation. Display-only by design —
 * viewing user-owned data needs no fresh device authentication (key operations remain auth-gated);
 * the values live only in this composition and never re-enter the list model or any log.
 *
 * Each claim is a label-over-mono-value row inside the neo-brutal card surface; every row is a
 * single merged semantics node so TalkBack speaks "{path}: {value}" once.
 */
@Composable
fun CredentialDetailScreen(
    detail: CredentialDetail,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SecondaryButton(label = "Back", onClick = onBack)

        Text(
            text = detail.name,
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = detail.typeLabel,
            style = MonoTextStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        if (detail.claims.isEmpty()) {
            Text(
                text = "This credential has no readable claims.",
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            NeoSurface(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    detail.claims.forEach { claim -> ClaimRow(claim) }
                }
            }
        }
    }
}

@Composable
private fun ClaimRow(claim: CredentialClaim) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "${claim.path}: ${claim.value}"
            },
    ) {
        Text(
            text = claim.path,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = claim.value,
            style = MonoTextStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
