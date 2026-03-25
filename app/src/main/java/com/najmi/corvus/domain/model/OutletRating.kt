package com.najmi.corvus.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class OutletRating(
    val credibility: Int,              // 0–100
    val bias: Int,                     // -2 to +2
    val isGovAffiliated: Boolean,
    val isSatire: Boolean = false,
    val mbfcCategory: MbfcCategory? = null,
    val ratingSource: RatingSource = RatingSource.HEURISTIC
)

@Serializable
enum class MbfcCategory(val displayLabel: String, val credibilityHint: String) {
    VERY_HIGH_FACTUAL  ("Very High Factual",  "Consistently accurate reporting"),
    HIGH_FACTUAL       ("High Factual",        "Generally accurate reporting"),
    MOSTLY_FACTUAL     ("Mostly Factual",      "Mostly accurate, minor errors"),
    MIXED_FACTUAL      ("Mixed Factual",       "Inconsistent accuracy"),
    LOW_FACTUAL        ("Low Factual",         "Frequent factual errors"),
    VERY_LOW_FACTUAL   ("Very Low Factual",   "Unreliable, poor sourcing"),
    SATIRE             ("Satire",              "Intentionally satirical content"),
    CONSPIRACY         ("Conspiracy / Pseudoscience", "Promotes conspiracy theories"),
    PRO_SCIENCE        ("Pro-Science",         "Peer-reviewed science focus"),
    SCIENCE            ("Science",             "Scientific focus"),
    GOVERNMENT         ("Government",          "Official government source")
}

@Serializable
enum class RatingSource {
    MBFC_CSV,      // From bundled mbfc_ratings.csv
    MY_HARDCODED,  // From MY_OUTLET_RATINGS hardcoded map
    HEURISTIC,     // Domain-based inference
    UNKNOWN
}

// Computed properties for UI helper (to be used in Composable or as extension functions)
fun OutletRating.credibilityLabel(): String = when {
    credibility >= 90 -> "Highly Factual"
    credibility >= 75 -> "Generally Factual"
    credibility >= 60 -> "Mostly Factual"
    credibility >= 45 -> "Mixed"
    credibility >= 30 -> "Low Factual"
    else              -> "Unreliable"
}

fun OutletRating.biasLabel(): String = when (bias) {
    -2 -> "Far Left"
    -1 -> "Left-Center"
     0 -> "Center"
     1 -> "Right-Center"
     2 -> "Far Right"
    else -> "Unknown"
}
