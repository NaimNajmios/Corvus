package com.najmi.corvus.domain.router

import android.util.Log
import com.najmi.corvus.data.remote.LlmClient
import com.najmi.corvus.data.remote.LlmResponse
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

    suspend fun execute(
        prompt: String,
        preferredProvider: LlmProvider = LlmProvider.GROQ
    ): LlmResponse {
        // 1. Try preferred provider if healthy
        val primaryClient = clients[preferredProvider]
        if (primaryClient != null && healthTracker.isAvailable(preferredProvider)) {
            try {
                return primaryClient.chat(prompt)
            } catch (e: Exception) {
                Log.e(TAG, "Primary provider ($preferredProvider) failed: ${e.message}")
                healthTracker.reportError(preferredProvider.name)
                // Continue to fallback
            }
        } else {
            Log.w(TAG, "Preferred provider ${preferredProvider.name} is unhealthy or missing, skipping to fallback")
        }

        // 2. Fallback to Gemini (typically highest quota/reliability)
        if (preferredProvider != LlmProvider.GEMINI) {
            val geminiClient = clients[LlmProvider.GEMINI]
            if (geminiClient != null && healthTracker.isAvailable(LlmProvider.GEMINI)) {
                try {
                    Log.i(TAG, "Falling back to GEMINI for prompt resilience")
                    return geminiClient.chat(prompt)
                } catch (e: Exception) {
                    Log.e(TAG, "Fallback GEMINI failed: ${e.message}")
                    healthTracker.reportError(LlmProvider.GEMINI.name)
                }
            } else {
                Log.w(TAG, "Fallback provider GEMINI is unhealthy or missing, skipping to next fallback")
            }
        }

        // 3. Iterate through all other healthy providers as last resort
        val fallbacks = LlmProvider.values()
            .filter { it != preferredProvider && it != LlmProvider.GEMINI }

        for (provider in fallbacks) {
            val client = clients[provider]
            if (client != null && healthTracker.isAvailable(provider)) {
                try {
                    Log.i(TAG, "Trying last-resort fallback: ${provider.name}")
                    return client.chat(prompt)
                } catch (e: Exception) {
                    Log.e(TAG, "Last-resort fallback ${provider.name} failed: ${e.message}")
                    healthTracker.reportError(provider.name)
                }
            } else {
                Log.w(TAG, "Last-resort fallback provider ${provider.name} is unhealthy or missing, skipping")
            }
        }

        throw Exception("All LLM providers failed or are unhealthy. Pipeline stalled.")
    }
}
