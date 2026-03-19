package com.najmi.corvus.domain.router

import android.util.Log
import com.najmi.corvus.data.repository.LlmProvider
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
    private val llmRepository: LlmRepository
) {
    companion object {
        private const val TAG = "LlmRouter"
        private val DEFAULT_PROVIDER_ORDER = listOf(
            LlmProvider.GEMINI,
            LlmProvider.GROQ,
            LlmProvider.CEREBRAS,
            LlmProvider.OPENROUTER
        )
    }

    suspend fun analyze(
        claim: String,
        sources: List<Source>,
        claimType: ClaimType = ClaimType.GENERAL,
        preferredProvider: LlmProvider? = null
    ): Pair<CorvusCheckResult.GeneralResult, LlmProvider> {
        val providers = buildProviderOrder(preferredProvider)
        
        Log.d(TAG, "Starting LLM analysis with provider order: ${providers.map { it.name }}")
        
        var lastError: Exception? = null
        
        for (provider in providers) {
            try {
                Log.d(TAG, "Attempting analysis with ${provider.name}")
                val result = llmRepository.analyze(claim, sources, provider, claimType)
                Log.d(TAG, "Success with ${provider.name}, verdict: ${result.verdict}")
                return result to provider
            } catch (e: Exception) {
                lastError = e
                val isRetryable = isRetryableError(e)
                Log.w(TAG, "${provider.name} failed: ${e.message}")
                
                if (isRetryable) {
                    continue
                }
                
                Log.d(TAG, "Non-retryable error, trying next provider")
            }
        }
        
        Log.e(TAG, "All providers exhausted, last error: ${lastError?.message}")
        throw LlmRouterException.AllProvidersExhausted
    }

    private fun buildProviderOrder(preferred: LlmProvider?): List<LlmProvider> {
        if (preferred == null) {
            return DEFAULT_PROVIDER_ORDER
        }
        
        return listOf(preferred) + DEFAULT_PROVIDER_ORDER.filter { it != preferred }
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