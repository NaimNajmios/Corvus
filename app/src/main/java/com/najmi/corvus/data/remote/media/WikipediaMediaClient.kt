package com.najmi.corvus.data.remote.media

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WikipediaMediaClient @Inject constructor(
    private val httpClient: HttpClient
) {
    companion object {
        private const val BASE_URL   = "https://en.wikipedia.org/api/rest_v1/page/summary"
        private const val TIMEOUT_MS = 4_000L
    }

    suspend fun fetchSummary(pageTitle: String): WikipediaSummary? {
        return withTimeout(TIMEOUT_MS) {
            runCatching {
                httpClient.get("$BASE_URL/${pageTitle.encodeURLPath()}") {
                    header(HttpHeaders.Accept, ContentType.Application.Json)
                    header(HttpHeaders.UserAgent, "CorvusFactChecker/1.0")
                }.body<WikipediaSummary>()
            }.getOrNull()
        }
    }

    suspend fun fetchPersonSummary(personName: String): WikipediaSummary? {
        val normalised = personName.trim().replace(" ", "_")

        fetchSummary(normalised)?.let { summary ->
            if (summary.type != "disambiguation" && summary.thumbnail != null) {
                return summary
            }
        }

        val qualifiers = listOf(
            "politician",
            "Prime_Minister_of_Malaysia",
            "Malaysian_politician",
            "Deputy_Prime_Minister_of_Malaysia",
            "Malaysian_businessman",
            "Malaysian_footballer"
        )

        for (qualifier in qualifiers) {
            fetchSummary("${normalised}_($qualifier)")?.let { summary ->
                if (summary.type != "disambiguation" && summary.thumbnail != null) {
                    return summary
                }
            }
        }

        return null
    }

    private fun String.encodeURLPath(): String =
        this.replace(" ", "_")
            .replace(Regex("[^a-zA-Z0-9_(-)]")) { char ->
                "%${char.value.first().code.toString(16).uppercase().padStart(2, '0')}"
            }
}

@Serializable
data class WikipediaSummary(
    val type        : String,
    val title       : String,
    val displayTitle : String? = null,
    val description  : String? = null,
    val extract      : String? = null,
    val thumbnail    : WikiThumbnail? = null,
    val contentUrls  : WikiContentUrls? = null
)

@Serializable
data class WikiThumbnail(
    val source : String,
    val width  : Int,
    val height : Int
)

@Serializable
data class WikiContentUrls(
    val desktop : WikiUrlPair? = null
)

@Serializable
data class WikiUrlPair(
    val page : String? = null
)
