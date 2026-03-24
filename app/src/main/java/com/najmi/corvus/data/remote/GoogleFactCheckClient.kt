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
data class GoogleFactCheckResponse(
    val claims: List<GoogleClaim> = emptyList()
)

@Serializable
data class GoogleClaim(
    val text: String,
    val claimReview: List<ClaimReview> = emptyList()
)

@Serializable
data class ClaimReview(
    val publisher: Publisher,
    val url: String,
    val title: String,
    val textualRating: String,
    val reviewDate: String? = null,
    val languageCode: String = "en"
)

@Serializable
data class Publisher(
    val name: String,
    val site: String
)

@Singleton
class GoogleFactCheckClient @Inject constructor(
    private val httpClient: HttpClient,
    @Named("google_fact_check") private val apiKey: String
) {
    suspend fun search(query: String): GoogleFactCheckResponse {
        return httpClient.get("https://factchecktools.googleapis.com/v1alpha1/claims:search") {
            parameter("query", query)
            parameter("key", apiKey)
            parameter("languageCode", "en")
        }.body()
    }
}
