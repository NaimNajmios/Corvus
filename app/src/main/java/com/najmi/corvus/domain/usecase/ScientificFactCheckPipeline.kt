package com.najmi.corvus.domain.usecase

import android.util.Log
import com.najmi.corvus.data.remote.PubMedClient
import com.najmi.corvus.data.repository.TavilyRepository
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.ClassifiedClaim
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.CredibilityTier
import com.najmi.corvus.domain.model.Source
import com.najmi.corvus.domain.model.SourceType
import com.najmi.corvus.domain.router.LlmRouter
import javax.inject.Inject

class ScientificFactCheckPipeline @Inject constructor(
    private val pubMedClient: PubMedClient,
    private val tavilyRepository: TavilyRepository,
    private val llmRouter: LlmRouter
) {
    companion object {
        private const val TAG = "SciencePipeline"
    }

    suspend fun verify(classified: ClassifiedClaim): CorvusCheckResult.GeneralResult {
        Log.d(TAG, "Starting scientific verification for: ${classified.raw}")
        
        val sources = mutableListOf<Source>()

        // Layer 1: PubMed
        try {
            val pmids = pubMedClient.search(classified.raw)
            for (pmid in pmids.take(2)) {
                val abstract = pubMedClient.fetchAbstract(pmid)
                abstract?.let {
                    sources.add(Source(
                        title = "PubMed Article $pmid",
                        url = "https://pubmed.ncbi.nlm.nih.gov/$pmid/",
                        publisher = "National Library of Medicine (PubMed)",
                        snippet = it.take(300) + "...",
                        sourceType = SourceType.ACADEMIC,
                        credibilityTier = CredibilityTier.PRIMARY
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "PubMed failed: ${e.message}")
        }

        // Layer 2: Web Search Fallback
        if (sources.size < 2) {
            try {
                val searchQuery = "${classified.raw} peer reviewed scientific study"
                val webResults = tavilyRepository.search(searchQuery, maxResults = 3)
                sources.addAll(webResults)
            } catch (e: Exception) {
                Log.e(TAG, "Tavily failed: ${e.message}")
            }
        }

        // LLM Synthesis
        val (result, provider) = llmRouter.analyze(classified.raw, sources, ClaimType.SCIENTIFIC)
        return result.copy(claim = classified.raw, providerUsed = provider.name)
    }
}
