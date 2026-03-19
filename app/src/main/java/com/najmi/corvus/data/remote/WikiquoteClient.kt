package com.najmi.corvus.data.remote

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class WikiquoteSummary(
    val title: String,
    val extract: String? = null,
    val thumbnail: WikiquoteThumbnail? = null,
    val description: String? = null
)

@Serializable
data class WikiquoteThumbnail(
    val source: String
)

@Singleton
class WikiquoteClient @Inject constructor(
    private val httpClient: HttpClient
) {
    companion object {
        private const val TAG = "WikiquoteClient"
        private const val BASE_URL = "https://en.wikiquote.org/api/rest_v1/page/summary"
    }

    suspend fun getSummary(personName: String): WikiquoteSummary? {
        val encoded = personName.replace(" ", "_")
        return try {
            Log.d(TAG, "Fetching Wikiquote summary for: $personName")
            httpClient.get("$BASE_URL/$encoded").body<WikiquoteSummary>()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Wikiquote summary: ${e.message}")
            null
        }
    }

    // Fuzzy match logic for quotes
    fun findMatch(submitted: String, extract: String): QuoteMatchResult {
        if (extract.isBlank()) return QuoteMatchResult.NOT_FOUND
        
        val normalizedSubmitted = submitted.lowercase().trim()
        val normalizedExtract = extract.lowercase().trim()

        return when {
            normalizedExtract.contains(normalizedSubmitted) -> QuoteMatchResult.EXACT(submitted)
            // Simple heuristic for partial match: check if a good chunk of words overlap
            calculateOverlap(normalizedSubmitted, normalizedExtract) > 0.6f -> {
                QuoteMatchResult.PARTIAL(extract.take(200) + "...")
            }
            else -> QuoteMatchResult.NOT_FOUND
        }
    }

    private fun calculateOverlap(s1: String, s2: String): Float {
        val words1 = s1.split(" ").toSet()
        val words2 = s2.split(" ").toSet()
        val common = words1.intersect(words2).size
        return common.toFloat() / words1.size.coerceAtLeast(1)
    }
}

sealed class QuoteMatchResult {
    data class EXACT(val matchedText: String) : QuoteMatchResult()
    data class PARTIAL(val similarText: String) : QuoteMatchResult()
    object NOT_FOUND : QuoteMatchResult()
}
