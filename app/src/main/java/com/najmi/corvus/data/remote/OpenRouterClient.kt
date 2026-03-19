package com.najmi.corvus.data.remote

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
data class OpenRouterRequest(
    val model: String = "google/gemini-2.0-flash-exp:free",
    val messages: List<OpenRouterMessage>,
    val temperature: Float = 0.3f,
    @SerialName("max_tokens") val maxTokens: Int = 1024
)

@Serializable
data class OpenRouterMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenRouterResponse(
    val choices: List<OpenRouterChoice>
)

@Serializable
data class OpenRouterChoice(
    val message: OpenRouterMessageResponse
)

@Serializable
data class OpenRouterMessageResponse(
    val content: String
)

@Serializable
data class OpenRouterErrorResponse(
    val error: OpenRouterError
)

@Serializable
data class OpenRouterError(
    val message: String,
    val type: String? = null,
    val code: String? = null
)

@Singleton
class OpenRouterClient @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    @Named("openrouter") private val apiKey: String
) {
    companion object {
        private const val TAG = "OpenRouterClient"
    }

    suspend fun chat(prompt: String): String {
        Log.d(TAG, "API Key (first 10 chars): ${apiKey.take(10)}...")
        
        val response = httpClient.post("https://openrouter.ai/api/v1/chat/completions") {
            headers.append("Authorization", "Bearer $apiKey")
            headers.append("HTTP-Referer", "https://github.com/najmicoder/corvus")
            headers.append("X-Title", "Corvus")
            contentType(ContentType.Application.Json)
            setBody(OpenRouterRequest(
                messages = listOf(OpenRouterMessage(role = "user", content = prompt))
            ))
        }
        
        val status = response.status
        val responseBody = response.bodyAsText()
        
        if (status != HttpStatusCode.OK) {
            Log.e(TAG, "OpenRouter error ($status): $responseBody")
            
            val errorMessage = try {
                val errorResponse = json.decodeFromString<OpenRouterErrorResponse>(responseBody)
                errorResponse.error.message
            } catch (e: Exception) {
                Log.w(TAG, "Could not parse error response: ${e.message}")
                responseBody.take(200)
            }
            
            throw Exception("OpenRouter error ($status): $errorMessage")
        }
        
        val openRouterResponse: OpenRouterResponse = try {
            json.decodeFromString<OpenRouterResponse>(responseBody)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse OpenRouter response: ${e.message}")
            Log.e(TAG, "Response body: ${responseBody.take(500)}")
            throw Exception("Invalid response from OpenRouter: ${e.message}")
        }
        
        return openRouterResponse.choices.firstOrNull()?.message?.content
            ?: throw Exception("Empty response from OpenRouter")
    }
}