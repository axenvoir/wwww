package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MatchdayDarkColorScheme = darkColorScheme(
    primary = PitchEmerald,
    onPrimary = StadiumSlate,
    primaryContainer = PitchEmeraldDark,
    onPrimaryContainer = TextPrimary,
    secondary = ChampionshipGold,
    onSecondary = StadiumSlate,
    tertiary = TacticalCyan,
    onTertiary = StadiumSlate,
    background = StadiumSlate,
    onBackground = TextPrimary,
    surface = TunnelNavy,
    onSurface = TextPrimary,
    surfaceVariant = CardSurface,
    onSurfaceVariant = TextSecondary,
    error = CrimsonAlert,
    onError = TextPrimary,
    outline = GlassBorder
)

@Composable
fun MatchdayLanTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MatchdayDarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
