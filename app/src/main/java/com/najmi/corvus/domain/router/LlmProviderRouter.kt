package com.najmi.corvus.domain.router

import android.util.Log
import com.najmi.corvus.data.remote.LlmClient
import com.najmi.corvus.domain.model.LlmProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmProviderRouter @Inject constructor(
    private val clients: Map<LlmProvider, @JvmSuppressWildcards LlmClient>,
    private val healthTracker: LlmProviderHealthTracker
) {
    companion object {
        private const val TAG = "LlmProviderRouter"
    }

    /**
     * Executes a prompt using the preferred provider, with automatic fallback to
     * healthy alternatives if the preferred one fails or is unhealthy.
     */
    suspend fun execute(
        prompt: String,
        preferredProvider: LlmProvider = LlmProvider.GROQ
    ): String {
        // 1. Try preferred provider if healthy
        if (healthTracker.isAvailable(preferredProvider)) {
            try {
                return executeWithProvider(preferredProvider, prompt)
            } catch (e: Exception) {
                Log.w(TAG, "Preferred provider ${preferredProvider.name} failed: ${e.message}")
                healthTracker.reportError(preferredProvider.name)
                // Continue to fallback
            }
        } else {
            Log.w(TAG, "Preferred provider ${preferredProvider.name} is unhealthy, skipping to fallback")
        }

        // 2. Fallback to Gemini (typically highest quota/reliability)
        if (preferredProvider != LlmProvider.GEMINI && healthTracker.isAvailable(LlmProvider.GEMINI)) {
            try {
                Log.i(TAG, "Falling back to GEMINI for prompt resilience")
                return executeWithProvider(LlmProvider.GEMINI, prompt)
            } catch (e: Exception) {
                Log.w(TAG, "Fallback GEMINI failed: ${e.message}")
                healthTracker.reportError(LlmProvider.GEMINI.name)
            }
        }

        // 3. Iterate through all other healthy providers as last resort
        val fallbackChain = LlmProvider.values()
            .filter { it != preferredProvider && it != LlmProvider.GEMINI }
            .filter { healthTracker.isAvailable(it) }

        for (provider in fallbackChain) {
            try {
                Log.i(TAG, "Trying last-resort fallback: ${provider.name}")
                return executeWithProvider(provider, prompt)
            } catch (e: Exception) {
                Log.w(TAG, "Last-resort fallback ${provider.name} failed: ${e.message}")
                healthTracker.reportError(provider.name)
            }
        }

        throw Exception("All LLM providers failed or are unhealthy. Pipeline stalled.")
    }

    private suspend fun executeWithProvider(provider: LlmProvider, prompt: String): String {
        val client = clients[provider] ?: throw Exception("No client implementation found for ${provider.name}")
        return client.chat(prompt)
    }
}
