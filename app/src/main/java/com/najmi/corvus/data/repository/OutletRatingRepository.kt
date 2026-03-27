package com.najmi.corvus.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.najmi.corvus.domain.model.*
import com.najmi.corvus.domain.util.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutletRatingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OutletRatingRepo"
        
        private val MY_OUTLET_PROFILES = mapOf(
            "bernama.com" to TopicCredibilityProfile(
                overall = 76,
                byTopic = mapOf(
                    ClaimType.STATISTICAL   to 88,
                    ClaimType.PERSON_FACT   to 82,
                    ClaimType.SCIENTIFIC    to 65,
                    ClaimType.CURRENT_EVENT to 78
                ),
                flags = setOf(CredibilityFlag.GOVERNMENT_AFFILIATED, CredibilityFlag.SYNDICATED)
            ),
            "malaysiakini.com" to TopicCredibilityProfile(
                overall = 80,
                byTopic = mapOf(
                    ClaimType.PERSON_FACT   to 82,
                    ClaimType.STATISTICAL   to 75
                ),
                flags = setOf(CredibilityFlag.PAYWALLED)
            ),
            "sebenarnya.my" to TopicCredibilityProfile(
                overall = 88,
                byTopic = mapOf(ClaimType.GENERAL to 90),
                flags = setOf(CredibilityFlag.FACT_CHECKER, CredibilityFlag.GOVERNMENT_AFFILIATED)
            ),
            "thestar.com.my" to TopicCredibilityProfile(
                overall = 72,
                byTopic = mapOf(
                    ClaimType.STATISTICAL   to 74,
                    ClaimType.SCIENTIFIC    to 60,
                    ClaimType.CURRENT_EVENT to 73
                ),
                flags = setOf(CredibilityFlag.CLICKBAIT_HEADLINES)
            )
        )
    }

    private val mbfcRatings: Map<String, OutletRating> by lazy { loadMbfcRatings() }

    fun getRating(url: String, claimType: ClaimType = ClaimType.GENERAL): OutletRating {
        val domain = extractDomain(url)
        
        // 1. Check Hardcoded Profiles (MY specific overrides)
        val profile = MY_OUTLET_PROFILES[domain]
        if (profile != null) {
            val baseRating = mbfcRatings[domain]
            return OutletRating(
                credibility = profile.byTopic[claimType] ?: profile.overall,
                bias = baseRating?.bias ?: 0,
                isGovAffiliated = profile.flags.contains(CredibilityFlag.GOVERNMENT_AFFILIATED),
                flags = profile.flags,
                ratingSource = RatingSource.MY_HARDCODED,
                mbfcCategory = baseRating?.mbfcCategory,
                confidence = 0.95f
            )
        }

        // 2. Fusion Logic
        val contributions = mutableListOf<RatingContribution>()
        
        // MBFC Contribution
        mbfcRatings[domain]?.let { mbfc ->
            contributions.add(RatingContribution(
                ratingSource = RatingSource.MBFC_CSV,
                rawScore = mbfc.credibility,
                weight = 0.5f,
                originalLabel = mbfc.mbfcCategory?.displayLabel
            ))
        }

        // Heuristic Contribution
        val heuristic = EnhancedDomainHeuristics.analyse(domain)
        contributions.add(RatingContribution(
            ratingSource = RatingSource.HEURISTIC,
            rawScore = heuristic.credibility,
            weight = 0.2f
        ))

        // Perform Fusion
        val fused = CredibilityFuser.fuse(contributions)

        return OutletRating(
            credibility = fused.composite,
            bias = mbfcRatings[domain]?.bias ?: 0,
            confidence = fused.confidence,
            isGovAffiliated = heuristic.flags.contains(CredibilityFlag.GOVERNMENT_AFFILIATED),
            isSatire = heuristic.flags.contains(CredibilityFlag.SATIRE),
            mbfcCategory = mbfcRatings[domain]?.mbfcCategory,
            ratingSource = if (contributions.any { it.ratingSource == RatingSource.MBFC_CSV }) RatingSource.MBFC_CSV else RatingSource.HEURISTIC,
            breakdown = fused.breakdown,
            flags = heuristic.flags
        )
    }

    fun getRatingOrNull(url: String): OutletRating? {
        val domain = extractDomain(url)
        return mbfcRatings[domain]
    }

    private fun extractDomain(url: String): String {
        return try {
            Uri.parse(url).host?.removePrefix("www.") ?: url
        } catch (e: Exception) {
            url
        }
    }

    private fun loadMbfcRatings(): Map<String, OutletRating> {
        val ratings = mutableMapOf<String, OutletRating>()
        try {
            context.assets.open("mbfc_ratings.csv").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val parts = line.split(",").map { it.trim() }
                    if (parts.size >= 4) {
                        val domain = parts[1].removePrefix("www.").lowercase()
                        val biasStr = parts[2].lowercase()
                        val factualStr = parts[3].lowercase()
                        val credibility = parts.getOrNull(6)?.toIntOrNull() ?: mapFactualToCredibility(factualStr)
                        
                        ratings[domain] = OutletRating(
                            credibility = credibility,
                            bias = mapBiasStringToInt(biasStr),
                            isGovAffiliated = biasStr.contains("government") || domain.endsWith(".gov") || domain.endsWith(".gov.my"),
                            isSatire = factualStr.contains("satire"),
                            mbfcCategory = mapFactualToMbfcCategory(factualStr),
                            ratingSource = RatingSource.MBFC_CSV,
                            confidence = 0.8f // MBFC is relatively high confidence
                        )
                    }
                }
            }
            Log.d(TAG, "Loaded ${ratings.size} MBFC ratings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load MBFC CSV: ${e.message}")
        }
        return ratings
    }

    private fun mapBiasStringToInt(bias: String): Int = when {
        bias.contains("extreme left") || bias.contains("far left") -> -2
        bias.contains("left")         -> -1
        bias.contains("center")       -> 0
        bias.contains("right") && !bias.contains("far") -> 1
        bias.contains("far right") || bias.contains("extreme right") -> 2
        else -> 0
    }

    private fun mapFactualToCredibility(factual: String): Int = when {
        factual.contains("very high") -> 95
        factual.contains("high")      -> 80
        factual.contains("mostly")    -> 65
        factual.contains("mixed")     -> 45
        factual.contains("low")       -> 25
        factual.contains("very low")  -> 10
        else                          -> 50
    }

    private fun mapFactualToMbfcCategory(factual: String): com.najmi.corvus.domain.model.MbfcCategory? = when {
        factual.contains("very high") -> com.najmi.corvus.domain.model.MbfcCategory.VERY_HIGH_FACTUAL
        factual.contains("high")      -> com.najmi.corvus.domain.model.MbfcCategory.HIGH_FACTUAL
        factual.contains("mostly")    -> com.najmi.corvus.domain.model.MbfcCategory.MOSTLY_FACTUAL
        factual.contains("mixed")     -> com.najmi.corvus.domain.model.MbfcCategory.MIXED_FACTUAL
        factual.contains("very low")  -> com.najmi.corvus.domain.model.MbfcCategory.VERY_LOW_FACTUAL
        factual.contains("low")       -> com.najmi.corvus.domain.model.MbfcCategory.LOW_FACTUAL
        factual.contains("satire")    -> com.najmi.corvus.domain.model.MbfcCategory.SATIRE
        else                          -> null
    }

}
