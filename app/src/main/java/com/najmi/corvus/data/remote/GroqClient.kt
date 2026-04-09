package com.najmi.corvus.data.remote

import android.util.Log
import com.najmi.corvus.data.local.DebugLogger
import com.najmi.corvus.data.remote.LlmClient
import com.najmi.corvus.data.remote.LlmResponse
import com.najmi.corvus.domain.model.TokenUsage
import com.najmi.corvus.domain.usecase.GroqQuotaGuard
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
data class GroqRequest(
    val model: String = "llama-3.3-70b-versatile",
    val messages: List<GroqMessage>,
    val temperature: Float = 0.3f,
    @SerialName("max_tokens") val maxTokens: Int = 1024
)

@Serializable
data class GroqMessage(
    val role: String,
    val content: String
)

@Serializable
data class GroqResponse(
    val choices: List<GroqChoice>,
    val usage: GroqUsage? = null,
    val model: String? = null
)

@Serializable
data class GroqUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

@Serializable
data class GroqChoice(
    val message: GroqMessageResponse
)

@Serializable
data class GroqMessageResponse(
    val content: String
)

@Serializable
data class GroqErrorResponse(
    val error: GroqError
)

@Serializable
data class GroqError(
    val message: String,
    val type: String? = null,
    val code: String? = null
)

class GroqQuotaExceededException(
    message: String,
    val callsToday: Int
) : Exception(message)

@Singleton
class GroqClient @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    @Named("groq") private val apiKey: String,
    private val quotaGuard: GroqQuotaGuard
) : LlmClient {
    companion object {
        private const val TAG = "GroqClient"
    }

    override suspend fun chat(prompt: String): LlmResponse {
        val startTime = System.currentTimeMillis()
        
        if (!quotaGuard.canCall()) {
            val callsToday = quotaGuard.callsToday()
            throw GroqQuotaExceededException(
                "Groq daily quota reached. Calls today: $callsToday",
                callsToday
            )
        }

        Log.d(TAG, "Sending request to Groq, prompt length: ${prompt.length}")
        
        val response = httpClient.post("https://api.groq.com/openai/v1/chat/completions") {
            headers.append("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(GroqRequest(
                messages = listOf(GroqMessage(role = "user", content = prompt))
            ))
        }
        
        val status = response.status
        val responseBody = response.bodyAsText()
        
        if (status != HttpStatusCode.OK) {
            Log.e(TAG, "Groq error ($status): $responseBody")
            
            val errorMessage = try {
                val errorResponse = json.decodeFromString<GroqErrorResponse>(responseBody)
                errorResponse.error.message
            } catch (e: Exception) {
                Log.w(TAG, "Could not parse error response: ${e.message}")
                responseBody.take(200)
            }
            
            throw Exception("Groq error ($status): $errorMessage")
        }
        
        val groqResponse: GroqResponse = try {
            json.decodeFromString<GroqResponse>(responseBody)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Groq response: ${e.message}")
            Log.e(TAG, "Response body: ${responseBody.take(500)}")
            throw Exception("Invalid response from Groq: ${e.message}")
        }
        
        val text = groqResponse.choices.firstOrNull()?.message?.content
            ?: throw Exception("Empty response from Groq")

        val usage = TokenUsage(
            promptTokens = groqResponse.usage?.promptTokens ?: 0,
            completionTokens = groqResponse.usage?.completionTokens ?: 0,
            totalTokens = groqResponse.usage?.totalTokens ?: 0,
            provider = "GROQ",
            model = groqResponse.model
        )

        val latencyMs = System.currentTimeMillis() - startTime
        Log.d(TAG, "Received response, tokens: ${usage.totalTokens}")
        
        DebugLogger.llm("Groq", groqResponse.model ?: "unknown", usage.totalTokens, latencyMs)

        quotaGuard.recordCall()

        return LlmResponse(text, usage)
    }
}
