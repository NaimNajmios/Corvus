package com.najmi.corvus.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Serializable
data class GoogleCustomSearchResponse(
    val items: List<GoogleSearchResult> = emptyList()
)

@Serializable
data class GoogleSearchResult(
    val title: String,
    val link: String,
    @SerialName("displayLink") val displayLink: String,
    val snippet: String? = null,
    @SerialName("pagemap") val pageMap: PageMap? = null
)

@Serializable
data class PageMap(
    val metatags: List<MetaTag>? = null
)

@Serializable
data class MetaTag(
    @SerialName("og:site_name") val ogSiteName: String? = null,
    @SerialName("twitter:card") val twitterCard: String? = null
)

@Singleton
class GoogleCustomSearchClient @Inject constructor(
    private val httpClient: HttpClient,
    @Named("google_cse_api_key") private val apiKey: String,
    @Named("google_cse_id") private val cseId: String
) {
    suspend fun search(query: String, maxResults: Int = 7): GoogleCustomSearchResponse {
        return httpClient.get("https://www.googleapis.com/customsearch/v1") {
            parameter("key", apiKey)
            parameter("cx", cseId)
            parameter("q", query)
            parameter("num", maxResults.coerceIn(1, 10))
        }.body()
    }
}