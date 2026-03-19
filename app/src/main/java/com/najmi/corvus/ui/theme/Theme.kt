package com.najmi.corvus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CorvusAccent,
    onPrimary = CorvusVoid,
    primaryContainer = CorvusAccentDim,
    onPrimaryContainer = CorvusTextPrimary,
    secondary = CorvusTextSecondary,
    onSecondary = CorvusVoid,
    secondaryContainer = CorvusSurfaceRaised,
    onSecondaryContainer = CorvusTextPrimary,
    tertiary = CorvusAccentDim,
    onTertiary = CorvusVoid,
    background = CorvusVoid,
    onBackground = CorvusTextPrimary,
    surface = CorvusSurface,
    onSurface = CorvusTextPrimary,
    surfaceVariant = CorvusSurfaceRaised,
    onSurfaceVariant = CorvusTextSecondary,
    outline = CorvusBorder,
    outlineVariant = CorvusBorder,
    error = VerdictFalse,
    onError = CorvusVoid
)

@Composable
fun CorvusTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        shapes = CorvusShapes,
        content = content
    )
}
