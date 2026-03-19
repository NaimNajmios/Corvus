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
data class GdeltResponse(
    val articles: List<GdeltArticle> = emptyList()
)

@Serializable
data class GdeltArticle(
    val url: String,
    val title: String,
    val sourcecountry: String? = null,
    val domain: String? = null,
    val seendate: String? = null
)

@Singleton
class GdeltClient @Inject constructor(
    private val httpClient: HttpClient
) {
    companion object {
        private const val TAG = "GdeltClient"
        private const val BASE_URL = "https://api.gdeltproject.org/api/v2/doc/doc"
    }

    suspend fun search(query: String): List<GdeltArticle> {
        return try {
            Log.d(TAG, "Searching GDELT for: $query")
            val response = httpClient.get(BASE_URL) {
                parameter("query", query)
                parameter("mode", "artlist")
                parameter("maxrecords", 10)
                parameter("format", "json")
            }.body<GdeltResponse>()
            response.articles
        } catch (e: Exception) {
            Log.e(TAG, "GDELT search failed: ${e.message}")
            emptyList()
        }
    }
}
