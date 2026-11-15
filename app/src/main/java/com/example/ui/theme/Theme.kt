package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AegisDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = CyberCyanContainer,
    onPrimaryContainer = CyberCyan,
    secondary = CyberIndigo,
    onSecondary = Color.White,
    secondaryContainer = CyberIndigoContainer,
    onSecondaryContainer = Color(0xFFD8D2FF),
    tertiary = ShieldEmerald,
    onTertiary = ObsidianVoid,
    tertiaryContainer = ShieldEmeraldContainer,
    onTertiaryContainer = ShieldEmerald,
    background = ObsidianVoid,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceBright,
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF1B2740),
    error = AlertCrimson,
    onError = Color.White,
    errorContainer = AlertCrimsonContainer,
    onErrorContainer = Color(0xFFFFB4AB)
)

private val AegisLightColorScheme = lightColorScheme(
    primary = GlacierCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0F4FF),
    onPrimaryContainer = Color(0xFF003547),
    secondary = GlacierIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E2FF),
    onSecondaryContainer = Color(0xFF1F187A),
    tertiary = Color(0xFF00875A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3F8E2),
    onTertiaryContainer = Color(0xFF003823),
    background = GlacierBackground,
    onBackground = TextPrimaryLight,
    surface = GlacierSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = GlacierSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = GlacierSurfaceVariant,
    surfaceContainerHigh = Color(0xFFDCE6F4),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFD32F2F),
    onError = Color.White
)

val AegisShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AegisXTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AegisDarkColorScheme else AegisLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AegisTypography,
        shapes = AegisShapes,
        content = content
    )
}
