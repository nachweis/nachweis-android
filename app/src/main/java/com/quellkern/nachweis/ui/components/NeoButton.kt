package com.quellkern.nachweis.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quellkern.nachweis.ui.theme.Grey
import com.quellkern.nachweis.ui.theme.Ink
import com.quellkern.nachweis.ui.theme.nachweisColors

/**
 * Primary action (design-tokens.md §6.4): blue fill, paper label (5.42:1), 4dp ink border,
 * neo-brutal press-offset, ≥48dp target. Disabled shows a grey fill with ink label (6.54:1) and
 * is announced as dimmed. The label is described via [label]; TalkBack speaks the action verb.
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val fill = if (enabled) MaterialTheme.colorScheme.primary else Grey
    val content = if (enabled) MaterialTheme.colorScheme.onPrimary else Ink
    NeoActionButton(label, onClick, modifier, enabled, fill, content)
}

/**
 * Secondary action (design-tokens.md §6.5): paper fill, ink label, 4dp ink border outline style.
 */
@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val fill = MaterialTheme.colorScheme.surface
    val content = if (enabled) MaterialTheme.colorScheme.onSurface else Grey
    NeoActionButton(label, onClick, modifier, enabled, fill, content)
}

@Composable
private fun NeoActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    fill: Color,
    content: Color,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    NeoSurface(
        modifier = modifier
            .selectable(
                selected = false,
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = label
                if (!enabled) disabled()
            },
        fill = fill,
        pressed = pressed && enabled,
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.labelLarge,
            ) {
                Text(
                    text = label,
                    color = content,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}
