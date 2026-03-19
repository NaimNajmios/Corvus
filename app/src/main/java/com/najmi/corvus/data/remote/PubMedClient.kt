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
data class PubMedSearchResponse(
    val esearchresult: PubMedSearchResult? = null
)

@Serializable
data class PubMedSearchResult(
    val idlist: List<String> = emptyList()
)

@Singleton
class PubMedClient @Inject constructor(
    private val httpClient: HttpClient
) {
    companion object {
        private const val TAG = "PubMedClient"
        private const val SEARCH_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi"
        private const val FETCH_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi"
    }

    suspend fun search(query: String): List<String> {
        return try {
            Log.d(TAG, "Searching PubMed for: $query")
            val response = httpClient.get(SEARCH_URL) {
                parameter("db", "pubmed")
                parameter("term", query)
                parameter("retmax", 5)
                parameter("retmode", "json")
            }.body<PubMedSearchResponse>()
            response.esearchresult?.idlist ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "PubMed search failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchAbstract(pmid: String): String? {
        // efetch returns text/xml usually, simplified here
        return try {
            httpClient.get(FETCH_URL) {
                parameter("db", "pubmed")
                parameter("id", pmid)
                parameter("rettype", "abstract")
                parameter("retmode", "text")
            }.body<String>()
        } catch (e: Exception) {
            null
        }
    }
}
