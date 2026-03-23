package com.najmi.corvus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class SectionColors(
    val sectionEvidence: Color,
    val sectionFacts: Color,
    val sectionMethodology: Color,
    val sectionTimeline: Color
)

private val DarkSectionColors = SectionColors(
    sectionEvidence = SectionEvidence,
    sectionFacts = SectionFacts,
    sectionMethodology = SectionMethodology,
    sectionTimeline = SectionTimeline
)

private val LightSectionColors = SectionColors(
    sectionEvidence = SectionEvidenceLight,
    sectionFacts = SectionFactsLight,
    sectionMethodology = SectionMethodologyLight,
    sectionTimeline = SectionTimelineLight
)

val LocalCorvusColors = staticCompositionLocalOf { DarkSectionColors }

object CorvusTheme {
    val colors: SectionColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCorvusColors.current
}

private val DarkColorScheme = darkColorScheme(
    primary = MonochromeWhite,
    onPrimary = CorvusVoid,
    primaryContainer = MonochromeGray,
    onPrimaryContainer = CorvusTextPrimary,
    secondary = CorvusTextSecondary,
    onSecondary = CorvusVoid,
    secondaryContainer = CorvusSurfaceRaised,
    onSecondaryContainer = CorvusTextPrimary,
    tertiary = MonochromeGray,
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
    primary = MonochromeBlack,
    onPrimary = CorvusVoidLight,
    primaryContainer = MonochromeGray,
    onPrimaryContainer = CorvusTextPrimaryLight,
    secondary = CorvusTextSecondaryLight,
    onSecondary = CorvusVoidLight,
    secondaryContainer = CorvusSurfaceRaisedLight,
    onSecondaryContainer = CorvusTextPrimaryLight,
    tertiary = MonochromeGray,
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
    colorPalette: ColorPalette = ColorPalette.MONOCHROME,
    content: @Composable () -> Unit
) {
    val palette = Palettes[colorPalette] ?: Palettes[ColorPalette.MONOCHROME]!!
    
    val colorScheme = if (darkTheme) {
        DarkColorScheme.copy(
            primary = palette.primaryDark,
            background = palette.surfaceDark,
            surface = palette.surfaceDark,
            surfaceVariant = palette.surfaceRaisedDark
        )
    } else {
        LightColorScheme.copy(
            primary = palette.primaryLight,
            background = palette.surfaceLight,
            surface = palette.surfaceLight,
            surfaceVariant = palette.surfaceRaisedLight
        )
    }
    
    val sectionColors = if (darkTheme) DarkSectionColors else LightSectionColors
    
    CompositionLocalProvider(
        LocalCorvusColors provides sectionColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = CorvusShapes,
            content = content
        )
    }
}
