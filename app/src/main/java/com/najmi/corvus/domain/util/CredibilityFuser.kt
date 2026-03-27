package com.najmi.corvus.domain.util

import com.najmi.corvus.domain.model.*

object CredibilityFuser {

    fun fuse(contributions: List<RatingContribution>): FusedCredibilityScore {
        if (contributions.isEmpty()) {
            return FusedCredibilityScore(
                composite  = 50,
                confidence = 0f,
                sources    = emptyList(),
                breakdown  = ScoreBreakdown(50, 50, 50, 50)
            )
        }

        // Weighted average of available scores
        val totalWeight    = contributions.sumOf { it.weight.toDouble() }.toFloat()
        val weightedSum    = contributions.sumOf {
            (it.rawScore * it.weight).toDouble()
        }.toFloat()

        val composite      = (weightedSum / totalWeight).toInt().coerceIn(0, 100)

        // Confidence: how many sources contributed + how much they agreed
        val sourceCount    = contributions.size
        val maxSources     = 4  // MBFC + AdFontes + NewsGuard + heuristic
        val coverage       = (sourceCount.toFloat() / maxSources).coerceIn(0f, 1f)

        // Agreement: lower variance = higher confidence
        val scores         = contributions.map { it.rawScore.toFloat() }
        val mean           = scores.average().toFloat()
        val variance       = scores.map { (it - mean) * (it - mean) }.average().toFloat()
        val agreement      = (1f - (variance / 2500f)).coerceIn(0f, 1f) // 2500 = 50^2 max variance

        val confidence     = (coverage * 0.6f + agreement * 0.4f).coerceIn(0f, 1f)

        return FusedCredibilityScore(
            composite  = composite,
            confidence = confidence,
            sources    = contributions,
            breakdown  = buildBreakdown(contributions)
        )
    }

    private fun buildBreakdown(contributions: List<RatingContribution>): ScoreBreakdown {
        val mbfc       = contributions.find { it.ratingSource == RatingSource.MBFC_CSV }
        val adFontes   = contributions.find { it.ratingSource == RatingSource.AD_FONTES }
        val newsGuard  = contributions.find { it.ratingSource == RatingSource.NEWS_GUARD }

        return ScoreBreakdown(
            factualAccuracy = mbfc?.rawScore
                ?: adFontes?.rawScore
                ?: contributions.firstOrNull()?.rawScore ?: 50,

            sourceQuality   = newsGuard?.rawScore
                ?: ((mbfc?.rawScore ?: 50) + (adFontes?.rawScore ?: 50)) / 2,

            biasImpact      = 75, // Placeholder

            transparency    = newsGuard?.rawScore
                ?: if (mbfc != null) 70 else 50
        )
    }
}
