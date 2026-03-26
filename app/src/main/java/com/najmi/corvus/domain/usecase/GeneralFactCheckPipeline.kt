package com.najmi.corvus.domain.usecase

import android.util.Log
import com.najmi.corvus.data.remote.WikidataSparqlClient
import com.najmi.corvus.data.remote.WikipediaClient
import com.najmi.corvus.data.repository.GoogleFactCheckRepository
import com.najmi.corvus.data.repository.TavilyRepository
import com.najmi.corvus.data.repository.OutletRatingRepository
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.ClassifiedClaim
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.ContextType
import com.najmi.corvus.domain.model.MissingContextInfo
import com.najmi.corvus.domain.model.RetrievalMetadata
import com.najmi.corvus.domain.model.Source
import com.najmi.corvus.domain.model.SourceType
import com.najmi.corvus.domain.model.Verdict
import com.najmi.corvus.data.local.UserPreferencesRepository
import com.najmi.corvus.domain.model.MethodologyMetadata
import com.najmi.corvus.domain.model.PipelineStep
import com.najmi.corvus.domain.model.PipelineStepResult
import com.najmi.corvus.domain.remote.llm.TemporalPromptInjector
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GeneralFactCheckPipeline @Inject constructor(
    private val wikipediaClient: WikipediaClient,
    private val wikidataClient: WikidataSparqlClient,
    private val googleFactCheckRepository: GoogleFactCheckRepository,
    private val tavilyRepository: TavilyRepository,
    private val queryRewriter: QueryRewriterUseCase,
    private val multiQueryRetriever: MultiQueryRetriever,
    private val actorCriticPipeline: ActorCriticPipeline,
    private val userPrefsRepo: UserPreferencesRepository,
    private val ratingRepo: OutletRatingRepository,
    private val plausibilityEnricher: PlausibilityEnricherUseCase,
    private val ragVerifier: RagVerifierUseCase,
    private val algorithmicVerifier: AlgorithmicGroundingVerifier,
    private val temporalAnalyser: TemporalClaimAnalyser,
    private val temporalMismatchDetector: SourceTemporalMismatchDetector
) {
    companion object {
        private const val TAG = "GeneralPipeline"
    }

    suspend fun verify(
        classified: ClassifiedClaim,
        onStepChange: suspend (PipelineStep) -> Unit = {}
    ): CorvusCheckResult.GeneralResult {
        Log.d(TAG, "Starting general verification for type: ${classified.type}")
        
        val sources = mutableListOf<Source>()
        val steps = mutableListOf<PipelineStepResult>()
        
        // Phase 1: Query Rewriting (NEW)
        val rewrittenQuery = queryRewriter.rewrite(classified)
        steps.add(PipelineStepResult(PipelineStep.RETRIEVING_SOURCES, "Query rewritten to ${rewrittenQuery.searchQueries.size} search queries"))
        
        // Phase 2: Multi-Query Retrieval (NEW)
        val sourceSet = multiQueryRetriever.retrieve(rewrittenQuery, classified)
        sources.addAll(sourceSet.sources)
        steps.add(PipelineStepResult(PipelineStep.RETRIEVING_SOURCES, "Retrieved ${sourceSet.totalRawResults} raw sources, ${sourceSet.deduplicatedCount} after dedup"))
        
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

        // Enrich with Bias/Credibility Ratings
        val enrichedSources = sources.map { it.copy(outletRating = ratingRepo.getRating(it.url)) }

        // Source Quality Gate
        val filteredSourcesCount = enrichedSources.count { (it.outletRating?.credibility ?: 50) >= 40 }
        steps.add(PipelineStepResult(PipelineStep.ANALYZING, "Filtered to $filteredSourcesCount high-credibility sources"))

        val filteredSources = enrichedSources.filter { 
            (it.outletRating?.credibility ?: 50) >= 40 
        }.sortedByDescending { it.outletRating?.credibility ?: 50 }

        // Temporal Analysis (NEW)
        val temporalProfile = temporalAnalyser.analyze(classified.raw)
        val temporalMismatch = temporalMismatchDetector.detect(filteredSources, temporalProfile)
        
        if (temporalMismatch.hasSignificantMismatch) {
            steps.add(PipelineStepResult(PipelineStep.ANALYZING, "TEMPORAL MISMATCH: ${temporalMismatch.mismatchDetails.size} old sources detected"))
        }

        // LLM Synthesis with Temporal Context
        onStepChange(PipelineStep.ANALYZING)
        val prefs = userPrefsRepo.preferences.first()
        
        val temporalContext = TemporalPromptInjector.buildTemporalContext(
            profile = temporalProfile,
            mismatchReport = temporalMismatch,
            sources = filteredSources
        )
        
        val initialResult = actorCriticPipeline.analyzeWithTemporalContext(
            claim = rewrittenQuery.coreQuestion,
            classified = classified,
            sources = filteredSources,
            prefs = prefs,
            temporalContext = temporalContext,
            onStepChange = onStepChange
        )
        
        val providerUsed = initialResult.criticProvider?.name ?: "Unknown"
        steps.add(PipelineStepResult(PipelineStep.ANALYZING, "Synthesized by $providerUsed"))

        // Apply Temporal Override if needed
        val afterTemporalOverride = applyTemporalOverride(initialResult, temporalMismatch, temporalProfile)
        
        // Plausibility Enrichment
        val finalResult = if (afterTemporalOverride.verdict == Verdict.UNVERIFIABLE) {
            steps.add(PipelineStepResult(PipelineStep.DONE, "Enriched with plausibility analysis"))
            val enrichedPlausibility = plausibilityEnricher.enrich(
                classified.raw,
                filteredSources,
                afterTemporalOverride.plausibility
            )
            afterTemporalOverride.copy(plausibility = enrichedPlausibility)
        } else {
            steps.add(PipelineStepResult(PipelineStep.DONE, "Analysis complete"))
            afterTemporalOverride
        }

        val avgCredibility = if (enrichedSources.isNotEmpty()) {
            enrichedSources.map { it.outletRating?.credibility ?: 50 }.average().toInt()
        } else 0

        val methodology = MethodologyMetadata(
            pipelineStepsCompleted = steps,
            claimTypeDetected = classified.type,
            sourcesRetrieved = enrichedSources.size,
            avgSourceCredibility = avgCredibility,
            llmProviderUsed = providerUsed,
            checkedAt = System.currentTimeMillis()
        )

        // Algorithmic Grounding Verification
        onStepChange(PipelineStep.GROUNDING_CHECK)
        val algoResult = algorithmicVerifier.verify(finalResult.keyFacts, enrichedSources)
        val penaltyLog = algoResult.fabricatedCitations.map {
            "Algorithmic Reject: Removed fabricated attribution to Source [${it.claimedSourceIndex}] for quote: '${it.originalStatement}'"
        }

        // RAG Verification pass
        val verifiedFacts = ragVerifier.verifyFacts(algoResult.verifiedFacts, enrichedSources)
        val explanationVerification = ragVerifier.verifyExplanation(
            finalResult.explanation,
            enrichedSources
        )

        // Escalate verdict confidence if explanation is poorly grounded
        var adjustedConfidence = when (explanationVerification.overallConfidence) {
            com.najmi.corvus.domain.model.ExplanationConfidence.POORLY_GROUNDED     -> finalResult.confidence * 0.6f
            com.najmi.corvus.domain.model.ExplanationConfidence.PARTIALLY_GROUNDED  -> finalResult.confidence * 0.8f
            else                                      -> finalResult.confidence
        }
        
        // Apply Algorithmic fabrication penalty
        adjustedConfidence = maxOf(0.05f, adjustedConfidence - algoResult.totalConfidencePenalty)

        return finalResult.copy(
            claim = classified.raw, 
            claimType = classified.type,
            providerUsed = providerUsed,
            sources = enrichedSources,
            methodology = methodology,
            keyFacts = verifiedFacts,
            confidence = adjustedConfidence,
            explanationVerification = explanationVerification,
            correctionsLog = (finalResult.correctionsLog ?: emptyList()) + penaltyLog,
            retrievalMetadata = RetrievalMetadata(
                originalClaim = classified.raw,
                rewrittenQueries = rewrittenQuery.searchQueries,
                coreQuestion = rewrittenQuery.coreQuestion,
                totalRawSources = sourceSet.totalRawResults,
                dedupedSources = sourceSet.deduplicatedCount,
                finalSources = sourceSet.sources.size
            )
        )
    }

    private fun applyTemporalOverride(
        llmResult: CorvusCheckResult.GeneralResult,
        mismatchReport: com.najmi.corvus.domain.model.TemporalMismatchReport,
        profile: com.najmi.corvus.domain.model.TemporalClaimProfile
    ): CorvusCheckResult.GeneralResult {
        val shouldOverride = mismatchReport.hasSignificantMismatch &&
            mismatchReport.suggestedVerdict == Verdict.MISLEADING &&
            profile.temporalUrgency == com.najmi.corvus.domain.model.TemporalUrgency.HIGH &&
            llmResult.verdict != Verdict.MISLEADING &&
            llmResult.verdict != Verdict.UNVERIFIABLE

        if (!shouldOverride) return llmResult

        val oldestSourceAge = mismatchReport.oldestSourceAge ?: 0
        val overrideNote = "TEMPORAL OVERRIDE: Sources are $oldestSourceAge days old. Claim implies current event. Verdict adjusted from ${llmResult.verdict.name} to MISLEADING."

        return llmResult.copy(
            verdict = Verdict.MISLEADING,
            confidence = (llmResult.confidence * 0.75f).coerceAtLeast(0.3f),
            missingContext = llmResult.missingContext ?: MissingContextInfo(
                content = "The sources describing this event are ${oldestSourceAge / 30} months old. The claim implies this is a current event, but the evidence dates to an earlier period.",
                contextType = ContextType.TEMPORAL
            ),
            correctionsLog = (llmResult.correctionsLog ?: emptyList()) + listOf(overrideNote)
        )
    }
}
