package com.najmi.corvus.domain.usecase

import android.util.Log
import com.najmi.corvus.data.remote.HansardClient
import com.najmi.corvus.data.remote.QuoteMatchResult
import com.najmi.corvus.data.remote.WikiquoteClient
import com.najmi.corvus.data.repository.GoogleFactCheckRepository
import com.najmi.corvus.data.repository.TavilyRepository
import com.najmi.corvus.data.repository.OutletRatingRepository
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.ClassifiedClaim
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.QuoteVerdict
import com.najmi.corvus.domain.model.Source
import com.najmi.corvus.domain.router.LlmRouter
import javax.inject.Inject

class QuoteVerificationPipeline @Inject constructor(
    private val wikiquoteClient: WikiquoteClient,
    private val hansardClient: HansardClient,
    private val googleFactCheckRepository: GoogleFactCheckRepository,
    private val tavilyRepository: TavilyRepository,
    private val llmRouter: LlmRouter,
    private val ratingRepo: OutletRatingRepository,
    private val plausibilityEnricher: PlausibilityEnricherUseCase
) {
    companion object {
        private const val TAG = "QuotePipeline"
    }

    suspend fun verify(classified: ClassifiedClaim): CorvusCheckResult.QuoteResult {
        Log.d(TAG, "Starting quote verification for: ${classified.speaker}")
        
        val sources = mutableListOf<Source>()
        var foundInWikiquote = false
        var wikiquoteText: String? = null

        // Layer 1: Wikiquote
        classified.speaker?.let { speaker ->
            val summary = wikiquoteClient.getSummary(speaker)
            summary?.extract?.let { extract ->
                val match = wikiquoteClient.findMatch(classified.quotedText ?: classified.raw, extract)
                if (match !is QuoteMatchResult.NOT_FOUND) {
                    foundInWikiquote = true
                    wikiquoteText = if (match is QuoteMatchResult.PARTIAL) match.similarText else classified.quotedText
                }
            }
        }

        // Layer 2: Google Fact Check (Sebenarnya lookup usually ends up here)
        try {
            val factCheck = googleFactCheckRepository.search(classified.raw)
            factCheck?.let { sources.addAll(it.sources) }
        } catch (e: Exception) {
            Log.e(TAG, "Layer 2 failed: ${e.message}")
        }

        // Layer 3: Hansard (Tavily proxy)
        if (classified.speaker != null) {
            try {
                val hansardResults = hansardClient.searchHansard(classified.speaker, classified.quotedText ?: classified.raw)
                sources.addAll(hansardResults)
            } catch (e: Exception) {
                Log.e(TAG, "Layer 3 failed: ${e.message}")
            }
        }

        // Layer 4: Targeted Web Search
        try {
            val query = "\"${classified.quotedText ?: classified.raw}\" ${classified.speaker ?: ""} verify"
            val webResults = tavilyRepository.search(query, maxResults = 5)
            sources.addAll(webResults)
        } catch (e: Exception) {
            Log.e(TAG, "Layer 4 failed: ${e.message}")
        }

        // Final Layer: LLM Synthesis
        return synthesizeResult(classified, sources, foundInWikiquote, wikiquoteText)
    }

    private suspend fun synthesizeResult(
        classified: ClassifiedClaim,
        sources: List<Source>,
        foundInWikiquote: Boolean,
        wikiquoteText: String?
    ): CorvusCheckResult.QuoteResult {
        // Enrich with Bias/Credibility Ratings
        val enrichedSources = sources.map { it.copy(outletRating = ratingRepo.getRating(it.url)) }

        // Source Quality Gate
        val filteredSources = enrichedSources.filter { 
            (it.outletRating?.credibility ?: 50) >= 40 
        }.sortedByDescending { it.outletRating?.credibility ?: 50 }

        val prompt = buildQuotePrompt(classified, filteredSources, foundInWikiquote, wikiquoteText)
        
        val (generalResult, provider) = llmRouter.analyze(classified.raw, filteredSources, ClaimType.QUOTE)
        
        // Plausibility Enrichment for UNVERIFIABLE
        val finalPlausibility = if (generalResult.verdict == com.najmi.corvus.domain.model.Verdict.UNVERIFIABLE) {
            plausibilityEnricher.enrich(
                classified.raw,
                filteredSources,
                generalResult.plausibility
            )
        } else {
            generalResult.plausibility
        }

        return CorvusCheckResult.QuoteResult(
            claim = classified.raw,
            quoteVerdict = mapToQuoteVerdict(generalResult.verdict),
            confidence = generalResult.confidence,
            speaker = classified.speaker ?: "Unknown",
            originalQuote = wikiquoteText ?: generalResult.keyFacts.firstOrNull()?.statement,
            submittedQuote = classified.quotedText ?: classified.raw,
            originalSource = enrichedSources.firstOrNull(),
            originalDate = classified.claimedDate,
            contextExplanation = generalResult.explanation,
            sources = enrichedSources,
            isVerbatim = foundInWikiquote,
            contextAccurate = generalResult.verdict == com.najmi.corvus.domain.model.Verdict.TRUE,
            providerUsed = provider.name,
            harmAssessment = generalResult.harmAssessment,
            plausibility = finalPlausibility,
            keyFacts = generalResult.keyFacts
        )
    }

    private fun buildQuotePrompt(
        classified: ClassifiedClaim,
        sources: List<Source>,
        foundInWikiquote: Boolean,
        wikiquoteText: String?
    ): String {
        return "Quote verification prompt context..." // Simplified for now
    }

    private fun mapToQuoteVerdict(verdict: com.najmi.corvus.domain.model.Verdict): QuoteVerdict {
        return when (verdict) {
            com.najmi.corvus.domain.model.Verdict.TRUE -> QuoteVerdict.VERIFIED
            com.najmi.corvus.domain.model.Verdict.FALSE -> QuoteVerdict.FABRICATED
            com.najmi.corvus.domain.model.Verdict.MISLEADING -> QuoteVerdict.OUT_OF_CONTEXT
            com.najmi.corvus.domain.model.Verdict.PARTIALLY_TRUE -> QuoteVerdict.PARAPHRASED
            else -> QuoteVerdict.UNVERIFIABLE
        }
    }
}
