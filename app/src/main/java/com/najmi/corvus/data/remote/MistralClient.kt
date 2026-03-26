package com.najmi.corvus.data.remote

import android.util.Log
import com.najmi.corvus.data.remote.LlmClient
import com.najmi.corvus.domain.usecase.MistralQuotaGuard
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
data class MistralRequest(
    val model: String,
    val messages: List<MistralMessage>,
    val temperature: Float = 0.3f,
    @SerialName("max_tokens") val maxTokens: Int = 1024
)

@Serializable
data class MistralMessage(
    val role: String,
    val content: String
)

@Serializable
data class MistralResponse(
    val choices: List<MistralChoice>,
    val usage: MistralUsage? = null
)

@Serializable
data class MistralChoice(
    val message: MistralMessageResponse
)

@Serializable
data class MistralMessageResponse(
    val content: String
)

@Serializable
data class MistralUsage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null
)

@Serializable
data class MistralErrorResponse(
    val error: MistralError
)

@Serializable
data class MistralError(
    val message: String,
    val type: String? = null,
    val code: String? = null
)

object MistralModels {
    const val SABA = "mistral-saba-latest"
    const val SMALL = "mistral-small-latest"

    val CONTEXT_WINDOWS = mapOf(
        SABA to 32_768,
        SMALL to 131_072
    )
}

class MistralQuotaExceededException(
    message: String,
    val callsToday: Int
) : Exception(message)

@Singleton
class MistralClient @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    @Named("mistral") private val apiKey: String,
    private val quotaGuard: MistralQuotaGuard
) : LlmClient {
    companion object {
        private const val TAG = "MistralClient"
        private const val BASE_URL = "https://api.mistral.ai/v1/chat/completions"
    }

    override suspend fun chat(prompt: String): String = chat(prompt, MistralModels.SMALL)
 
    suspend fun chat(prompt: String, model: String = MistralModels.SMALL): String {
        if (!quotaGuard.canCall()) {
            val callsToday = quotaGuard.callsToday()
            throw MistralQuotaExceededException(
                "Mistral daily quota reached. Calls today: $callsToday",
                callsToday
            )
        }

        Log.d(TAG, "Sending request to Mistral ($model), prompt length: ${prompt.length}")

        val response = httpClient.post(BASE_URL) {
            headers.append("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(MistralRequest(
                model = model,
                messages = listOf(MistralMessage(role = "user", content = prompt))
            ))
        }

        val status = response.status
        val responseBody = response.bodyAsText()

        if (status != HttpStatusCode.OK) {
            Log.e(TAG, "Mistral error ($status): $responseBody")

            val errorMessage = try {
                val errorResponse = json.decodeFromString<MistralErrorResponse>(responseBody)
                errorResponse.error.message
            } catch (e: Exception) {
                Log.w(TAG, "Could not parse error response: ${e.message}")
                responseBody.take(200)
            }

            throw Exception("Mistral error ($status): $errorMessage")
        }

        val mistralResponse: MistralResponse = try {
            json.decodeFromString<MistralResponse>(responseBody)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Mistral response: ${e.message}")
            Log.e(TAG, "Response body: ${responseBody.take(500)}")
            throw Exception("Invalid response from Mistral: ${e.message}")
        }

        val text = mistralResponse.choices.firstOrNull()?.message?.content
            ?: throw Exception("Empty response from Mistral")

        val usage = com.najmi.corvus.domain.model.TokenUsage(
            promptTokens = mistralResponse.usage?.promptTokens ?: 0,
            completionTokens = mistralResponse.usage?.completionTokens ?: 0,
            totalTokens = mistralResponse.usage?.totalTokens ?: 0,
            provider = "MISTRAL",
            model = model
        )

        Log.d(TAG, "Received response, tokens: ${usage.totalTokens}")

        quotaGuard.recordCall()

        return text
    }
  
    suspend fun chatSaba(prompt: String): String = chat(prompt, MistralModels.SABA)
 
    suspend fun chatSmall(prompt: String): String = chat(prompt, MistralModels.SMALL)
}
