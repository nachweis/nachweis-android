package com.quellkern.nachweis.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quellkern.nachweis.ui.theme.Amber
import com.quellkern.nachweis.ui.theme.Ink
import com.quellkern.nachweis.ui.theme.Over
import com.quellkern.nachweis.ui.theme.Paper
import com.quellkern.nachweis.ui.theme.Teal
import com.quellkern.nachweis.ui.theme.Yellow
import com.quellkern.nachweis.ui.theme.nachweisColors

/**
 * The fixed status vocabulary (design-tokens.md §2). Every status carries a **second signal**
 * besides hue — a distinct glyph shape and a label — so it stays unambiguous under red/green
 * colour blindness or a grayscale render. The glyphs are drawn (not icon-font) so their shapes
 * differ by outline alone: check, warning triangle, failure octagon-x, outside-registration
 * slash-circle, selected filled dot, pending outline dot.
 */
enum class StatusKind(val fill: Color, val label: String) {
    Verified(Teal, "Verified"),
    Untrusted(Amber, "Untrusted verifier"),
    Failed(Over, "Failed"),
    OutsideRegistration(Over, "Outside this verifier's registration"),
    Selected(Yellow, "Selected"),
    Pending(Paper, "Pending"),
}

/**
 * A status chip: status-hue fill, ink text and ink glyph, 4dp ink border, rectangular. The label
 * text carries the meaning for TalkBack; the glyph is decorative there (the word already speaks
 * it). Chromatic hues are only ever fills here, never text on paper (the contrast law).
 */
@Composable
fun StatusChip(
    kind: StatusKind,
    modifier: Modifier = Modifier,
    labelOverride: String? = null,
) {
    val label = labelOverride ?: kind.label
    Row(
        modifier = modifier
            .border(nachweisColors.borderWeight, nachweisColors.borderColor, RectangleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusGlyph(kind, modifier = Modifier.size(16.dp).clearAndSetSemantics { })
        Text(
            text = label,
            color = Ink,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** Draws the shape-distinct glyph for [kind] in ink, sized to the composable's bounds. */
@Composable
fun StatusGlyph(kind: StatusKind, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) { drawStatusGlyph(kind) }
}

private fun DrawScope.drawStatusGlyph(kind: StatusKind) {
    val s = size.minDimension
    val stroke = s * 0.13f
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = s * 0.42f
    when (kind) {
        StatusKind.Verified -> {
            drawCircle(Ink, radius = r, center = c, style = Stroke(width = stroke))
            val p = Path().apply {
                moveTo(c.x - r * 0.45f, c.y)
                lineTo(c.x - r * 0.08f, c.y + r * 0.4f)
                lineTo(c.x + r * 0.5f, c.y - r * 0.4f)
            }
            drawPath(p, Ink, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
        StatusKind.Untrusted -> {
            // Triangle with an exclamation bar + dot.
            val p = Path().apply {
                moveTo(c.x, c.y - r)
                lineTo(c.x + r, c.y + r * 0.85f)
                lineTo(c.x - r, c.y + r * 0.85f)
                close()
            }
            drawPath(p, Ink, style = Stroke(width = stroke))
            drawLine(Ink, Offset(c.x, c.y - r * 0.1f), Offset(c.x, c.y + r * 0.35f), stroke, StrokeCap.Round)
            drawCircle(Ink, radius = stroke * 0.6f, center = Offset(c.x, c.y + r * 0.6f))
        }
        StatusKind.Failed -> {
            // Octagon with an x.
            drawPath(octagon(c, r), Ink, style = Stroke(width = stroke))
            val d = r * 0.4f
            drawLine(Ink, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), stroke, StrokeCap.Round)
            drawLine(Ink, Offset(c.x + d, c.y - d), Offset(c.x - d, c.y + d), stroke, StrokeCap.Round)
        }
        StatusKind.OutsideRegistration -> {
            // Circle with a single diagonal slash (distinct from the failure octagon-x by shape).
            drawCircle(Ink, radius = r, center = c, style = Stroke(width = stroke))
            val d = r * 0.62f
            drawLine(Ink, Offset(c.x - d, c.y + d), Offset(c.x + d, c.y - d), stroke, StrokeCap.Round)
        }
        StatusKind.Selected -> drawCircle(Ink, radius = r * 0.7f, center = c)
        StatusKind.Pending -> drawCircle(Ink, radius = r, center = c, style = Stroke(width = stroke))
    }
}

private fun octagon(c: Offset, r: Float): Path {
    val a = r * 0.41f
    return Path().apply {
        moveTo(c.x - a, c.y - r)
        lineTo(c.x + a, c.y - r)
        lineTo(c.x + r, c.y - a)
        lineTo(c.x + r, c.y + a)
        lineTo(c.x + a, c.y + r)
        lineTo(c.x - a, c.y + r)
        lineTo(c.x - r, c.y + a)
        lineTo(c.x - r, c.y - a)
        close()
    }
}
