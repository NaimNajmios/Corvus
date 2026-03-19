package com.najmi.corvus.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.najmi.corvus.domain.model.OutletRating
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
            "bernama.com"              to OutletRating(credibility = 75, bias = 0,  isGovAffiliated = true),
            "malaysiakini.com"         to OutletRating(credibility = 80, bias = -1, isGovAffiliated = false),
            "freemalaysiatoday.com"    to OutletRating(credibility = 72, bias = -1, isGovAffiliated = false),
            "thestar.com.my"           to OutletRating(credibility = 78, bias = 0,  isGovAffiliated = false),
            "nst.com.my"               to OutletRating(credibility = 70, bias = 1,  isGovAffiliated = false),
            "astroawani.com"           to OutletRating(credibility = 73, bias = 0,  isGovAffiliated = false),
            "utusan.com.my"            to OutletRating(credibility = 60, bias = 2,  isGovAffiliated = false),
            "sebenarnya.my"            to OutletRating(credibility = 85, bias = 0,  isGovAffiliated = true),
            "sinarharian.com.my"       to OutletRating(credibility = 65, bias = 1,  isGovAffiliated = false)
        )
    }

    private val mbfcRatings: Map<String, OutletRating> by lazy { loadMbfcRatings() }

    fun getRating(url: String): OutletRating {
        val domain = extractDomain(url)
        return MY_OUTLET_RATINGS[domain]
            ?: mbfcRatings[domain]
            ?: estimateHeuristically(domain)
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
                    val parts = line.split(",")
                    if (parts.size >= 4) {
                        val domain = parts[0].trim().removePrefix("www.")
                        val cred = parts[1].trim().toIntOrNull() ?: 50
                        val bias = parts[2].trim().toIntOrNull() ?: 0
                        val category = parts[3].trim()
                        
                        ratings[domain] = OutletRating(
                            credibility = cred,
                            bias = bias,
                            isGovAffiliated = false,
                            isSatire = category.equals("Satire", ignoreCase = true),
                            mbfcCategory = category
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

    private fun estimateHeuristically(domain: String): OutletRating {
        return when {
            domain.endsWith(".gov.my") -> OutletRating(88, 0, true)
            domain.endsWith(".edu.my") -> OutletRating(85, 0, false)
            domain.endsWith(".gov")     -> OutletRating(82, 0, true)
            domain.endsWith(".edu")     -> OutletRating(80, 0, false)
            domain.contains("blog")     -> OutletRating(30, 0, false)
            domain.contains("wordpress") -> OutletRating(25, 0, false)
            else                        -> OutletRating(50, 0, false)
        }
    }
}
