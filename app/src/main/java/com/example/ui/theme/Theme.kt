package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StadiumColorScheme = darkColorScheme(
    primary = TrophyGold,
    onPrimary = PitchBlack,
    primaryContainer = GoldDark,
    onPrimaryContainer = GoldLight,
    secondary = EmeraldNeon,
    onSecondary = PitchBlack,
    secondaryContainer = PitchCardElevated,
    onSecondaryContainer = EmeraldLight,
    tertiary = StadiumCyan,
    onTertiary = PitchBlack,
    background = PitchDark,
    onBackground = TextPrimary,
    surface = PitchSurface,
    onSurface = TextPrimary,
    surfaceVariant = PitchCard,
    onSurfaceVariant = TextSecondary,
    outline = PitchCardBorder,
    error = CardRed,
    onError = Color.White
)

@Composable
fun MalabAlTahaddiTheme(
    darkTheme: Boolean = true, // Football Stadium theme is always dark & atmospheric
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StadiumColorScheme,
        typography = Typography,
        content = content
    )
}
