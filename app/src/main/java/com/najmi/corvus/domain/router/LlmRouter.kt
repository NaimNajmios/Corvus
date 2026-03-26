package com.najmi.corvus.domain.router

import android.util.Log
import com.najmi.corvus.domain.model.LlmProvider
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.ClassifiedClaim
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.Source
import com.najmi.corvus.domain.usecase.ActorCriticPipeline
import javax.inject.Inject
import javax.inject.Singleton

sealed class LlmRouterException(message: String) : Exception(message) {
    object AllProvidersExhausted : LlmRouterException("All LLM providers failed")
}

@Singleton
class LlmRouter @Inject constructor(
    private val actorCriticPipeline: ActorCriticPipeline
) {
    companion object {
        private const val TAG = "LlmRouter"
    }

    suspend fun analyze(
        claim: String,
        sources: List<Source>,
        type: ClaimType
    ): Pair<CorvusCheckResult.GeneralResult, LlmProvider> {
        val classified = ClassifiedClaim(
            raw = claim,
            type = type,
            entities = emptyList()
        )

        Log.d(TAG, "Routing ${type.name} claim to Actor-Critic pipeline")
        
        val result = actorCriticPipeline.analyze(
            claim = claim,
            classified = classified,
            sources = sources
        )

        val provider = result.generalResult.criticProvider ?: LlmProvider.GEMINI
        return result.generalResult to provider
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
