package com.najmi.corvus.data.remote

import com.najmi.corvus.domain.model.TokenUsage

/**
 * A wrapper for LLM responses that includes both the raw output text
 * and the token usage metadata from the provider.
 */
data class LlmResponse(
    val text: String,
    val usage: TokenUsage
)
