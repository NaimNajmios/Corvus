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
data class DosmDataset(
    val id: String? = null,
    val catalog_id: String? = null,
    val description: String? = null,
    val label: String? = null
)

@Singleton
class DosmClient @Inject constructor(
    private val httpClient: HttpClient
) {
    companion object {
        private const val TAG = "DosmClient"
        private const val BASE_URL = "https://api.data.gov.my/data-catalogue"
    }

    suspend fun search(query: String): List<DosmDataset> {
        return try {
            Log.d(TAG, "Searching OpenDOSM for: $query")
            httpClient.get(BASE_URL) {
                parameter("meta", true)
                parameter("filter__description__icontains", query)
            }.body<List<DosmDataset>>()
        } catch (e: Exception) {
            Log.e(TAG, "DOSM search failed: ${e.message}")
            emptyList()
        }
    }
}
