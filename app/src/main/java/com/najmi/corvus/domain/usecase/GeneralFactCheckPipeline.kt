package com.najmi.corvus.domain.usecase

import android.util.Log
import com.najmi.corvus.data.remote.WikidataSparqlClient
import com.najmi.corvus.data.remote.WikipediaClient
import com.najmi.corvus.data.repository.GoogleFactCheckRepository
import com.najmi.corvus.data.repository.TavilyRepository
import com.najmi.corvus.data.repository.OutletRatingRepository
import com.najmi.corvus.domain.model.ClaimLanguage
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.ClassifiedClaim
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.Source
import com.najmi.corvus.domain.model.SourceType
import com.najmi.corvus.domain.router.LlmRouter
import javax.inject.Inject

class GeneralFactCheckPipeline @Inject constructor(
    private val wikipediaClient: WikipediaClient,
    private val wikidataClient: WikidataSparqlClient,
    private val googleFactCheckRepository: GoogleFactCheckRepository,
    private val tavilyRepository: TavilyRepository,
    private val llmRouter: LlmRouter,
    private val ratingRepo: OutletRatingRepository
) {
    companion object {
        private const val TAG = "GeneralPipeline"
    }

    suspend fun verify(classified: ClassifiedClaim): CorvusCheckResult.GeneralResult {
        Log.d(TAG, "Starting general verification for type: ${classified.type}")
        
        val sources = mutableListOf<Source>()

        // Layer 1: Knowledge Bases (Wikipedia/Wikidata)
        if (classified.type == ClaimType.HISTORICAL || classified.type == ClaimType.PERSON_FACT) {
            val entities = classified.entities.ifEmpty { listOf(classified.raw) }
            for (entity in entities.take(2)) {
                val summary = wikipediaClient.getSummary(entity)
                summary?.let {
                    sources.add(Source(
                        title = it.title,
                        url = "https://en.wikipedia.org/wiki/${it.title}",
                        publisher = "Wikipedia",
                        snippet = it.extract,
                        sourceType = SourceType.KNOWLEDGE_BASE
                    ))
                }
                
                if (classified.type == ClaimType.PERSON_FACT) {
                    val roles = wikidataClient.getPersonRoles(entity)
                    roles?.let {
                        sources.add(Source(
                            title = "Wikidata: $entity",
                            url = "https://www.wikidata.org/wiki/Special:Search?search=${entity}",
                            publisher = "Wikidata",
                            snippet = "Roles/Positions: $it",
                            sourceType = SourceType.KNOWLEDGE_BASE
                        ))
                    }
                }
            }
        }

        // Layer 2: Google Fact Check
        try {
            val known = googleFactCheckRepository.search(classified.raw)
            known?.let { sources.addAll(it.sources) }
        } catch (e: Exception) {
            Log.e(TAG, "GFC failed: ${e.message}")
        }

        // Layer 3: Web Search Fallback
        if (sources.size < 2) {
            try {
                val webResults = tavilyRepository.search(classified.raw, maxResults = 3)
                sources.addAll(webResults)
            } catch (e: Exception) {
                Log.e(TAG, "Tavily failed: ${e.message}")
            }
        }

        // Enrich with Bias/Credibility Ratings
        val enrichedSources = sources.map { it.copy(outletRating = ratingRepo.getRating(it.url)) }

        // Source Quality Gate: filter out low-credibility sources from LLM context
        val filteredSources = enrichedSources.filter { 
            (it.outletRating?.credibility ?: 50) >= 40 
        }.sortedByDescending { it.outletRating?.credibility ?: 50 }

        // LLM Synthesis
        val (result, provider) = llmRouter.analyze(classified.raw, filteredSources, classified.type)
        return result.copy(
            claim = classified.raw, 
            providerUsed = provider.name,
            sources = enrichedSources // UI shows all
        )
    }
}
