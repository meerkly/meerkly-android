package com.meerkly.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.meerkly.android.R

/**
 * Fraunces — the brand's display face, the same one account.meerkly.com uses as
 * `--font-display`. Before this the app used [FontFamily.Serif], which Android
 * resolves to Noto Serif: perfectly competent, and nothing like the website.
 *
 * The bundled file is the *variable* font, so `opsz` (optical size) can be
 * pinned to the display end of the axis. That axis is most of Fraunces'
 * character — at low `opsz` it's a quiet text serif, at high `opsz` it gets the
 * high-contrast, slightly wonky personality the brand is after. Variable fonts
 * need API 26, which is exactly this app's minSdk.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun frauncesDisplay(weight: Int) = Font(
    R.font.fraunces,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.Setting("opsz", 72f),
        FontVariation.Setting("wght", weight.toFloat()),
        // A little softness takes the edge off the terminals at display sizes;
        // WONK left at 0 — the alternate glyphs are charming in a logo and
        // distracting in a heading you read every day.
        FontVariation.Setting("SOFT", 20f),
    ),
)

val Display = FontFamily(
    frauncesDisplay(600),
    frauncesDisplay(700),
)

/**
 * One place that decides what "a heading" looks like.
 *
 * Only the display styles take Fraunces; body and label stay on the system face,
 * which is more legible at small sizes and carries no brand weight anyway.
 * Tighter letter-spacing on the big styles because Fraunces sets wide by
 * default at display optical sizes.
 */
private val TightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val Typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
        lineHeightStyle = TightLineHeight,
    ),
    headlineMedium = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
        lineHeightStyle = TightLineHeight,
    ),
    headlineSmall = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.3).sp,
        lineHeightStyle = TightLineHeight,
    ),
    titleLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp,
    ),
)
