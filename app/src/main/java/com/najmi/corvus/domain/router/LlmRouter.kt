package com.najmi.corvus.domain.router

import android.util.Log
import com.najmi.corvus.domain.model.LlmProvider
import com.najmi.corvus.data.repository.LlmRepository
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.Source
import javax.inject.Inject
import javax.inject.Singleton

sealed class LlmRouterException(message: String) : Exception(message) {
    object AllProvidersExhausted : LlmRouterException("All LLM providers failed")
}

@Singleton
class LlmRouter @Inject constructor(
    private val llmRepository: LlmRepository,
    private val healthTracker: LlmProviderHealthTracker
) {
    companion object {
        private const val TAG = "LlmRouter"
    }

    suspend fun analyze(
        claim: String,
        sources: List<Source>,
        type: ClaimType
    ): Pair<CorvusCheckResult.GeneralResult, LlmProvider> {
        
        // ── Step 1: Determine Provider Priority ──────────────────────────
        val priorityList = when (type) {
            ClaimType.SCIENTIFIC, ClaimType.STATISTICAL -> listOf(
                LlmProvider.GEMINI,
                LlmProvider.OPENROUTER, // Often has DeepSeek V3/R1
                LlmProvider.GROQ
            )
            else -> listOf(
                LlmProvider.GEMINI,
                LlmProvider.GROQ,
                LlmProvider.CEREBRAS
            )
        }

        // ── Step 2: Cascade through healthy providers ────────────────────
        var lastError: Exception? = null

        for (provider in priorityList) {
            if (!healthTracker.isHealthy(provider.name)) {
                Log.w(TAG, "Skipping unhealthy provider: ${provider.name}")
                continue
            }

            try {
                Log.d(TAG, "Attempting analysis with ${provider.name}")
                val result = llmRepository.analyze(claim, sources, provider, type)
                
                // Success — reset health
                healthTracker.reset(provider.name)
                return result to provider

            } catch (e: Exception) {
                lastError = e
                healthTracker.reportError(provider.name)
                Log.e(TAG, "Provider ${provider.name} failed: ${e.message}")
                
                // If the error is about rate limits or service unavailable, continue to next provider
                if (isRetryableError(e)) {
                    continue
                } else {
                    // For structural errors (e.g. prompt too long), we might want to stop, 
                    // but usually safe to try another provider who might handle it differently.
                    continue 
                }
            }
        }

        // ── Step 3: Final fallback to primary if all "unhealthy" ─────────
        Log.e(TAG, "All preferred providers unhealthy/failed. Last error: ${lastError?.message}")
        throw LlmRouterException.AllProvidersExhausted
    }

    private fun isRetryableError(e: Exception): Boolean {
        val message = e.message ?: return false
        return message.contains("429", ignoreCase = true) ||
               message.contains("rate", ignoreCase = true) ||
               message.contains("quota", ignoreCase = true) ||
               message.contains("timeout", ignoreCase = true) ||
               message.contains("503", ignoreCase = true) ||
               message.contains("unavailable", ignoreCase = true)
    }
}
