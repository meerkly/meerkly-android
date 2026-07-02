package com.meerkly.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

/**
 * Meerkly the meerkat + the brand coin, ported from the desktop renderer's SVG
 * (200x240 viewBox) as Compose drawings so gradients and shapes stay exact
 * without VectorDrawable conversion.
 */

private val FurLight = Color(0xFFDCAB68)
private val FurDark = Color(0xFFBD8746)
private val FurShade = Color(0xFFA9743A)
private val BellyCream = Color(0xFFF6E7CD)
private val DarkPatch = Color(0xFF3A2417)
private val CoinLight = Color(0xFFFFE6A3)
private val CoinMid = Color(0xFFFFB43D)
private val CoinDeep = Color(0xFFF59000)
private val CoinEdge = Color(0xFFD97E00)
private val CoinInk = Color(0xFF8A4A00)
private val EarPink = Color(0xFFFFB3D4)
private val CheekPink = Color(0xFFFF8FBF)

@Composable
fun MeerklyMascot(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        // Scale from the 200x240 SVG space to the canvas.
        val s = size.width / 200f
        scale(s, pivot = Offset.Zero) {
            drawMascot()
        }
    }
}

private fun DrawScope.drawMascot() {
    val fur = Brush.verticalGradient(0f to FurLight, 1f to FurDark, startY = 0f, endY = 240f)

    // Ground shadow
    drawOval(DarkPatch.copy(alpha = 0.12f), topLeft = Offset(54f, 224f), size = Size(92f, 14f))

    // Tail (thick curved stroke + tip)
    val tail = Path().apply {
        moveTo(136f, 180f)
        cubicTo(158f, 172f, 168f, 152f, 167f, 128f)
    }
    drawPath(tail, fur, style = Stroke(width = 13f, cap = StrokeCap.Round))
    drawOval(FurShade, topLeft = Offset(160f, 116f), size = Size(14f, 16f))

    // Body
    val body = Path().apply {
        moveTo(100f, 86f)
        cubicTo(70f, 86f, 60f, 116f, 60f, 150f)
        cubicTo(60f, 192f, 72f, 216f, 100f, 216f)
        cubicTo(128f, 216f, 140f, 192f, 140f, 150f)
        cubicTo(140f, 116f, 130f, 86f, 100f, 86f)
        close()
    }
    drawPath(body, fur)
    drawOval(BellyCream, topLeft = Offset(67f, 112f), size = Size(66f, 92f)) // belly
    drawOval(FurShade, topLeft = Offset(71f, 206f), size = Size(26f, 16f)) // feet
    drawOval(FurShade, topLeft = Offset(103f, 206f), size = Size(26f, 16f))

    // Left arm reaching to the coin
    val leftArm = Path().apply {
        moveTo(72f, 114f)
        cubicTo(62f, 132f, 64f, 154f, 82f, 166f)
    }
    drawPath(leftArm, fur, style = Stroke(width = 13f, cap = StrokeCap.Round))

    // Coin (radial gradient + ring + dollar)
    val coinCenter = Offset(100f, 172f)
    drawCircle(
        Brush.radialGradient(
            0f to CoinLight, 0.55f to CoinMid, 1f to CoinDeep,
            center = Offset(93f, 166f), radius = 32f,
        ),
        radius = 20f, center = coinCenter,
    )
    drawCircle(CoinEdge, radius = 20f, center = coinCenter, style = Stroke(2f))
    drawCircle(Color.White.copy(alpha = 0.5f), radius = 14.5f, center = coinCenter, style = Stroke(1.5f))
    drawDollar(center = coinCenter, halfHeight = 11f, color = CoinInk, stroke = 3f)
    drawCircle(FurShade, radius = 8f, center = Offset(83f, 170f)) // paw on the coin

    // Ears
    drawOval(fur, topLeft = Offset(59f, 18f), size = Size(26f, 24f))
    drawOval(EarPink, topLeft = Offset(65.5f, 25f), size = Size(13f, 12f))
    drawOval(fur, topLeft = Offset(115f, 18f), size = Size(26f, 24f))
    drawOval(EarPink, topLeft = Offset(121.5f, 25f), size = Size(13f, 12f))

    // Head
    drawOval(fur, topLeft = Offset(60f, 14f), size = Size(80f, 84f))

    // Eye patches (rotated ovals)
    rotate(-18f, pivot = Offset(84f, 54f)) {
        drawOval(DarkPatch, topLeft = Offset(70f, 37f), size = Size(28f, 34f))
    }
    rotate(18f, pivot = Offset(116f, 54f)) {
        drawOval(DarkPatch, topLeft = Offset(102f, 37f), size = Size(28f, 34f))
    }

    // Cheeks + muzzle
    drawOval(CheekPink.copy(alpha = 0.55f), topLeft = Offset(67.5f, 67.5f), size = Size(13f, 9f))
    drawOval(CheekPink.copy(alpha = 0.55f), topLeft = Offset(119.5f, 67.5f), size = Size(13f, 9f))
    drawOval(BellyCream, topLeft = Offset(85f, 66f), size = Size(30f, 24f))

    // Eyes
    drawCircle(Color.White, 8.5f, Offset(85f, 56f))
    drawCircle(Color(0xFF2A1A10), 5.8f, Offset(86f, 57f))
    drawCircle(Color.White, 2.3f, Offset(88f, 54f))
    drawCircle(Color.White, 8.5f, Offset(115f, 56f))
    drawCircle(Color(0xFF2A1A10), 5.8f, Offset(114f, 57f))
    drawCircle(Color.White, 2.3f, Offset(117f, 54f))

    // Nose + mouth
    val nose = Path().apply {
        moveTo(94f, 70f)
        quadraticTo(100f, 67f, 106f, 70f)
        quadraticTo(103f, 76.5f, 100f, 76.5f)
        quadraticTo(97f, 76.5f, 94f, 70f)
        close()
    }
    drawPath(nose, DarkPatch)
    val mouth = Path().apply {
        moveTo(100f, 76.5f); lineTo(100f, 79f)
        moveTo(100f, 79f); cubicTo(99f, 83f, 95f, 84f, 91f, 82f)
        moveTo(100f, 79f); cubicTo(101f, 83f, 105f, 84f, 109f, 82f)
    }
    drawPath(mouth, DarkPatch, style = Stroke(2f, cap = StrokeCap.Round))

    // Waving arm
    val wave = Path().apply {
        moveTo(132f, 114f)
        cubicTo(145f, 104f, 153f, 90f, 156f, 80f)
    }
    drawPath(wave, fur, style = Stroke(13f, cap = StrokeCap.Round))
    drawCircle(FurShade, 8.5f, Offset(157f, 78f))

    // Twinkles
    drawTwinkle(Offset(170f, 69f), 9f, Color(0xFFFFD57F))
    drawTwinkle(Offset(120f, 156.5f), 6.5f, CheekPink)
}

