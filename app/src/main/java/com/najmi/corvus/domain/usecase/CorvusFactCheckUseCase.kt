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

        val articles = tavilyRepository.search(claim, maxResults = 3)
        Log.d(TAG, "Tavily returned ${articles.size} articles")
        
        if (articles.isEmpty()) {
            Log.d(TAG, "No sources found - returning UNVERIFIABLE")
            return CorvusResult(
                verdict = Verdict.UNVERIFIABLE,
                confidence = 0.3f,
                explanation = "Unable to find reliable sources. Check your Tavily API key and internet connection.",
                keyFacts = emptyList(),
                sources = emptyList()
            )
        }

        onStepChange(PipelineStep.ANALYZING)
        delay(300)

        // Try Gemini first
        try {
            Log.d(TAG, "Attempting Gemini analysis...")
            return llmRepository.analyze(claim, articles, LlmProvider.GEMINI)
        } catch (geminiError: Exception) {
            Log.e(TAG, "Gemini failed: ${geminiError.message}")
            
            // Try Groq as fallback
            try {
                Log.d(TAG, "Attempting Groq fallback...")
                return llmRepository.analyze(claim, articles, LlmProvider.GROQ)
            } catch (groqError: Exception) {
                Log.e(TAG, "Groq also failed: ${groqError.message}")
                
                // Return partial result with sources
                return                 CorvusResult(
                    verdict = Verdict.UNVERIFIABLE,
                    confidence = 0.2f,
                    explanation = "AI analysis failed: ${geminiError.message}. " +
                                  "Fallback error: ${groqError.message}. " +
                                  "Showing available sources below.",
                    keyFacts = emptyList(),
                    sources = articles.take(3)
                ).also {
                    onStepChange(PipelineStep.DONE)
                }
            }
        }
    }
}
