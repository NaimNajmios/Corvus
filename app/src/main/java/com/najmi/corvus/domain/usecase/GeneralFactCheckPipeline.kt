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
import com.najmi.corvus.domain.model.MethodologyMetadata
import com.najmi.corvus.domain.model.PipelineStep
import com.najmi.corvus.domain.model.PipelineStepResult
import javax.inject.Inject

class GeneralFactCheckPipeline @Inject constructor(
    private val wikipediaClient: WikipediaClient,
    private val wikidataClient: WikidataSparqlClient,
    private val googleFactCheckRepository: GoogleFactCheckRepository,
    private val tavilyRepository: TavilyRepository,
    private val llmRouter: LlmRouter,
    private val ratingRepo: OutletRatingRepository,
    private val plausibilityEnricher: PlausibilityEnricherUseCase,
    private val ragVerifier: RagVerifierUseCase
) {
    companion object {
        private const val TAG = "GeneralPipeline"
    }

    suspend fun verify(classified: ClassifiedClaim): CorvusCheckResult.GeneralResult {
        Log.d(TAG, "Starting general verification for type: ${classified.type}")
        
        val sources = mutableListOf<Source>()
        val steps = mutableListOf<PipelineStepResult>()
        
        // Layer 1: Knowledge Bases (Wikipedia/Wikidata)
        var kbFound = false
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
            kbFound = sources.isNotEmpty()
            steps.add(PipelineStepResult(PipelineStep.CHECKING_KNOWN_FACTS, if (kbFound) "Found knowledge base matches" else "No direct matches in KB"))
        }

        // Layer 2: Google Fact Check
        var gfcFound = false
        try {
            val known = googleFactCheckRepository.search(classified.raw)
            known?.let { 
                sources.addAll(it.sources) 
                gfcFound = true
            }
            steps.add(PipelineStepResult(PipelineStep.CHECKING_KNOWN_FACTS, if (gfcFound) "Found Google Fact Check match" else "No matching GFC entries"))
        } catch (e: Exception) {
            Log.e(TAG, "GFC failed: ${e.message}")
        }

        // Layer 3: Web Search Fallback
        if (sources.size < 2) {
            try {
                steps.add(PipelineStepResult(PipelineStep.RETRIEVING_SOURCES, "Performing Tavily web search"))
                val webResults = tavilyRepository.search(classified.raw, maxResults = 3)
                sources.addAll(webResults)
            } catch (e: Exception) {
                Log.e(TAG, "Tavily failed: ${e.message}")
            }
        } else {
            steps.add(PipelineStepResult(PipelineStep.RETRIEVING_SOURCES, "Skipped search (sufficient KB evidence)"))
        }

        // Enrich with Bias/Credibility Ratings
        val enrichedSources = sources.map { it.copy(outletRating = ratingRepo.getRating(it.url)) }

        // Source Quality Gate
        val filteredSourcesCount = enrichedSources.count { (it.outletRating?.credibility ?: 50) >= 40 }
        steps.add(PipelineStepResult(PipelineStep.ANALYZING, "Filtered to $filteredSourcesCount high-credibility sources"))

        val filteredSources = enrichedSources.filter { 
            (it.outletRating?.credibility ?: 50) >= 40 
        }.sortedByDescending { it.outletRating?.credibility ?: 50 }

        // LLM Synthesis
        val (initialResult, provider) = llmRouter.analyze(classified.raw, filteredSources, classified.type)
        steps.add(PipelineStepResult(PipelineStep.ANALYZING, "Synthesized by ${provider.name}"))

        // Plausibility Enrichment
        val finalResult = if (initialResult.verdict == com.najmi.corvus.domain.model.Verdict.UNVERIFIABLE) {
            steps.add(PipelineStepResult(PipelineStep.DONE, "Enriched with plausibility analysis"))
            val enrichedPlausibility = plausibilityEnricher.enrich(
                classified.raw,
                filteredSources,
                initialResult.plausibility
            )
            initialResult.copy(plausibility = enrichedPlausibility)
        } else {
            steps.add(PipelineStepResult(PipelineStep.DONE, "Analysis complete"))
            initialResult
        }

        val avgCredibility = if (enrichedSources.isNotEmpty()) {
            enrichedSources.map { it.outletRating?.credibility ?: 50 }.average().toInt()
        } else 0

        val methodology = MethodologyMetadata(
            pipelineStepsCompleted = steps,
            claimTypeDetected = classified.type,
            sourcesRetrieved = enrichedSources.size,
            avgSourceCredibility = avgCredibility,
            llmProviderUsed = provider.name,
            checkedAt = System.currentTimeMillis()
        )

        // ── RAG Verification pass ────────────────────────────────────────
        val verifiedFacts = ragVerifier.verifyFacts(finalResult.keyFacts, enrichedSources)
        val explanationVerification = ragVerifier.verifyExplanation(
            finalResult.explanation,
            enrichedSources
        )

        // Escalate verdict confidence if explanation is poorly grounded
        val adjustedConfidence = when (explanationVerification.overallConfidence) {
            com.najmi.corvus.domain.model.ExplanationConfidence.POORLY_GROUNDED     -> finalResult.confidence * 0.6f
            com.najmi.corvus.domain.model.ExplanationConfidence.PARTIALLY_GROUNDED  -> finalResult.confidence * 0.8f
            else                                      -> finalResult.confidence
        }
        // ────────────────────────────────────────────────────────────────

        return finalResult.copy(
            claim = classified.raw, 
            providerUsed = provider.name,
            sources = enrichedSources,
            methodology = methodology,
            keyFacts = verifiedFacts,
            confidence = adjustedConfidence,
            explanationVerification = explanationVerification
        )
    }
}
