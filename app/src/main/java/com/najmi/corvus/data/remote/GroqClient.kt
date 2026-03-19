package com.najmi.corvus.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
    val choices: List<GroqChoice>
)

@Serializable
data class GroqChoice(
    val message: GroqMessageResponse
)

@Serializable
data class GroqMessageResponse(
    val content: String
)

@Singleton
class GroqClient @Inject constructor(
    private val httpClient: HttpClient,
    @Named("groq") private val apiKey: String
) {
    suspend fun chat(prompt: String): String {
        val response: GroqResponse = httpClient.post("https://api.groq.com/openai/v1/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(GroqRequest(
                messages = listOf(GroqMessage(role = "user", content = prompt))
            ))
        }.body()
        
        return response.choices.firstOrNull()?.message?.content
            ?: throw Exception("Empty response from Groq")
    }
}
