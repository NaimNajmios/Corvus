package com.najmi.corvus.data.remote

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
data class GeminiRequest(
    val contents: List<GeminiContent>
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList()
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContentResponse
)

@Serializable
data class GeminiContentResponse(
    val parts: List<GeminiPartResponse>
)

@Serializable
data class GeminiPartResponse(
    val text: String
)

@Singleton
class GeminiClient @Inject constructor(
    private val httpClient: HttpClient,
    @Named("gemini") private val apiKey: String
) {
    suspend fun generateContent(prompt: String): String {
        val response: GeminiResponse = httpClient.post(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"
        ) {
            contentType(ContentType.Application.Json)
            setBody(GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = prompt))
                    )
                )
            ))
        }.body()
        
        return response.candidates.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text
            ?: throw Exception("Empty response from Gemini")
    }
}
