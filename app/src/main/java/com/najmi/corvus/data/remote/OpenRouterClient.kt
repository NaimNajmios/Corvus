package com.najmi.corvus.data.remote

import android.util.Log
import com.najmi.corvus.data.local.DebugLogger
import com.najmi.corvus.data.remote.LlmClient
import com.najmi.corvus.data.remote.LlmResponse
import com.najmi.corvus.domain.model.TokenUsage
import com.najmi.corvus.domain.usecase.OpenRouterQuotaGuard
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
    val model: String = "qwen/qwen3.6-plus-preview",
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
    val choices: List<OpenRouterChoice>,
    val usage: OpenRouterUsage? = null,
    val model: String? = null
)

@Serializable
data class OpenRouterUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
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

class OpenRouterQuotaExceededException(
    message: String,
    val callsToday: Int
) : Exception(message)

@Singleton
class OpenRouterClient @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    @Named("openrouter") private val apiKey: String,
    private val quotaGuard: OpenRouterQuotaGuard
) : LlmClient {
    companion object {
        private const val TAG = "OpenRouterClient"
    }

    override suspend fun chat(prompt: String): LlmResponse {
        val startTime = System.currentTimeMillis()
        
        if (!quotaGuard.canCall()) {
            val callsToday = quotaGuard.callsToday()
            throw OpenRouterQuotaExceededException(
                "OpenRouter daily quota reached. Calls today: $callsToday",
                callsToday
            )
        }

        Log.d(TAG, "Sending request to OpenRouter, prompt length: ${prompt.length}")
        
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
        
        val text = openRouterResponse.choices.firstOrNull()?.message?.content
            ?: throw Exception("Empty response from OpenRouter")

        val usage = TokenUsage(
            promptTokens = openRouterResponse.usage?.promptTokens ?: 0,
            completionTokens = openRouterResponse.usage?.completionTokens ?: 0,
            totalTokens = openRouterResponse.usage?.totalTokens ?: 0,
            provider = "OPENROUTER",
            model = openRouterResponse.model
        )

        val latencyMs = System.currentTimeMillis() - startTime
        Log.d(TAG, "Received response, tokens: ${usage.totalTokens}")
        
        DebugLogger.llm("OpenRouter", openRouterResponse.model ?: "unknown", usage.totalTokens, latencyMs)

        quotaGuard.recordCall()

        return LlmResponse(text, usage)
    }
}