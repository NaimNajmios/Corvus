package com.najmi.corvus.data.remote.knowledgegraph

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Named

class KnowledgeGraphClient @Inject constructor(
    private val httpClient: HttpClient,
    @Named("kgApiKey") private val apiKey: String
) {
    companion object {
        private const val BASE_URL = "https://kgsearch.googleapis.com/v1/entities:search"
        private const val MIN_SCORE_THRESHOLD = 100.0   // KG scores vary 0–1000+
        private const val REQUEST_TIMEOUT_MS  = 3000L   // Hard 3s timeout — never delays result
    }

    suspend fun searchEntity(query: String): KgItem? {
        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            try {
                val response = httpClient.get(BASE_URL) {
                    parameter("query",  query)
                    parameter("key",    apiKey)
                    parameter("limit",  3)          // Top 3 candidates
                    parameter("indent", false)
                }.body<KgSearchResponse>()

                response.items
                    .filter { it.score >= MIN_SCORE_THRESHOLD }
                    .maxByOrNull { it.score }       // Return highest-scoring entity
            } catch (e: Exception) {
                null    // Any network/parse error → silent null
            }
        }
    }

    // Overload for when we already know the entity name (from classifier)
    suspend fun searchEntityByName(
        name: String,
        types: List<String> = emptyList()
    ): KgItem? {
        val typeFilter = types.joinToString(",")
        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            try {
                val response = httpClient.get(BASE_URL) {
                    parameter("query",  name)
                    parameter("key",    apiKey)
                    parameter("limit",  5)
                    if (typeFilter.isNotBlank()) parameter("types", typeFilter)
                }.body<KgSearchResponse>()

                response.items
                    .filter { it.score >= MIN_SCORE_THRESHOLD }
                    .maxByOrNull { it.score }
            } catch (e: Exception) {
                null
            }
        }
    }
}
