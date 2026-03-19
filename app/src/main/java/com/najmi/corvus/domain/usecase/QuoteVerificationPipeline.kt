package com.najmi.corvus.domain.usecase

import android.util.Log
import com.najmi.corvus.data.remote.HansardClient
import com.najmi.corvus.data.remote.QuoteMatchResult
import com.najmi.corvus.data.remote.WikiquoteClient
import com.najmi.corvus.data.repository.GoogleFactCheckRepository
import com.najmi.corvus.data.repository.TavilyRepository
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
    private val llmRouter: LlmRouter
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
        // Use LLM to analyze all sources and give a final verdict
        // For now, mapping LLM analysis to QuoteResult
        // Note: LlmRouter currently returns GeneralResult, we need a specialized prompt for quotes
        
        val prompt = buildQuotePrompt(classified, sources, foundInWikiquote, wikiquoteText)
        
        // This is a bit of a hack since LlmRouter is built for GeneralResult
        // We might need a QuoteLlmRouter or update LlmRouter to handle both
        // For expediency, I'll use llmRouter.analyze and map it
        
        val (generalResult, provider) = llmRouter.analyze(classified.raw, sources, ClaimType.QUOTE)
        
        return CorvusCheckResult.QuoteResult(
            claim = classified.raw,
            quoteVerdict = mapToQuoteVerdict(generalResult.verdict),
            confidence = generalResult.confidence,
            speaker = classified.speaker ?: "Unknown",
            originalQuote = wikiquoteText ?: generalResult.keyFacts.firstOrNull(),
            submittedQuote = classified.quotedText ?: classified.raw,
            originalSource = sources.firstOrNull(),
            originalDate = classified.claimedDate,
            contextExplanation = generalResult.explanation,
            sources = sources,
            isVerbatim = foundInWikiquote,
            contextAccurate = generalResult.verdict == com.najmi.corvus.domain.model.Verdict.TRUE,
            providerUsed = provider.name
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
