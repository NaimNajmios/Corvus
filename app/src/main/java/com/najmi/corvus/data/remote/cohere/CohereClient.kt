package com.najmi.corvus.data.remote.cohere

import android.util.Log
import com.najmi.corvus.data.local.DebugLogger
import com.najmi.corvus.data.remote.LlmClient
import com.najmi.corvus.data.remote.LlmResponse
import com.najmi.corvus.domain.model.LlmProvider
import com.najmi.corvus.domain.model.TokenUsage
import com.najmi.corvus.domain.usecase.CohereQuotaGuard
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

class CohereQuotaExceededException(
    message: String,
    val callsToday: Int
) : Exception(message)

@Singleton
class CohereClient @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    @Named("cohere") private val apiKey: String,
    private val quotaGuard: CohereQuotaGuard
) : LlmClient {
    
    override suspend fun chat(prompt: String): LlmResponse = chatR(prompt)
    companion object {
        private const val TAG = "CohereClient"
        private const val CHAT_URL = "https://api.cohere.com/v2/chat"
    }

    suspend fun chat(prompt: String, model: String = CohereModels.COMMAND_R): LlmResponse {
        val startTime = System.currentTimeMillis()
        
        if (!quotaGuard.canCall(model)) {
            val callsToday = quotaGuard.callsToday()
            throw CohereQuotaExceededException(
                "Cohere daily quota reached for model $model. Calls today: $callsToday",
                callsToday
            )
        }

        val response = httpClient.post(CHAT_URL) {
            headers.append("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(CohereRequest(
                model = model,
                message = prompt,
                maxTokens = 1500
            ))
        }

        val status = response.status
        val responseBody = response.bodyAsText()

        if (status != HttpStatusCode.OK) {
            Log.e(TAG, "Cohere error ($status): $responseBody")

            val errorMessage = try {
                val errorResponse = json.decodeFromString<CohereErrorResponse>(responseBody)
                errorResponse.error?.message ?: errorResponse.message ?: responseBody.take(200)
            } catch (e: Exception) {
                Log.w(TAG, "Could not parse error response: ${e.message}")
                responseBody.take(200)
            }

            throw Exception("Cohere error ($status): $errorMessage")
        }

        val cohereResponse: CohereResponse = try {
            json.decodeFromString<CohereResponse>(responseBody)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Cohere response: ${e.message}")
            Log.e(TAG, "Response body: ${responseBody.take(500)}")
            throw Exception("Invalid response from Cohere: ${e.message}")
        }

        quotaGuard.recordCall(model)
        
        val usage = TokenUsage(
            promptTokens = cohereResponse.meta?.tokens?.inputTokens ?: cohereResponse.meta?.billedUnits?.inputTokens ?: 0,
            completionTokens = cohereResponse.meta?.tokens?.outputTokens ?: cohereResponse.meta?.billedUnits?.outputTokens ?: 0,
            totalTokens = (cohereResponse.meta?.tokens?.inputTokens ?: 0) + (cohereResponse.meta?.tokens?.outputTokens ?: 0),
            provider = "COHERE",
            model = model
        )
        
        val latencyMs = System.currentTimeMillis() - startTime
        DebugLogger.llm("Cohere", model, usage.totalTokens, latencyMs)
        
        return LlmResponse(cohereResponse.text, usage)
    }

    suspend fun chatR(prompt: String): LlmResponse = chat(prompt, CohereModels.COMMAND_R)

    suspend fun chatRPlus(prompt: String): LlmResponse = chat(prompt, CohereModels.COMMAND_R_PLUS)

    fun getProvider(model: String): LlmProvider = when {
        model.contains("plus", ignoreCase = true) -> LlmProvider.COHERE_R_PLUS
        else -> LlmProvider.COHERE_R
    }
}
