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
data class WikidataResponse(
    val results: WikidataResults? = null
)

@Serializable
data class WikidataResults(
    val bindings: List<Map<String, WikidataValue>> = emptyList()
)

@Serializable
data class WikidataValue(
    val value: String,
    val type: String? = null
)

@Singleton
class WikidataSparqlClient @Inject constructor(
    private val httpClient: HttpClient
) {
    companion object {
        private const val TAG = "WikidataClient"
        private const val SPARQL_ENDPOINT = "https://query.wikidata.org/sparql"
    }

    suspend fun query(sparql: String): WikidataResponse? {
        return try {
            Log.d(TAG, "Executing SPARQL query...")
            httpClient.get(SPARQL_ENDPOINT) {
                parameter("query", sparql)
                parameter("format", "json")
            }.body<WikidataResponse>()
        } catch (e: Exception) {
            Log.e(TAG, "Wikidata query failed: ${e.message}")
            null
        }
    }

    // Helper for common "Person Role" queries
    suspend fun getPersonRoles(personName: String): String? {
        val sparql = """
            SELECT ?roleLabel WHERE {
              ?person rdfs:label "$personName"@en.
              ?person wdt:P39 ?role.
              SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
            }
            LIMIT 5
        """.trimIndent()
        
        val response = query(sparql)
        return response?.results?.bindings?.joinToString(", ") { 
            it["roleLabel"]?.value ?: ""
        }
    }
}
