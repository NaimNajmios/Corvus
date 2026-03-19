package com.najmi.corvus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

private val LightColorScheme = lightColorScheme(
    primary = CorvusAccent,
    onPrimary = CorvusVoid,
    primaryContainer = CorvusAccentDim,
    onPrimaryContainer = CorvusTextPrimaryLight,
    secondary = CorvusTextSecondaryLight,
    onSecondary = CorvusVoidLight,
    secondaryContainer = CorvusSurfaceRaisedLight,
    onSecondaryContainer = CorvusTextPrimaryLight,
    tertiary = CorvusAccentDim,
    onTertiary = CorvusVoidLight,
    background = CorvusVoidLight,
    onBackground = CorvusTextPrimaryLight,
    surface = CorvusSurfaceLight,
    onSurface = CorvusTextPrimaryLight,
    surfaceVariant = CorvusSurfaceRaisedLight,
    onSurfaceVariant = CorvusTextSecondaryLight,
    outline = CorvusBorderLight,
    outlineVariant = CorvusBorderLight,
    error = VerdictFalse,
    onError = CorvusVoidLight
)

@Composable
fun CorvusTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = CorvusShapes,
        content = content
    )
}
