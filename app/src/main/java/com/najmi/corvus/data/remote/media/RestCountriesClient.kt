package com.najmi.corvus.data.remote.media

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestCountriesClient @Inject constructor(
    private val httpClient: HttpClient
) {
    companion object {
        private const val BASE_URL   = "https://restcountries.com/v3.1"
        private const val TIMEOUT_MS = 4_000L

        fun flagPngUrl(isoCode: String) =
            "https://flagcdn.com/w160/${isoCode.lowercase()}.png"

        fun flagSvgUrl(isoCode: String) =
            "https://flagcdn.com/${isoCode.lowercase()}.svg"
    }

    suspend fun fetchByName(countryName: String): RestCountry? {
        return withTimeout(TIMEOUT_MS) {
            runCatching {
                httpClient.get("$BASE_URL/name/${countryName.encodeURLPath()}") {
                    parameter("fields", "name,cca2,flags,capital,population,region")
                }.body<List<RestCountry>>()
                    .firstOrNull()
            }.getOrNull()
        }
    }

    suspend fun fetchByIso(isoCode: String): RestCountry? {
        return withTimeout(TIMEOUT_MS) {
            runCatching {
                httpClient.get("$BASE_URL/alpha/$isoCode") {
                    parameter("fields", "name,cca2,flags,capital,population")
                }.body<List<RestCountry>>()
                    .firstOrNull()
            }.getOrNull()
        }
    }

    private fun String.encodeURLPath(): String =
        this.replace(" ", "_")
            .replace(Regex("[^a-zA-Z0-9_]")) { char ->
                "%${char.value.first().code.toString(16).uppercase().padStart(2, '0')}"
            }
}

@Serializable
data class RestCountry(
    val name       : RestCountryName,
    val cca2       : String,
    val flags      : RestCountryFlags,
    val capital    : List<String>? = null,
    val population : Long? = null,
    val region     : String? = null
)

@Serializable
data class RestCountryName(
    val common   : String,
    val official : String
)

@Serializable
data class RestCountryFlags(
    val png : String,
    val svg : String
)
