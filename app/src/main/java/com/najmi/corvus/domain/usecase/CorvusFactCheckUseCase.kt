package com.najmi.corvus.domain.usecase

import android.util.Log
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
    companion object {
        private const val TAG = "CorvusFactCheck"
    }

    suspend fun check(
        claim: String,
        onStepChange: (PipelineStep) -> Unit
    ): CorvusResult {
        Log.d(TAG, "Starting fact check for: $claim")
        
        onStepChange(PipelineStep.CHECKING_KNOWN_FACTS)
        delay(300)

        try {
            val knownCheck = googleFactCheckRepository.search(claim)
            if (knownCheck != null) {
                Log.d(TAG, "Found known fact check")
                onStepChange(PipelineStep.DONE)
                return knownCheck
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Fact Check failed: ${e.message}")
        }

        onStepChange(PipelineStep.RETRIEVING_SOURCES)
        delay(300)

        val articles = tavilyRepository.search(claim, maxResults = 5)
        Log.d(TAG, "Tavily returned ${articles.size} articles")
        
        if (articles.isEmpty()) {
            return CorvusResult(
                verdict = Verdict.UNVERIFIABLE,
                confidence = 0.3f,
                explanation = "Unable to find reliable sources to verify this claim. The claim may be too specific, too recent, or not covered by indexed sources. Check your API keys and internet connection.",
                keyFacts = emptyList(),
                sources = emptyList()
            )
        }

        onStepChange(PipelineStep.ANALYZING)
        delay(300)

        return try {
            llmRepository.analyze(claim, articles, LlmProvider.GEMINI)
        } catch (e: Exception) {
            Log.e(TAG, "Gemini failed: ${e.message}")
            try {
                llmRepository.analyze(claim, articles, LlmProvider.GROQ)
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Groq also failed: ${fallbackError.message}")
                CorvusResult(
                    verdict = Verdict.UNVERIFIABLE,
                    confidence = 0.2f,
                    explanation = "Analysis failed. Error: ${e.message}. Please check your API keys and try again.",
                    keyFacts = emptyList(),
                    sources = articles.take(3)
                )
            }
        }.also {
            onStepChange(PipelineStep.DONE)
        }
    }
}
