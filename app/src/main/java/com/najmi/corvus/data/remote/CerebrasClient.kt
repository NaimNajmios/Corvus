package com.najmi.corvus.data.remote

import android.util.Log
import com.najmi.corvus.data.remote.LlmClient
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Serializable
data class CerebrasRequest(
    val model: String = "llama3.3-70b",
    val messages: List<CerebrasMessage>,
    val temperature: Float = 0.3f,
    @SerialName("max_tokens") val maxTokens: Int = 1024
)

@Serializable
data class CerebrasMessage(
    val role: String,
    val content: String
)

@Serializable
data class CerebrasResponse(
    val choices: List<CerebrasChoice>
)

@Serializable
data class CerebrasChoice(
    val message: CerebrasMessageResponse
)

@Serializable
data class CerebrasMessageResponse(
    val content: String
)

@Serializable
data class CerebrasErrorResponse(
    val error: CerebrasError
)

@Serializable
data class CerebrasError(
    val message: String,
    val type: String? = null,
    val code: String? = null
)

@Singleton
class CerebrasClient @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    @Named("cerebras") private val apiKey: String
) : LlmClient {
    companion object {
        private const val TAG = "CerebrasClient"
    }

    override suspend fun chat(prompt: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "API Key (first 10 chars): ${apiKey.take(10)}...")
        
        val response = httpClient.post("https://api.cerebras.ai/v1/chat/completions") {
            headers.append("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(CerebrasRequest(
                messages = listOf(CerebrasMessage(role = "user", content = prompt))
            ))
        }
        
        val status = response.status
        val responseBody = response.bodyAsText()
        
        if (status != HttpStatusCode.OK) {
            Log.e(TAG, "Cerebras error ($status): $responseBody")
            
            val errorMessage = try {
                val errorResponse = json.decodeFromString<CerebrasErrorResponse>(responseBody)
                errorResponse.error.message
            } catch (e: Exception) {
                Log.w(TAG, "Could not parse error response: ${e.message}")
                responseBody.take(200)
            }
            
            throw Exception("Cerebras error ($status): $errorMessage")
        }
        
        val cerebrasResponse: CerebrasResponse = try {
            json.decodeFromString<CerebrasResponse>(responseBody)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Cerebras response: ${e.message}")
            Log.e(TAG, "Response body: ${responseBody.take(500)}")
            throw Exception("Invalid response from Cerebras: ${e.message}")
        }
        
        cerebrasResponse.choices.firstOrNull()?.message?.content
            ?: throw Exception("Empty response from Cerebras")
    }
}