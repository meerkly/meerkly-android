package com.meerkly.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meerkly.android.ui.theme.Cream

@Composable
internal fun StrokeIcon(
    // Follows the ambient content colour rather than being hardcoded. These
    // icons started life inside IconChip, where white-on-gradient was always
    // right; reusing them in the navigation bar drew white on a cream surface,
    // so every unselected tab icon was invisible and only the selected one —
    // sitting on the pink indicator — showed up.
    color: Color = LocalContentColor.current,
    size: Dp = 22.dp,
    builder: Path.() -> Unit,
) {
    Canvas(modifier = Modifier.size(size)) {
        // Paths are authored in a 24x24 box.
        scale(this.size.width / 24f, pivot = Offset.Zero) {
            drawPath(
                Path().apply(builder),
                color,
                style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
internal fun CheckIcon() = StrokeIcon {
    moveTo(20f, 6f); lineTo(9f, 17f); lineTo(4f, 12f)
}

@Composable
internal fun ShieldIcon() = StrokeIcon {
    moveTo(12f, 3f); lineTo(19f, 6f); lineTo(19f, 11f)
    cubicTo(19f, 15.4f, 16f, 18.4f, 12f, 19.5f)
    cubicTo(8f, 18.4f, 5f, 15.4f, 5f, 11f)
    lineTo(5f, 6f); close()
    moveTo(9.5f, 12f); lineTo(11.3f, 13.8f); lineTo(14.8f, 10.2f)
}

@Composable
internal fun HeartIcon() = StrokeIcon {
    moveTo(12f, 20f)
    cubicTo(7f, 16.5f, 3.5f, 13f, 2.5f, 10f)
    cubicTo(1.5f, 6.5f, 3.5f, 4.5f, 6f, 4.5f)
    cubicTo(8f, 4.5f, 9.2f, 5.7f, 12f, 6.8f)
    cubicTo(14.8f, 5.7f, 16f, 4.5f, 18f, 4.5f)
    cubicTo(20.5f, 4.5f, 22.5f, 6.5f, 21.5f, 10f)
    cubicTo(20.5f, 13f, 17f, 16.5f, 12f, 20f)
    close()
}

@Composable
internal fun WalletIcon() = StrokeIcon {
    moveTo(3f, 6.5f); lineTo(21f, 6.5f); lineTo(21f, 17f); lineTo(3f, 17f); close()
    moveTo(8f, 20f); lineTo(16f, 20f)
    moveTo(12f, 17f); lineTo(12f, 20f)
}

@Composable
internal fun TrendIcon() = StrokeIcon {
    moveTo(4f, 16f); lineTo(8.5f, 11f); lineTo(12f, 14f); lineTo(20f, 7f)
    moveTo(15f, 7f); lineTo(20f, 7f); lineTo(20f, 12f)
}

// ---- Navigation icons ----------------------------------------------------
// Hand-authored in the same 24x24 box as the icons above rather than pulled
// from material-icons-extended: a set of Material glyphs sitting next to the
// drawn Shield/Heart/Wallet would read as borrowed, and that dependency isn't
// in the project.

/** Home — a simple house. */
@Composable
internal fun HomeIcon() = StrokeIcon {
    moveTo(3f, 11f); lineTo(12f, 3f); lineTo(21f, 11f)
    moveTo(5.5f, 9.5f); lineTo(5.5f, 20f); lineTo(18.5f, 20f); lineTo(18.5f, 9.5f)
}

/** Activity — stacked rows, i.e. a feed. */
@Composable
internal fun ListIcon() = StrokeIcon {
    moveTo(4f, 7f); lineTo(20f, 7f)
    moveTo(4f, 12f); lineTo(20f, 12f)
    moveTo(4f, 17f); lineTo(14f, 17f)
}

/** Devices — a phone outline. */
@Composable
internal fun PhoneIcon() = StrokeIcon {
    moveTo(7f, 3f); lineTo(17f, 3f); lineTo(17f, 21f); lineTo(7f, 21f); close()
    moveTo(10.5f, 18f); lineTo(13.5f, 18f)
}

/** Settings — a slider/tuner rather than a cog, which is fiddly at 22dp. */
@Composable
internal fun SlidersIcon() = StrokeIcon {
    moveTo(4f, 8f); lineTo(20f, 8f)
    moveTo(4f, 16f); lineTo(20f, 16f)
    moveTo(9f, 5.5f); lineTo(9f, 10.5f)
    moveTo(15f, 13.5f); lineTo(15f, 18.5f)
}

/** Clock — timestamps in the activity feed. */
@Composable
internal fun ClockIcon() = StrokeIcon {
    addOval(androidx.compose.ui.geometry.Rect(3f, 3f, 21f, 21f))
    moveTo(12f, 7.5f); lineTo(12f, 12f); lineTo(15.5f, 14f)
}
