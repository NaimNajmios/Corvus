package com.najmi.corvus.domain.util

import androidx.compose.ui.graphics.Color

/**
 * Human-readable labels for confidence scores.
 * Enhancement 8 — Confidence Contextual Label
 */
fun Float.confidenceLabel(): String = when {
    this >= 0.90f -> "Very high confidence"
    this >= 0.75f -> "High confidence"
    this >= 0.60f -> "Moderate confidence"
    this >= 0.40f -> "Low confidence"
    this >= 0.20f -> "Very low confidence"
    else          -> "Treat with caution"
}

/**
 * Short human-readable labels for confidence scores, suitable for tight spaces.
 */
fun Float.confidenceLabelShort(): String = when {
    this >= 0.90f -> "Very high"
    this >= 0.75f -> "High"
    this >= 0.60f -> "Moderate"
    this >= 0.40f -> "Low"
    else          -> "Very low"
}

/**
 * Returns a color based on the confidence level.
 */
fun Float.confidenceColor(
    highColor: Color,
    midColor: Color,
    lowColor: Color
): Color = when {
    this >= 0.75f -> highColor
    this >= 0.50f -> midColor
    else          -> lowColor
}
