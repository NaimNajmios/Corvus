package com.najmi.corvus.data.remote

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class JunkipediaResponse(
    val results: List<JunkipediaResult> = emptyList()
)

@Serializable
data class JunkipediaResult(
    val id: String,
    val text: String,
    val label: String? = null,
    val category: String? = null,
    val debunk_url: String? = null,
    val first_seen: String? = null
)

@Singleton
class JunkipediaClient @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json
) {
    companion object {
        private const val TAG = "JunkipediaClient"
        private const val BASE_URL = "https://api.junkipedia.org/v1/search" // Example endpoint
    }

    suspend fun search(query: String): List<JunkipediaResult> {
        return try {
            // Placeholder: Junkipedia may require specific headers or use a different public endpoint
            // For now, implementing as a standard search GET request
            val response = httpClient.get(BASE_URL) {
                parameter("q", query)
                parameter("limit", 5)
            }

            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                val parsed = json.decodeFromString<JunkipediaResponse>(body)
                parsed.results
            } else {
                Log.e(TAG, "Search failed with status: ${response.status}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching Junkipedia: ${e.message}")
            emptyList()
        }
    }
}
