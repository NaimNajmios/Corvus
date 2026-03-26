package com.najmi.corvus.domain.usecase

import com.najmi.corvus.data.remote.cohere.CohereModels
import com.najmi.corvus.domain.model.ClaimLanguage
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.ClassifiedClaim
import com.najmi.corvus.domain.model.HarmLevel
import com.najmi.corvus.domain.model.LlmProvider
import com.najmi.corvus.domain.router.LlmProviderHealthTracker
import javax.inject.Inject

data class ProviderAssignment(
    val actor: LlmProvider,
    val critic: LlmProvider,
    val rationale: String
)

class ActorCriticProviderSelector @Inject constructor(
    private val healthTracker: LlmProviderHealthTracker,
    private val cohereGuard: CohereQuotaGuard
) {
    suspend fun select(
        classified: ClassifiedClaim,
        harmLevel: HarmLevel = HarmLevel.NONE
    ): ProviderAssignment {
        val claimType = classified.type

        return when {
            claimType == ClaimType.QUOTE -> assignQuoteClaim()
            claimType == ClaimType.SCIENTIFIC -> assignScientificClaim()
            harmLevel == HarmLevel.HIGH -> assignHighHarmClaim()
            else -> assignGeneralClaim()
        }
    }

    suspend fun selectForLanguage(
        language: ClaimLanguage,
        claimType: ClaimType,
        harmLevel: HarmLevel = HarmLevel.NONE
    ): ProviderAssignment {
        return when {
            isBahasaMalaysia(language) -> assignBmLanguage(claimType, harmLevel)
            claimType == ClaimType.QUOTE -> assignQuoteClaim()
            claimType == ClaimType.SCIENTIFIC -> assignScientificClaim()
            harmLevel == HarmLevel.HIGH -> assignHighHarmClaim()
            else -> assignGeneralClaim()
        }
    }

    private suspend fun assignBmLanguage(
        claimType: ClaimType,
        harmLevel: HarmLevel
    ): ProviderAssignment {
        val sabaAvailable = healthTracker.isAvailable(LlmProvider.MISTRAL_SABA)
        val criticOk = healthTracker.isAvailable(LlmProvider.GEMINI)

        return ProviderAssignment(
            actor = if (sabaAvailable) LlmProvider.MISTRAL_SABA
                    else bestAvailableActor(exclude = emptySet()),
            critic = if (criticOk) LlmProvider.GEMINI
                     else bestAvailableCritic(exclude = setOf(LlmProvider.MISTRAL_SABA)),
            rationale = "Mistral-Saba selected for Bahasa Malaysia claim"
        )
    }

    private suspend fun assignQuoteClaim(): ProviderAssignment {
        val cohereAvailable = cohereGuard.canCall(CohereModels.COMMAND_R) &&
                healthTracker.isAvailable(LlmProvider.COHERE_R)

        return ProviderAssignment(
            actor = bestAvailableActor(exclude = setOf(
                LlmProvider.COHERE_R,
                LlmProvider.COHERE_R_PLUS
            )),
            critic = if (cohereAvailable) LlmProvider.COHERE_R
                     else bestAvailableCritic(exclude = setOf(
                         LlmProvider.COHERE_R,
                         LlmProvider.COHERE_R_PLUS
                     )),
            rationale = if (cohereAvailable)
                "Cohere command-r selected as Critic for quote citation verification"
            else
                "Cohere quota exhausted — using standard Critic fallback"
        )
    }

    private suspend fun assignScientificClaim(): ProviderAssignment {
        val cohereAvailable = cohereGuard.canCall(CohereModels.COMMAND_R) &&
                healthTracker.isAvailable(LlmProvider.COHERE_R)

        return ProviderAssignment(
            actor = if (cohereAvailable) LlmProvider.COHERE_R
                    else bestAvailableActor(exclude = setOf(
                        LlmProvider.COHERE_R,
                        LlmProvider.COHERE_R_PLUS
                    )),
            critic = bestAvailableCritic(exclude = setOf(
                LlmProvider.COHERE_R,
                LlmProvider.COHERE_R_PLUS
            )),
            rationale = if (cohereAvailable)
                "Cohere command-r selected as Actor for scientific claim grounding"
            else
                "Cohere quota exhausted — using standard Actor fallback"
        )
    }

    private suspend fun assignHighHarmClaim(): ProviderAssignment {
        val plusAvailable = cohereGuard.canCall(CohereModels.COMMAND_R_PLUS) &&
                healthTracker.isAvailable(LlmProvider.COHERE_R_PLUS)

        return ProviderAssignment(
            actor = bestAvailableActor(exclude = setOf(
                LlmProvider.COHERE_R,
                LlmProvider.COHERE_R_PLUS
            )),
            critic = if (plusAvailable) LlmProvider.COHERE_R_PLUS
                     else bestAvailableCritic(exclude = setOf(
                         LlmProvider.COHERE_R_PLUS
                     )),
            rationale = if (plusAvailable)
                "Cohere command-r-plus escalated for HIGH harm claim verification"
            else
                "command-r-plus quota exhausted — using best available Critic"
        )
    }

    private fun assignGeneralClaim(): ProviderAssignment {
        return ProviderAssignment(
            actor = bestAvailableActor(exclude = setOf(
                LlmProvider.COHERE_R,
                LlmProvider.COHERE_R_PLUS,
                LlmProvider.MISTRAL_SABA
            )),
            critic = bestAvailableCritic(exclude = setOf(
                LlmProvider.COHERE_R,
                LlmProvider.COHERE_R_PLUS
            )),
            rationale = "Standard routing — general English claim"
        )
    }

    private fun bestAvailableActor(exclude: Set<LlmProvider>): LlmProvider {
        val actorPreference = listOf(
            LlmProvider.GROQ,
            LlmProvider.CEREBRAS,
            LlmProvider.GEMINI,
            LlmProvider.MISTRAL_SMALL,
            LlmProvider.OPENROUTER,
            LlmProvider.MISTRAL_SABA
        )
        return actorPreference
            .filter { it !in exclude }
            .firstOrNull { healthTracker.isAvailable(it) }
            ?: LlmProvider.GEMINI
    }

    private fun bestAvailableCritic(exclude: Set<LlmProvider>): LlmProvider {
        val criticPreference = listOf(
            LlmProvider.GEMINI,
            LlmProvider.GROQ,
            LlmProvider.MISTRAL_SMALL,
            LlmProvider.CEREBRAS,
            LlmProvider.OPENROUTER
        )
        return criticPreference
            .filter { it !in exclude }
            .firstOrNull { healthTracker.isAvailable(it) }
            ?: LlmProvider.GROQ
    }

    private fun isBahasaMalaysia(language: ClaimLanguage) =
        language == ClaimLanguage.BAHASA_MALAYSIA ||
        language == ClaimLanguage.MIXED
}
