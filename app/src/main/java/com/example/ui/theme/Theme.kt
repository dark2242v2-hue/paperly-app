package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

private val PaperlyDarkColorScheme = darkColorScheme(
    primary = PaperlyDarkIndigo,
    onPrimary = Color.White,
    primaryContainer = PaperlyDarkIndigoTint,
    onPrimaryContainer = PaperlyDarkOnIndigoTint,
    secondary = PaperlySky,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = PaperlyEmerald,
    onTertiary = Color.White,
    background = PaperlyDarkBackground,
    onBackground = PaperlyDarkText,
    surface = PaperlyDarkSurface,
    onSurface = PaperlyDarkText,
    surfaceVariant = PaperlyDarkSurfaceVariant,
    onSurfaceVariant = PaperlyDarkMuted,
    outline = PaperlyDarkOutline,
    outlineVariant = Color(0xFF1E293B),
    error = PaperlyRose,
    onError = Color.White
)

private val PaperlyLightColorScheme = lightColorScheme(
    primary = PaperlyIndigo,
    onPrimary = Color.White,
    primaryContainer = PaperlyIndigoTint,
    onPrimaryContainer = PaperlyOnIndigoTint,
    secondary = PaperlySky,
    onSecondary = Color.White,
    secondaryContainer = PaperlySkySoft,
    onSecondaryContainer = Color(0xFF075985),
    tertiary = PaperlyEmerald,
    onTertiary = Color.White,
    background = PaperlyLightBackground,
    onBackground = PaperlySlateInk,
    surface = PaperlyLightSurface,
    onSurface = PaperlySlateInk,
    surfaceVariant = PaperlyLightSurfaceVariant,
    onSurfaceVariant = PaperlySlateMuted,
    outline = PaperlyLightOutline,
    outlineVariant = Color(0xFFF1F5F9),
    error = PaperlyRose,
    onError = Color.White
)

val PaperlyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PaperlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PaperlyDarkColorScheme else PaperlyLightColorScheme
    val currentDensity = LocalDensity.current
    val clampedDensity = remember(currentDensity.density, currentDensity.fontScale) {
        Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale.coerceIn(0.85f, 1.05f)
        )
    }

    CompositionLocalProvider(LocalDensity provides clampedDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PaperlyTypography,
            shapes = PaperlyShapes,
            content = content
        )
    }
}
