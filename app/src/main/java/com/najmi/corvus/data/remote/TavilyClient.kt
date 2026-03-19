package com.najmi.corvus.data.remote

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Serializable
data class TavilySearchRequest(
    val query: String,
    @SerialName("api_key") val apiKey: String,
    @SerialName("search_depth") val searchDepth: String = "basic",
    @SerialName("max_results") val maxResults: Int = 5,
    @SerialName("include_raw_content") val includeRawContent: Boolean = false
)

@Serializable
data class TavilySearchResponse(
    val results: List<TavilyResult> = emptyList()
)

@Serializable
data class TavilyResult(
    val title: String,
    val url: String,
    val publisher: String? = null,
    val content: String? = null
)

@Singleton
class TavilyClient @Inject constructor(
    private val httpClient: HttpClient,
    @Named("tavily") private val apiKey: String
) {
    companion object {
        private const val TAG = "TavilyClient"
    }

    suspend fun search(query: String, maxResults: Int = 5): TavilySearchResponse {
        Log.d(TAG, "Searching with API key: ${apiKey.take(10)}...")
        
        return httpClient.post("https://api.tavily.com/search") {
            contentType(ContentType.Application.Json)
            setBody(TavilySearchRequest(
                query = query,
                apiKey = apiKey,
                maxResults = maxResults
            ))
        }.body()
    }
}
