package com.najmi.corvus.ui.theme

import androidx.compose.ui.graphics.Color

val CorvusVoid = Color(0xFF0A0A0C)
val CorvusSurface = Color(0xFF111116)
val CorvusSurfaceRaised = Color(0xFF1A1A22)
val CorvusBorder = Color(0xFF2A2A35)
val CorvusTextPrimary = Color(0xFFF0EFE8)
val CorvusTextSecondary = Color(0xFF8A8A99)
val CorvusTextTertiary = Color(0xFF4A4A5A)

val CorvusVoidLight = Color(0xFFFFFFFF)
val CorvusSurfaceLight = Color(0xFFF5F5F5)
val CorvusSurfaceRaisedLight = Color(0xFFE8E8E8)
val CorvusBorderLight = Color(0xFFD0D0D0)
val CorvusTextPrimaryLight = Color(0xFF1A1A1A)
val CorvusTextSecondaryLight = Color(0xFF6A6A6A)
val CorvusTextTertiaryLight = Color(0xFF9A9A9A)

// Monochromatic Accents
val MonochromeWhite = Color(0xFFFFFFFF)
val MonochromeBlack = Color(0xFF000000)
val MonochromeGray = Color(0xFF8A8A99)

// Muted Verdict Colors for Monochromatic Theme
val VerdictTrue = Color(0xFF4A8A5A)
val VerdictFalse = Color(0xFF8A4A4A)
val VerdictMisleading = Color(0xFF8A7A4A)
val VerdictPartiallyTrue = Color(0xFF4A6A8A)
val VerdictUnverifiable = Color(0xFF5A5A6A)

// Section-specific accent colors (very subtle tints for cards)
val SectionEvidence = Color(0xFF4A6A8A) // Subtle blue-gray
val SectionFacts = Color(0xFF4A8A5A)    // Subtle green-gray
val SectionMethodology = Color(0xFF6A5A8A) // Subtle purple-gray
val SectionTimeline = Color(0xFF8A7A4A)    // Subtle amber-gray

// Source Indicator Colors
val CredibilityHigh = Color(0xFF4CAF50)
val CredibilityMedium = Color(0xFFFFC107)
val CredibilityLow = Color(0xFFF44336)

val BiasLeft = Color(0xFF2196F3)
val BiasLeftCenter = Color(0xFF03A9F4)
val BiasCenter = Color(0xFF9E9E9E)
val BiasRightCenter = Color(0xFFFF9800)
val BiasRight = Color(0xFFF44336)

val SectionEvidenceLight = Color(0xFFD0E0EF)
val SectionFactsLight = Color(0xFFD0EFC0)
val SectionMethodologyLight = Color(0xFFE0D0EF)
val SectionTimelineLight = Color(0xFFEFE0D0)

enum class ColorPalette(val label: String) {
    MONOCHROME("Monochrome"),
    OCEAN("Ocean"),
    FOREST("Forest"),
    SUNSET("Sunset"),
    LAVENDER("Lavender")
}

data class PaletteColors(
    val primaryDark: Color,
    val primaryLight: Color,
    val surfaceDark: Color,
    val surfaceLight: Color,
    val surfaceRaisedDark: Color,
    val surfaceRaisedLight: Color
)

val Palettes = mapOf(
    ColorPalette.MONOCHROME to PaletteColors(
        primaryDark = MonochromeWhite,
        primaryLight = MonochromeBlack,
        surfaceDark = CorvusSurface,
        surfaceLight = CorvusSurfaceLight,
        surfaceRaisedDark = CorvusSurfaceRaised,
        surfaceRaisedLight = CorvusSurfaceRaisedLight
    ),
    ColorPalette.OCEAN to PaletteColors(
        primaryDark = Color(0xFF8AB4F8), // Soft Blue
        primaryLight = Color(0xFF1967D2), // Deep Blue
        surfaceDark = Color(0xFF1A1C1E), // Navy Surface
        surfaceLight = Color(0xFFF1F3F4), // Very Light Blue-Gray
        surfaceRaisedDark = Color(0xFF2D2F31),
        surfaceRaisedLight = Color(0xFFE8EAED)
    ),
    ColorPalette.FOREST to PaletteColors(
        primaryDark = Color(0xFF81C995), // Soft Green
        primaryLight = Color(0xFF188038), // Deep Green
        surfaceDark = Color(0xFF171B17), // Forest Surface
        surfaceLight = Color(0xFFF3F5F3), // Light Sage
        surfaceRaisedDark = Color(0xFF282C28),
        surfaceRaisedLight = Color(0xFFE6E8E6)
    ),
    ColorPalette.SUNSET to PaletteColors(
        primaryDark = Color(0xFFFDB462), // Soft Amber
        primaryLight = Color(0xFFE37400), // Deep Orange
        surfaceDark = Color(0xFF1E1A17), // Warm Surface
        surfaceLight = Color(0xFFF5F3F1), // Light Peach
        surfaceRaisedDark = Color(0xFF2D2825),
        surfaceRaisedLight = Color(0xFFE9E6E3)
    ),
    ColorPalette.LAVENDER to PaletteColors(
        primaryDark = Color(0xFFD7AEFB), // Soft Purple
        primaryLight = Color(0xFF9334E6), // Deep Purple
        surfaceDark = Color(0xFF1C1A1E), // Violet Surface
        surfaceLight = Color(0xFFF4F1F5), // Light Lavender
        surfaceRaisedDark = Color(0xFF2C282D),
        surfaceRaisedLight = Color(0xFFE8E5E9)
    )
)

// ── Additional UI Colors ────────────────────────────────────────────
val VerdictUnverified = Color(0xFF5A5A6A)
val CorvusTextTertiaryAlpha = Color(0xB34A4A5A)
val CorvusTextTertiaryLightAlpha = Color(0xB39A9A9A)