/** Simple stroked "$": vertical bar + S-curve, matching the brand coin mark. */
private fun DrawScope.drawDollar(center: Offset, halfHeight: Float, color: Color, stroke: Float) {
    val bar = Path().apply {
        moveTo(center.x, center.y - halfHeight)
        lineTo(center.x, center.y + halfHeight)
    }
    val sCurve = Path().apply {
        moveTo(center.x + 4.6f, center.y - 6.7f)
        cubicTo(
            center.x + 3.5f, center.y - 8.1f,
            center.x + 1.7f, center.y - 8.8f,
            center.x - 0.2f, center.y - 8.8f,
        )
        cubicTo(
            center.x - 2.8f, center.y - 8.8f,
            center.x - 4.6f, center.y - 7.4f,
            center.x - 4.6f, center.y - 5.3f,
        )
        cubicTo(center.x - 4.6f, center.y - 0.5f, center.x + 4.8f, center.y - 2.8f, center.x + 4.8f, center.y + 2f)
        cubicTo(center.x + 4.8f, center.y + 4.2f, center.x + 2.8f, center.y + 5.7f, center.x - 0.2f, center.y + 5.7f)
        cubicTo(center.x - 2.3f, center.y + 5.7f, center.x - 4.2f, center.y + 4.9f, center.x - 5.3f, center.y + 3.4f)
    }
    val style = Stroke(stroke, cap = StrokeCap.Round)
    drawPath(bar, color, style = style)
    drawPath(sCurve, color, style = style)
}

private fun DrawScope.drawTwinkle(center: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(center.x, center.y - r)
        lineTo(center.x + r * 0.22f, center.y - r * 0.22f)
        lineTo(center.x + r, center.y)
        lineTo(center.x + r * 0.22f, center.y + r * 0.22f)
        lineTo(center.x, center.y + r)
        lineTo(center.x - r * 0.22f, center.y + r * 0.22f)
        lineTo(center.x - r, center.y)
        lineTo(center.x - r * 0.22f, center.y - r * 0.22f)
        close()
    }
    drawPath(p, color)
}

/** The gold brand coin with the "$" mark (topbar / wordmark companion). */
@Composable
fun BrandCoin(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            Brush.radialGradient(
                0f to CoinLight, 0.55f to CoinMid, 1f to CoinDeep,
                center = Offset(c.x - r * 0.3f, c.y - r * 0.4f), radius = r * 1.7f,
            ),
            radius = r * 0.92f, center = c,
        )
        drawCircle(CoinEdge, radius = r * 0.92f, center = c, style = Stroke(r * 0.07f))
        drawCircle(Color.White.copy(alpha = 0.55f), radius = r * 0.66f, center = c, style = Stroke(r * 0.06f))
        translate(0f, 0f) {
            scale(r / 24f, pivot = c) {
                drawDollar(center = c, halfHeight = 12f, color = CoinInk, stroke = 2.4f)
            }
        }
    }
}
