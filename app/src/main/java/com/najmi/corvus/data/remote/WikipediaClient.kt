package com.najmi.corvus.data.remote

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class WikipediaSummary(
    val title: String,
    val extract: String? = null,
    val description: String? = null
)

@Serializable
data class WikipediaSearchResponse(
    val query: WikipediaQuery? = null
)

@Serializable
data class WikipediaQuery(
    val search: List<WikipediaSearchResult> = emptyList()
)

@Serializable
data class WikipediaSearchResult(
    val title: String,
    val snippet: String? = null
)

@Singleton
class WikipediaClient @Inject constructor(
    private val httpClient: HttpClient
) {
    companion object {
        private const val TAG = "WikipediaClient"
        private const val SUMMARY_BASE = "https://en.wikipedia.org/api/rest_v1/page/summary"
        private const val SEARCH_BASE = "https://en.wikipedia.org/w/api.php"
    }

    suspend fun getSummary(topic: String): WikipediaSummary? {
        val encoded = topic.replace(" ", "_")
        return try {
            Log.d(TAG, "Fetching Wikipedia summary for: $topic")
            httpClient.get("$SUMMARY_BASE/$encoded").body<WikipediaSummary>()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Wikipedia summary: ${e.message}")
            null
        }
    }

    suspend fun search(query: String): List<WikipediaSearchResult> {
        return try {
            Log.d(TAG, "Searching Wikipedia for: $query")
            val response = httpClient.get(SEARCH_BASE) {
                parameter("action", "query")
                parameter("list", "search")
                parameter("srsearch", query)
                parameter("format", "json")
                parameter("srlimit", 3)
            }.body<WikipediaSearchResponse>()
            response.query?.search ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Wikipedia search failed: ${e.message}")
            emptyList()
        }
    }
}
