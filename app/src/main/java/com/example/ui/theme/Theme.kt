package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val StreamDarkColorScheme = darkColorScheme(
    primary = NeonMint,
    onPrimary = ObsidianVoid,
    primaryContainer = Color(0xFF0A3A2B),
    onPrimaryContainer = NeonMint,
    secondary = ElectricViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2B1648),
    onSecondaryContainer = Color(0xFFE9D5FF),
    tertiary = CyberCyan,
    onTertiary = ObsidianVoid,
    background = ObsidianVoid,
    onBackground = DarkTextPrimary,
    surface = ObsidianSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainer = ObsidianCard,
    surfaceContainerHigh = ObsidianElevated,
    outline = ObsidianOutline,
    error = RoseCoral,
    onError = ObsidianVoid
)

private val StreamLightColorScheme = lightColorScheme(
    primary = DeepMint,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = DeepViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = Color(0xFF3B0764),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    background = PorcelainBackground,
    onBackground = LightTextPrimary,
    surface = PorcelainSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = PorcelainCard,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainer = PorcelainCard,
    surfaceContainerHigh = PorcelainElevated,
    outline = PorcelainOutline,
    error = Color(0xFFE11D48),
    onError = Color.White
)

private val StreamShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun StreamMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StreamDarkColorScheme else StreamLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = StreamTypography,
        shapes = StreamShapes,
        content = content
    )
}
