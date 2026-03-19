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
data class WorldBankIndicator(
    val id: String,
    val value: String
)

@Serializable
data class WorldBankValue(
    val indicator: WorldBankIndicator,
    val country: WorldBankIndicator,
    val value: Double?,
    val date: String
)

@Singleton
class WorldBankClient @Inject constructor(
    private val httpClient: HttpClient
) {
    companion object {
        private const val TAG = "WorldBankClient"
        private const val BASE_URL = "https://api.worldbank.org/v2"
    }

    // Example indicator: NY.GDP.MKTP.KD.ZG (GDP growth)
    suspend fun getIndicator(countryCode: String, indicatorCode: String): List<WorldBankValue> {
        return try {
            Log.d(TAG, "Fetching World Bank data for $countryCode, indicator $indicatorCode")
            val response = httpClient.get("$BASE_URL/country/$countryCode/indicator/$indicatorCode") {
                parameter("format", "json")
                parameter("mrv", 5) // most recent 5 values
            }.body<List<Any>>() // World Bank returns [meta, data]
            
            // Note: Simplification here, we'd need to parse the second element as List<WorldBankValue>
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "World Bank fetch failed: ${e.message}")
            emptyList()
        }
    }
}
