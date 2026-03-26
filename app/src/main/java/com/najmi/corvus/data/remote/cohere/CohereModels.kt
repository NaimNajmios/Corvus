package com.najmi.corvus.data.remote.cohere

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object CohereModels {
    const val COMMAND_R = "command-r-08-2024"
    const val COMMAND_R_PLUS = "command-r-plus-08-2024"

    val CONTEXT_WINDOWS = mapOf(
        COMMAND_R to 128_000,
        COMMAND_R_PLUS to 128_000
    )
}

@Serializable
data class CohereRequest(
    val model: String,
    val message: String,
    @SerialName("max_tokens") val maxTokens: Int = 1000,
    val temperature: Float = 0.1f,
    @SerialName("chat_history") val chatHistory: List<CohereChatMessage> = emptyList()
)

@Serializable
data class CohereChatMessage(
    val role: String,
    val message: String
)

@Serializable
data class CohereResponse(
    val text: String,
    @SerialName("generation_id") val generationId: String? = null,
    val meta: CohereMeta? = null
)

@Serializable
data class CohereMeta(
    @SerialName("billed_units") val billedUnits: CohereBilledUnits? = null,
    val tokens: CohereTokens? = null
)

@Serializable
data class CohereBilledUnits(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0
)

@Serializable
data class CohereTokens(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0
)

@Serializable
data class CohereErrorResponse(
    val error: CohereError? = null,
    val message: String? = null
)

@Serializable
data class CohereError(
    val message: String,
    val type: String? = null,
    val code: String? = null
)
