package com.najmi.corvus.domain.usecase

import com.najmi.corvus.data.repository.GoogleFactCheckRepository
import com.najmi.corvus.data.repository.LlmProvider
import com.najmi.corvus.data.repository.LlmRepository
import com.najmi.corvus.data.repository.TavilyRepository
import com.najmi.corvus.domain.model.CorvusResult
import com.najmi.corvus.domain.model.PipelineStep
import com.najmi.corvus.domain.model.Verdict
import kotlinx.coroutines.delay
import javax.inject.Inject

class CorvusFactCheckUseCase @Inject constructor(
    private val googleFactCheckRepository: GoogleFactCheckRepository,
    private val tavilyRepository: TavilyRepository,
    private val llmRepository: LlmRepository
) {
    suspend fun check(
        claim: String,
        onStepChange: (PipelineStep) -> Unit
    ): CorvusResult {
        onStepChange(PipelineStep.CHECKING_KNOWN_FACTS)
        delay(300)

        val knownCheck = googleFactCheckRepository.search(claim)
        if (knownCheck != null) {
            onStepChange(PipelineStep.DONE)
            return knownCheck
        }

        onStepChange(PipelineStep.RETRIEVING_SOURCES)
        delay(300)

        val articles = tavilyRepository.search(claim, maxResults = 5)
        if (articles.isEmpty()) {
            return CorvusResult(
                verdict = Verdict.UNVERIFIABLE,
                confidence = 0.3f,
                explanation = "Unable to find reliable sources to verify this claim. The claim may be too specific, too recent, or not covered by indexed sources.",
                keyFacts = emptyList(),
                sources = emptyList()
            )
        }

        onStepChange(PipelineStep.ANALYZING)
        delay(300)

        return try {
            llmRepository.analyze(claim, articles, LlmProvider.GEMINI)
        } catch (e: Exception) {
            try {
                llmRepository.analyze(claim, articles, LlmProvider.GROQ)
            } catch (fallbackError: Exception) {
                CorvusResult(
                    verdict = Verdict.UNVERIFIABLE,
                    confidence = 0.2f,
                    explanation = "Analysis failed. Both primary and fallback AI services encountered errors: ${e.message}. Please try again later.",
                    keyFacts = emptyList(),
                    sources = articles.take(3)
                )
            }
        }.also {
            onStepChange(PipelineStep.DONE)
        }
    }
}
