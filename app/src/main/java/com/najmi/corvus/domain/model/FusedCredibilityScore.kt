package com.najmi.corvus.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FusedCredibilityScore(
    val composite: Int,
    val confidence: Float,
    val sources: List<RatingContribution>,
    val breakdown: ScoreBreakdown
)

@Serializable
data class RatingContribution(
    val ratingSource: RatingSource,
    val rawScore: Int,
    val weight: Float,
    val originalLabel: String? = null
)

@Serializable
data class ScoreBreakdown(
    val factualAccuracy: Int,   // 0–100
    val sourceQuality: Int,     // 0–100
    val biasImpact: Int,        // 0–100 (100 = no bias impact)
    val transparency: Int       // 0–100
)
