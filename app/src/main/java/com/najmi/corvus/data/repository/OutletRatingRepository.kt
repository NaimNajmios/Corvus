package com.najmi.corvus.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.najmi.corvus.domain.model.OutletRating
import com.najmi.corvus.domain.model.MbfcCategory
import com.najmi.corvus.domain.model.RatingSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutletRatingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OutletRatingRepo"
        
        private val MY_OUTLET_RATINGS = mapOf(
            "bernama.com"              to OutletRating(credibility = 75, bias = 0,  isGovAffiliated = true,  mbfcCategory = MbfcCategory.GOVERNMENT, ratingSource = RatingSource.MY_HARDCODED),
            "malaysiakini.com"         to OutletRating(credibility = 80, bias = -1, isGovAffiliated = false, mbfcCategory = MbfcCategory.HIGH_FACTUAL, ratingSource = RatingSource.MY_HARDCODED),
            "freemalaysiatoday.com"    to OutletRating(credibility = 72, bias = -1, isGovAffiliated = false, mbfcCategory = MbfcCategory.HIGH_FACTUAL, ratingSource = RatingSource.MY_HARDCODED),
            "thestar.com.my"           to OutletRating(credibility = 78, bias = 0,  isGovAffiliated = false, mbfcCategory = MbfcCategory.HIGH_FACTUAL, ratingSource = RatingSource.MY_HARDCODED),
            "nst.com.my"               to OutletRating(credibility = 70, bias = 1,  isGovAffiliated = false, mbfcCategory = MbfcCategory.MOSTLY_FACTUAL, ratingSource = RatingSource.MY_HARDCODED),
            "astroawani.com"           to OutletRating(credibility = 73, bias = 0,  isGovAffiliated = false, mbfcCategory = MbfcCategory.HIGH_FACTUAL, ratingSource = RatingSource.MY_HARDCODED),
            "utusan.com.my"            to OutletRating(credibility = 60, bias = 2,  isGovAffiliated = false, mbfcCategory = MbfcCategory.MIXED_FACTUAL, ratingSource = RatingSource.MY_HARDCODED),
            "sebenarnya.my"            to OutletRating(credibility = 85, bias = 0,  isGovAffiliated = true,  mbfcCategory = MbfcCategory.GOVERNMENT, ratingSource = RatingSource.MY_HARDCODED),
            "sinarharian.com.my"       to OutletRating(credibility = 65, bias = 1,  isGovAffiliated = false, mbfcCategory = MbfcCategory.MOSTLY_FACTUAL, ratingSource = RatingSource.MY_HARDCODED)
        )
    }

    private val mbfcRatings: Map<String, OutletRating> by lazy { loadMbfcRatings() }

    fun getRating(url: String): OutletRating {
        val domain = extractDomain(url)
        return MY_OUTLET_RATINGS[domain]
            ?: mbfcRatings[domain]
            ?: estimateHeuristically(domain)
    }

    fun getRatingOrNull(url: String): OutletRating? {
        val domain = extractDomain(url)
        return MY_OUTLET_RATINGS[domain] ?: mbfcRatings[domain]
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
                            ratingSource = RatingSource.MBFC_CSV
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

    private fun estimateHeuristically(domain: String): OutletRating {
        return when {
            domain.endsWith(".gov.my") -> OutletRating(90, 0, true, mbfcCategory = com.najmi.corvus.domain.model.MbfcCategory.GOVERNMENT, ratingSource = RatingSource.HEURISTIC)
            domain.endsWith(".gov")     -> OutletRating(85, 0, true, mbfcCategory = com.najmi.corvus.domain.model.MbfcCategory.GOVERNMENT, ratingSource = RatingSource.HEURISTIC)
            domain.endsWith(".edu.my") || domain.endsWith(".edu") -> OutletRating(82, 0, false, ratingSource = RatingSource.HEURISTIC)
            domain.contains("blog")     -> OutletRating(25, 0, false, ratingSource = RatingSource.HEURISTIC)
            domain.contains("wordpress") || domain.contains("blogger") -> OutletRating(20, 0, false, ratingSource = RatingSource.HEURISTIC)
            else                        -> OutletRating(50, 0, false, ratingSource = RatingSource.UNKNOWN)
        }
    }
}
