package com.najmi.corvus.domain.usecase

import android.util.Log
import com.najmi.corvus.data.repository.GoogleFactCheckRepository
import com.najmi.corvus.data.repository.TavilyRepository
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.PipelineStep
import com.najmi.corvus.domain.model.Verdict
import com.najmi.corvus.domain.router.LlmRouter
import com.najmi.corvus.domain.router.LlmRouterException
import kotlinx.coroutines.delay
import javax.inject.Inject

class CorvusFactCheckUseCase @Inject constructor(
    private val googleFactCheckRepository: GoogleFactCheckRepository,
    private val tavilyRepository: TavilyRepository,
    private val llmRouter: LlmRouter,
    private val classifier: ClaimClassifierUseCase,
    private val quotePipeline: QuoteVerificationPipeline,
    private val generalPipeline: GeneralFactCheckPipeline,
    private val statisticalPipeline: StatisticalFactCheckPipeline,
    private val scientificPipeline: ScientificFactCheckPipeline,
    private val eventPipeline: CurrentEventPipeline
) {
    companion object {
        private const val TAG = "CorvusFactCheck"
    }

    suspend fun check(
        claim: String,
        onStepChange: (PipelineStep) -> Unit
    ): CorvusCheckResult {
        Log.d(TAG, "Starting fact check for: $claim")
        
        onStepChange(PipelineStep.CHECKING_KNOWN_FACTS)
        delay(300)

        val classified = classifier.classify(claim)
        Log.d(TAG, "Claim classified as: ${classified.type}")

        // For now, mapping old GoogleFactCheck return to GeneralResult
        try {
            val knownCheck = googleFactCheckRepository.search(claim)
            if (knownCheck != null) {
                Log.d(TAG, "Found known fact check")
                onStepChange(PipelineStep.DONE)
                return CorvusCheckResult.GeneralResult(
                    claim = claim,
                    verdict = knownCheck.verdict,
                    confidence = knownCheck.confidence,
                    explanation = knownCheck.explanation,
                    keyFacts = knownCheck.keyFacts,
                    sources = knownCheck.sources,
                    isFromKnownFactCheck = true,
                    claimType = classified.type
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Fact Check failed: ${e.message}")
        }

        onStepChange(PipelineStep.RETRIEVING_SOURCES)
        delay(300)

        if (classified.type == ClaimType.QUOTE) {
            return quotePipeline.verify(classified)
        }

        if (classified.type == ClaimType.STATISTICAL) {
            return statisticalPipeline.verify(classified)
        }

        if (classified.type == ClaimType.SCIENTIFIC) {
            return scientificPipeline.verify(classified)
        }

        if (classified.type == ClaimType.CURRENT_EVENT) {
            return eventPipeline.verify(classified)
        }

        // Targeted search based on type (simple for now)
        val searchQuery = when (classified.type) {
            ClaimType.QUOTE -> "${classified.speaker ?: ""} ${classified.quotedText ?: claim} quote verify"
            ClaimType.STATISTICAL -> "${claim} statistics Malaysia"
            else -> claim
        }

        val articles = tavilyRepository.search(searchQuery, maxResults = 3)
        Log.d(TAG, "Tavily returned ${articles.size} articles")
        
        if (articles.isEmpty()) {
            Log.d(TAG, "No sources found - returning UNVERIFIABLE")
            return CorvusCheckResult.GeneralResult(
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

        return generalPipeline.verify(classified).also {
            onStepChange(PipelineStep.DONE)
        }
    }
}
