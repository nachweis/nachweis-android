package com.quellkern.nachweis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quellkern.nachweis.ui.theme.Ink

/**
 * A full-width status banner: status-hue fill, ink text and glyph, 4dp ink border (design-tokens
 * §6.3 verdict banner). Ink text on the hue fill honours the contrast law. The whole banner is a
 * polite live region so TalkBack speaks the verdict when it appears; the glyph is decorative
 * because the message carries the meaning. This is the D1 consent surface's trust verdict —
 * deliberately sober, no press choreography.
 */
@Composable
fun StatusBanner(
    kind: StatusKind,
    message: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(kind.fill, RectangleShape)
            .border(4.dp, Ink, RectangleShape)
            .padding(12.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = message
            },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusGlyph(kind, modifier = Modifier.size(20.dp).clearAndSetSemantics { })
        Text(
            text = message,
            color = Ink,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}
