package com.quellkern.nachweis.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quellkern.nachweis.ui.theme.LocalReducedMotion
import com.quellkern.nachweis.ui.theme.Motion
import com.quellkern.nachweis.ui.theme.nachweisColors

/**
 * The neo-brutal building block (design-system.md): a rectangular filled face with a 4dp ink
 * border sitting above a hard, blur-free offset shadow of the same ink. Separation is structural,
 * not tonal. When [pressed] the face slides into the shadow (the classic neo-brutal press);
 * under reduced motion the slide is instant (design-tokens.md §4.4).
 *
 * Space for the shadow is reserved with outer padding so the offset rect is never clipped, which
 * keeps the element's outer bounds honest for layout and touch-target math.
 */
@Composable
fun NeoSurface(
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = nachweisColors.borderColor,
    borderWidth: Dp = nachweisColors.borderWeight,
    shadowOffset: Dp = nachweisColors.shadowOffset,
    pressed: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val target = if (pressed) shadowOffset else 0.dp
    val slide by animateDpAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (reduced) 0 else Motion.FAST),
        label = "neo-press",
    )
    Box(modifier = modifier.padding(end = shadowOffset, bottom = shadowOffset)) {
        // Hard shadow: an ink rect at the offset, behind the face.
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .background(borderColor, RectangleShape),
        )
        // The face; borders and fill on top, sliding toward the shadow on press.
        Box(
            modifier = Modifier
                .offset(x = slide, y = slide)
                .background(fill, RectangleShape)
                .border(borderWidth, borderColor, RectangleShape),
            content = content,
        )
    }
}

/**
 * A [NeoSurface] that is a single selectable/clickable node with the press choreography wired to
 * its own interaction source. Used by cards and list rows. [role] and [contentDescription] carry
 * the TalkBack contract; the ripple is suppressed because the press-offset is the feedback.
 */
@Composable
fun NeoClickableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.surface,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    NeoSurface(
        modifier = modifier.selectable(
            selected = false,
            enabled = enabled,
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        ),
        fill = fill,
        pressed = pressed && enabled,
        content = content,
    )
}
