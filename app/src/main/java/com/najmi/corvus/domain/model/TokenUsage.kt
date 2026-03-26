package com.najmi.corvus.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val provider: String,
    val model: String? = null,
    val step: TokenStep = TokenStep.GENERAL
) {
    companion object {
        val EMPTY = TokenUsage(0, 0, 0, "unknown")
    }
}

@Serializable
enum class TokenStep {
    CLASSIFICATION,
    QUERY_REWRITING,
    DECOMPOSITION,
    ACTOR_PASS,
    CRITIC_PASS,
    GROUNDING_VERIFICATION,
    RAG_VERIFICATION,
    PLAUSIBILITY,
    GENERAL
}

@Serializable
data class CheckTokenReport(
    val totalPrompt: Int,
    val totalCompletion: Int,
    val totalCombined: Int,
    val breakdown: List<TokenUsage>
)
