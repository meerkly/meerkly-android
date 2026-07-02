package com.meerkly.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// One fixed brand scheme: the cream & ink identity is the product (matching
// the desktop app and account portal), so no dynamic color and no dark branch.
private val MeerklyColorScheme = lightColorScheme(
    primary = Pink,
    onPrimary = Cream,
    primaryContainer = Pink,
    onPrimaryContainer = Cream,
    secondary = Gold,
    onSecondary = Cream,
    tertiary = Emerald,
    onTertiary = Cream,
    background = Bone,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = SandSoft,
    onSurfaceVariant = InkSoft,
    outline = Sand,
    error = Rose,
    onError = Cream,
)

@Composable
fun MeerklyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MeerklyColorScheme,
        typography = Typography,
        content = content,
    )
}
