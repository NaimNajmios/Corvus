package com.najmi.corvus.domain.util

import com.najmi.corvus.domain.model.TokenUsage
import com.najmi.corvus.domain.model.TokenStep

object TokenCounter {

    /**
     * Estimates tokens based on average characters per token (4 chars per token).
     * Used for providers that don't return usage data or for internal pre-screening.
     */
    fun estimate(text: String, provider: String, step: TokenStep = TokenStep.GENERAL): TokenUsage {
        if (text.isBlank()) return TokenUsage.EMPTY.copy(provider = provider, step = step)
        
        val count = (text.length / 4).coerceAtLeast(1)
        return TokenUsage(
            promptTokens = count,
            completionTokens = 0, // usually we estimate prompt before sending
            totalTokens = count,
            provider = provider,
            step = step
        )
    }

    /**
     * Combines multiple usage reports into one.
     */
    fun combine(usages: List<TokenUsage>): TokenUsage {
        if (usages.isEmpty()) return TokenUsage.EMPTY
        
        return TokenUsage(
            promptTokens = usages.sumOf { it.promptTokens },
            completionTokens = usages.sumOf { it.completionTokens },
            totalTokens = usages.sumOf { it.totalTokens },
            provider = "Aggregated",
            step = TokenStep.GENERAL
        )
    }
}
