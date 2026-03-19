package com.najmi.corvus.domain.usecase

import android.util.Log
import com.najmi.corvus.data.repository.GoogleFactCheckRepository
import com.najmi.corvus.data.repository.TavilyRepository
import com.najmi.corvus.domain.model.CorvusResult
import com.najmi.corvus.domain.model.PipelineStep
import com.najmi.corvus.domain.model.Verdict
import com.najmi.corvus.domain.router.LlmRouter
import com.najmi.corvus.domain.router.LlmRouterException
import kotlinx.coroutines.delay
import javax.inject.Inject

class CorvusFactCheckUseCase @Inject constructor(
    private val googleFactCheckRepository: GoogleFactCheckRepository,
    private val tavilyRepository: TavilyRepository,
    private val llmRouter: LlmRouter
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
                return knownCheck.copy(claim = claim)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Fact Check failed: ${e.message}")
        }

        onStepChange(PipelineStep.RETRIEVING_SOURCES)
        delay(300)

        val articles = tavilyRepository.search(claim, maxResults = 3)
        Log.d(TAG, "Tavily returned ${articles.size} articles")
        
        if (articles.isEmpty()) {
            Log.d(TAG, "No sources found - returning UNVERIFIABLE")
            return CorvusResult(
                claim = claim,
                verdict = Verdict.UNVERIFIABLE,
                confidence = 0.3f,
                explanation = "Unable to find reliable sources. Check your Tavily API key and internet connection.",
                keyFacts = emptyList(),
                sources = emptyList()
            )
        }

        onStepChange(PipelineStep.ANALYZING)
        delay(300)

        return try {
            val (result, provider) = llmRouter.analyze(claim, articles)
            Log.d(TAG, "Analysis succeeded with $provider")
            result.copy(claim = claim, providerUsed = provider.name).also {
                onStepChange(PipelineStep.DONE)
            }
        } catch (e: LlmRouterException) {
            Log.e(TAG, "All providers exhausted: ${e.message}")
            CorvusResult(
                claim = claim,
                verdict = Verdict.UNVERIFIABLE,
                confidence = 0.2f,
                explanation = "All AI providers failed. Check your API keys and internet connection. Showing available sources below.",
                keyFacts = emptyList(),
                sources = articles.take(3),
                providerUsed = "none"
            ).also {
                onStepChange(PipelineStep.DONE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}")
            CorvusResult(
                claim = claim,
                verdict = Verdict.UNVERIFIABLE,
                confidence = 0.1f,
                explanation = "Analysis failed: ${e.message}",
                keyFacts = emptyList(),
                sources = articles.take(3),
                providerUsed = "error"
            ).also {
                onStepChange(PipelineStep.DONE)
            }
        }
    }
}
