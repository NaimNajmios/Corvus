package com.najmi.corvus.domain.usecase

import android.util.Log
import com.najmi.corvus.data.remote.DosmClient
import com.najmi.corvus.data.remote.WorldBankClient
import com.najmi.corvus.data.repository.TavilyRepository
import com.najmi.corvus.data.repository.OutletRatingRepository
import com.najmi.corvus.domain.model.ClaimLanguage
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.ClassifiedClaim
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.CredibilityTier
import com.najmi.corvus.domain.model.Source
import com.najmi.corvus.domain.model.SourceType
import com.najmi.corvus.domain.router.LlmRouter
import javax.inject.Inject

class StatisticalFactCheckPipeline @Inject constructor(
    private val dosmClient: DosmClient,
    private val worldBankClient: WorldBankClient,
    private val tavilyRepository: TavilyRepository,
    private val llmRouter: LlmRouter,
    private val ratingRepo: OutletRatingRepository
) {
    companion object {
        private const val TAG = "StatsPipeline"
    }

    suspend fun verify(classified: ClassifiedClaim): CorvusCheckResult.GeneralResult {
        Log.d(TAG, "Starting statistical verification for: ${classified.raw}")
        
        val sources = mutableListOf<Source>()

        // Layer 1: DOSM (Malaysian Government Data)
        try {
            val dosmResults = dosmClient.search(classified.raw)
            dosmResults.take(3).forEach {
                sources.add(Source(
                    title = it.label ?: "DOSM Dataset",
                    url = "https://data.gov.my/data-catalogue/${it.id}",
                    publisher = "Department of Statistics Malaysia",
                    snippet = it.description,
                    sourceType = SourceType.GOVERNMENT_DATA,
                    credibilityTier = CredibilityTier.PRIMARY
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "DOSM failed: ${e.message}")
        }

        // Layer 2: Web Search Fallback
        if (sources.size < 2) {
            try {
                val searchQuery = "${classified.raw} statistics Malaysia official"
                val webResults = tavilyRepository.search(searchQuery, maxResults = 3)
                sources.addAll(webResults)
            } catch (e: Exception) {
                Log.e(TAG, "Tavily failed: ${e.message}")
            }
        }

        // Enrich with Bias/Credibility Ratings
        val enrichedSources = sources.map { it.copy(outletRating = ratingRepo.getRating(it.url)) }

        // Source Quality Gate
        val filteredSources = enrichedSources.filter { 
            (it.outletRating?.credibility ?: 50) >= 40 
        }.sortedByDescending { it.outletRating?.credibility ?: 50 }

        // LLM Synthesis
        val (result, provider) = llmRouter.analyze(classified.raw, filteredSources, ClaimType.STATISTICAL)
        return result.copy(
            claim = classified.raw, 
            providerUsed = provider.name,
            sources = enrichedSources // UI shows all
        )
    }
}
