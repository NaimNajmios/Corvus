package com.najmi.corvus.domain.usecase

import android.util.Log
import com.najmi.corvus.data.remote.GdeltClient
import com.najmi.corvus.data.repository.OutletRatingRepository
import com.najmi.corvus.data.repository.TavilyRepository
import com.najmi.corvus.domain.model.ClaimLanguage
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.ClassifiedClaim
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.Source
import com.najmi.corvus.domain.model.SourceType
import com.najmi.corvus.domain.router.LlmRouter
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

        // Layer 1: GDELT (Global news tracking)
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
            val webResults = tavilyRepository.search(classified.raw, maxResults = 5)
            sources.addAll(webResults)
        } catch (e: Exception) {
            Log.e(TAG, "Tavily failed: ${e.message}")
        }

        // Enrich with Bias/Credibility Ratings
        val enrichedSources = sources.map { it.copy(outletRating = ratingRepo.getRating(it.url)) }

        // Source Quality Gate
        val filteredSources = enrichedSources.filter { 
            (it.outletRating?.credibility ?: 50) >= 40 
        }.sortedByDescending { it.outletRating?.credibility ?: 50 }

        // LLM Synthesis
        val (result, provider) = llmRouter.analyze(classified.raw, filteredSources, ClaimType.CURRENT_EVENT)
        return result.copy(
            claim = classified.raw, 
            providerUsed = provider.name,
            sources = enrichedSources // UI shows all
        )
    }
}
