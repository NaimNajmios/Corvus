package com.najmi.corvus.data.remote

import android.util.Log
import com.najmi.corvus.data.remote.LlmClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
    val candidates: List<GeminiCandidate> = emptyList(),
    @SerialName("promptFeedback") val promptFeedback: PromptFeedback? = null
)

@Serializable
data class PromptFeedback(
    @SerialName("blockReason") val blockReason: String? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContentResponse,
    @SerialName("finishReason") val finishReason: String? = null
)

@Serializable
data class GeminiContentResponse(
    val parts: List<GeminiPartResponse>
)

@Serializable
data class GeminiPartResponse(
    val text: String
)

@Serializable
data class GeminiErrorResponse(
    val error: GeminiError
)

@Serializable
data class GeminiError(
    val code: String,
    val message: String,
    val status: String
)

@Singleton
class GeminiClient @Inject constructor(
    private val httpClient: HttpClient,
    @Named("gemini") private val apiKey: String
) : LlmClient {
    companion object {
        private const val TAG = "GeminiClient"
    }

    override suspend fun chat(prompt: String): String {
        Log.d(TAG, "Sending request to Gemini, prompt length: ${prompt.length}")
        
        val response = httpClient.post(
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
        }

        if (response.status != HttpStatusCode.OK) {
            val errorBody = response.bodyAsText()
            Log.e(TAG, "Gemini error response: $errorBody")
            
            val errorMessage = try {
                kotlinx.serialization.json.Json.decodeFromString<GeminiErrorResponse>(errorBody).error.message
            } catch (e: Exception) {
                errorBody
            }
            throw Exception("Gemini error (${response.status}): $errorMessage")
        }

        val geminiResponse: GeminiResponse = response.body()

        if (geminiResponse.candidates.isEmpty()) {
            val blockReason = geminiResponse.promptFeedback?.blockReason
            Log.e(TAG, "Empty candidates. Block reason: $blockReason")
            throw Exception("Response blocked or empty. Reason: ${blockReason ?: "Unknown"}")
        }

        val candidate = geminiResponse.candidates.first()
        
        when (candidate.finishReason) {
            "MAX_TOKENS" -> Log.w(TAG, "Response truncated - hit max tokens")
            "SAFETY" -> throw Exception("Response blocked by safety filters")
            "RECITATION" -> throw Exception("Response blocked due to recitation")
            null, "STOP" -> {}
            else -> Log.w(TAG, "Finish reason: ${candidate.finishReason}")
        }

        val text = candidate.content.parts.firstOrNull()?.text
        if (text.isNullOrBlank()) {
            Log.e(TAG, "Empty text in response")
            throw Exception("Empty response from Gemini")
        }

        Log.d(TAG, "Received response, length: ${text.length}")
        return text
    }
}
