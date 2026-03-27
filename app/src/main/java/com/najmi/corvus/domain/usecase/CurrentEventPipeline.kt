package com.najmi.corvus.domain.usecase

import android.util.Log
import com.najmi.corvus.data.remote.GdeltClient
import com.najmi.corvus.data.repository.OutletRatingRepository
import com.najmi.corvus.data.repository.TavilyRepository
import com.najmi.corvus.domain.model.*
import com.najmi.corvus.domain.router.LlmRouter
import com.najmi.corvus.domain.util.*
import javax.inject.Inject

class CurrentEventPipeline @Inject constructor(
    private val gdeltClient: GdeltClient,
    private val tavilyRepository: TavilyRepository,
    private val llmRouter: LlmRouter,
    private val ratingRepo: OutletRatingRepository
) {
    companion object {
        private const val TAG = "EventPipeline"
    }

    suspend fun verify(classified: ClassifiedClaim): CorvusCheckResult.GeneralResult {
        Log.d(TAG, "Starting current event verification for: ${classified.raw}")
        
        val sources = mutableListOf<Source>()
        val steps = mutableListOf<com.najmi.corvus.domain.model.PipelineStepResult>()
        
        // Layer 1: GDELT (Global news tracking)
        steps.add(com.najmi.corvus.domain.model.PipelineStepResult(com.najmi.corvus.domain.model.PipelineStep.CHECKING_KNOWN_FACTS, "Searching GDELT global news archives"))
        try {
            val gdeltResults = gdeltClient.search(classified.raw)
            gdeltResults.take(3).forEach {
                sources.add(Source(
                    title = it.title,
                    url = it.url,
                    publisher = it.domain ?: "Unknown",
                    publishedDate = it.seendate,
                    sourceType = SourceType.NEWS_ARCHIVE
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "GDELT failed: ${e.message}")
        }

        // Layer 2: Web Search (Latest)
        try {
            steps.add(com.najmi.corvus.domain.model.PipelineStepResult(com.najmi.corvus.domain.model.PipelineStep.RETRIEVING_SOURCES, "Performing real-time web search for latest updates"))
            val webResults = tavilyRepository.search(classified.raw, maxResults = 5)
            sources.addAll(webResults)
        } catch (e: Exception) {
            Log.e(TAG, "Tavily failed: ${e.message}")
        }

        // Enrich with Bias/Credibility Ratings
        var enrichedSources = sources.map { it.copy(outletRating = ratingRepo.getRating(it.url, ClaimType.CURRENT_EVENT)) }
        
        // Runtime Signal Boosting
        enrichedSources = RuntimeCredibilityAdjuster.adjustForConsensus(enrichedSources, ClaimType.CURRENT_EVENT)

        // Source Quality Gate
        val filteredSources = enrichedSources.filter { 
            (it.outletRating?.credibility ?: 50) >= 40 
        }.sortedByDescending { it.outletRating?.credibility ?: 50 }

        // LLM Synthesis
        steps.add(com.najmi.corvus.domain.model.PipelineStepResult(com.najmi.corvus.domain.model.PipelineStep.ANALYZING, "Analyzing current event news clusters"))
        val (result, provider) = llmRouter.analyze(classified.raw, filteredSources, ClaimType.CURRENT_EVENT)
        val methodology = com.najmi.corvus.domain.model.MethodologyMetadata(
            pipelineStepsCompleted = steps + com.najmi.corvus.domain.model.PipelineStepResult(com.najmi.corvus.domain.model.PipelineStep.DONE, "Current event verification complete"),
            claimTypeDetected = com.najmi.corvus.domain.model.ClaimType.CURRENT_EVENT,
            sourcesRetrieved = enrichedSources.size,
            avgSourceCredibility = if (enrichedSources.isNotEmpty()) enrichedSources.map { it.outletRating?.credibility ?: 50 }.average().toInt() else 0,
            llmProviderUsed = provider.name,
            checkedAt = System.currentTimeMillis()
        )

        return result.copy(
            claim = classified.raw, 
            providerUsed = provider.name,
            sources = enrichedSources, // UI shows all
            methodology = methodology
        )
    }
}
