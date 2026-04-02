package com.najmi.corvus.domain.usecase

import com.najmi.corvus.domain.usecase.DecompositionResult

import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.PipelineStep
import com.najmi.corvus.domain.model.Verdict
import com.najmi.corvus.domain.model.SubClaim
import com.najmi.corvus.domain.model.QuoteVerdict
import com.najmi.corvus.domain.model.RetrievalMetadata
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class CompositeFactCheckPipeline @Inject constructor(
    private val viralDetector: ViralClaimDetectorUseCase,
    private val decomposer: ClaimDecomposerUseCase,
    private val factCheckUseCase: CorvusFactCheckUseCase,
    private val timelineBuilder: ConfidenceTimelineBuilder,
    private val kgEnricher: KgEnricherUseCase,
    private val classifier: ClaimClassifierUseCase,
    private val holisticVerifier: HolisticClaimVerifier
) {
    suspend fun check(
        raw: String,
        onStepChange: suspend (PipelineStep) -> Unit
    ): CorvusCheckResult = coroutineScope {
        onStepChange(PipelineStep.CHECKING_VIRAL_DATABASE)
        val viralHit = viralDetector.check(raw)
        if (viralHit != null) {
            onStepChange(PipelineStep.DONE)
            return@coroutineScope viralHit
        }

        val classified = classifier.classify(raw)

        val mainResultDeferred = async {
            val res = when (val decomposed = decomposer.decompose(raw)) {
                is DecompositionResult.Single -> {
                    factCheckUseCase.check(decomposed.claim, onStepChange)
                }
                is DecompositionResult.Compound -> {
                    checkCompound(decomposed, onStepChange)
                }
            }
            enrichWithTimeline(res)
        }

        val entityContextDeferred = async {
            try {
                kgEnricher.enrich(raw, classified)
            } catch (e: Exception) {
                null
            }
        }

        val mainResult = mainResultDeferred.await()
        val entityContext = entityContextDeferred.await()

        mainResult.withEntityContext(entityContext)
    }

    private suspend fun enrichWithTimeline(result: CorvusCheckResult): CorvusCheckResult {
        val timeline = timelineBuilder.buildTimeline(
            claim = result.claim,
            currentConfidence = result.confidence,
            currentTimestamp = result.checkedAt,
            sourceTitle = result.providerUsed
        )
        
        return when (result) {
            is CorvusCheckResult.GeneralResult -> result.copy(confidenceTimeline = timeline)
            is CorvusCheckResult.QuoteResult -> result.copy(confidenceTimeline = timeline)
            is CorvusCheckResult.CompositeResult -> result.copy(confidenceTimeline = timeline)
            is CorvusCheckResult.ViralHoaxResult -> result // No timeline for viral hoaxes
        }
    }

    private suspend fun checkCompound(
        compound: DecompositionResult.Compound,
        onStepChange: suspend (PipelineStep) -> Unit
    ): CorvusCheckResult.CompositeResult = coroutineScope {
        onStepChange(PipelineStep.CHECKING_SUB_CLAIMS)
        
        val deferredResults = compound.subClaims.map { subClaim ->
            async {
                factCheckUseCase.check(subClaim.text) { /* Ignore inner steps for now */ }
            }
        }
        
        val results = deferredResults.awaitAll()
        
        val updatedSubClaims = compound.subClaims.zip(results).map { (sub, res) ->
            sub.copy(result = res)
        }
        
        val allSources = updatedSubClaims.flatMap { it.result?.sources ?: emptyList() }.distinctBy { it.url }
        
        val holisticResult = holisticVerifier.verify(compound.original, updatedSubClaims, allSources)
        
        val finalCompositeVerdict = deriveFinalCompositeVerdict(updatedSubClaims, holisticResult)
        
        onStepChange(PipelineStep.DONE)
        
        val allReasoning = updatedSubClaims.mapNotNull { sub ->
            (sub.result as? CorvusCheckResult.GeneralResult)?.reasoningScratchpad
        }.filter { it.isNotBlank() }
        
        val allCorrections = updatedSubClaims.flatMap { sub ->
            when (sub.result) {
                is CorvusCheckResult.GeneralResult -> (sub.result as CorvusCheckResult.GeneralResult).correctionsLog ?: emptyList()
                is CorvusCheckResult.QuoteResult -> (sub.result as CorvusCheckResult.QuoteResult).keyFacts.map { "Sub-claim [${sub.index}]: ${it.statement}" }
                else -> emptyList()
            }
        }.let { corrections ->
            if (holisticResult.correctionsApplied.isNotEmpty()) {
                corrections + "HOLISTIC CORRECTIONS: " + holisticResult.correctionsApplied
            } else corrections
        }
        
        val allRewrittenQueries = updatedSubClaims.flatMap { sub ->
            (sub.result as? CorvusCheckResult.GeneralResult)?.retrievalMetadata?.rewrittenQueries ?: emptyList()
        }.distinct()
        
        val totalRawSources = updatedSubClaims.sumOf { sub ->
            (sub.result as? CorvusCheckResult.GeneralResult)?.retrievalMetadata?.totalRawSources ?: 0
        }
        
        val totalDedupedSources = updatedSubClaims.sumOf { sub ->
            (sub.result as? CorvusCheckResult.GeneralResult)?.retrievalMetadata?.dedupedSources ?: 0
        }
        
        val holisticAnalysisNote = if (holisticResult.issuesFound.isNotEmpty()) {
            val issuesSummary = holisticResult.issuesFound.joinToString("; ") { "${it.type}: ${it.description}" }
            "\n\n=== HOLISTIC ANALYSIS ===\n$issuesSummary\n${holisticResult.holisticExplanation}"
        } else null
        
        val reasoningScratchpad = if (allReasoning.isNotEmpty() || holisticAnalysisNote != null) {
            val parts = mutableListOf<String>()
            allReasoning.mapIndexed { index, reasoning ->
                parts.add("=== Sub-Claim ${index + 1} Analysis ===\n$reasoning")
            }
            holisticAnalysisNote?.let { parts.add(it) }
            parts.joinToString("\n\n")
        } else null
        
        val retrievalMeta = if (allRewrittenQueries.isNotEmpty()) {
            RetrievalMetadata(
                originalClaim = compound.original,
                rewrittenQueries = allRewrittenQueries,
                coreQuestion = compound.original,
                totalRawSources = totalRawSources,
                dedupedSources = totalDedupedSources,
                finalSources = allSources.size
            )
        } else null
        
        val avgSubclaimConfidence = updatedSubClaims.map { it.result?.confidence ?: 0f }.average().toFloat()
        val finalConfidence = if (holisticResult.confidence > 0) {
            (avgSubclaimConfidence + holisticResult.confidence) / 2
        } else avgSubclaimConfidence
        
        CorvusCheckResult.CompositeResult(
            claim = compound.original,
            subClaims = updatedSubClaims,
            compositeVerdict = finalCompositeVerdict,
            confidence = finalConfidence,
            compositeSummary = buildCompositeSummary(updatedSubClaims),
            sources = allSources,
            reasoningScratchpad = reasoningScratchpad,
            correctionsLog = allCorrections.ifEmpty { null },
            retrievalMetadata = retrievalMeta,
            holisticVerdict = holisticResult.holisticVerdict,
            holisticIssues = holisticResult.issuesFound,
            holisticExplanation = holisticResult.holisticExplanation,
            holisticConfidence = holisticResult.confidence.takeIf { it > 0 },
            holisticCorrections = holisticResult.correctionsApplied,
            methodology = com.najmi.corvus.domain.model.MethodologyMetadata(
                pipelineStepsCompleted = listOf(
                    com.najmi.corvus.domain.model.PipelineStepResult(
                        com.najmi.corvus.domain.model.PipelineStep.CHECKING_SUB_CLAIMS, 
                        "Claim decomposed into ${updatedSubClaims.size} sub-claims"
                    ),
                    com.najmi.corvus.domain.model.PipelineStepResult(
                        com.najmi.corvus.domain.model.PipelineStep.GROUNDING_CHECK,
                        "Holistic verification completed with ${holisticResult.issuesFound.size} issues found"
                    )
                ),
                claimTypeDetected = com.najmi.corvus.domain.model.ClaimType.GENERAL,
                sourcesRetrieved = allSources.size,
                avgSourceCredibility = if (updatedSubClaims.isNotEmpty()) {
                    updatedSubClaims.mapNotNull { it.result?.methodology?.avgSourceCredibility }.average().toInt().takeIf { it > 0 } ?: 50
                } else 50,
                llmProviderUsed = "Corvus Aggregator",
                checkedAt = System.currentTimeMillis()
            )
        )
    }

    private fun deriveCompositeVerdict(subClaims: List<SubClaim>): Verdict {
        val verdicts = subClaims.mapNotNull { 
            (it.result as? CorvusCheckResult.GeneralResult)?.verdict 
                ?: (it.result as? CorvusCheckResult.QuoteResult)?.let { qr ->
                    when (qr.quoteVerdict) {
                        QuoteVerdict.VERIFIED -> Verdict.TRUE
                        QuoteVerdict.FABRICATED -> Verdict.FALSE
                        else -> Verdict.PARTIALLY_TRUE
                    }
                }
        }
        
        if (verdicts.isEmpty()) return Verdict.UNVERIFIABLE
        
        return when {
            verdicts.all { it == Verdict.TRUE } -> Verdict.TRUE
            verdicts.all { it == Verdict.FALSE } -> Verdict.FALSE
            verdicts.any { it == Verdict.MISLEADING } -> Verdict.MISLEADING
            verdicts.any { it == Verdict.FALSE } && verdicts.any { it == Verdict.TRUE } -> Verdict.PARTIALLY_TRUE
            verdicts.any { it == Verdict.PARTIALLY_TRUE } -> Verdict.PARTIALLY_TRUE
            else -> Verdict.UNVERIFIABLE
        }
    }

    private fun deriveFinalCompositeVerdict(
        subClaims: List<SubClaim>,
        holisticResult: com.najmi.corvus.domain.model.HolisticVerificationResult
    ): Verdict {
        val subclaimVerdict = deriveCompositeVerdict(subClaims)
        
        if (holisticResult.holisticVerdict == Verdict.UNVERIFIABLE) {
            return subclaimVerdict
        }
        
        return when {
            holisticResult.holisticVerdict == Verdict.MISLEADING -> Verdict.MISLEADING
            holisticResult.holisticVerdict == Verdict.FALSE && subclaimVerdict != Verdict.FALSE -> Verdict.FALSE
            holisticResult.holisticVerdict == Verdict.PARTIALLY_TRUE && 
                subclaimVerdict in listOf(Verdict.TRUE, Verdict.PARTIALLY_TRUE) -> Verdict.PARTIALLY_TRUE
            holisticResult.issuesFound.isNotEmpty() && subclaimVerdict == Verdict.TRUE -> Verdict.MISLEADING
            else -> subclaimVerdict
        }
    }

    private fun buildCompositeSummary(subClaims: List<SubClaim>): String {
        return subClaims.joinToString("\n") { sub ->
            val verdict = when (val res = sub.result) {
                is CorvusCheckResult.GeneralResult -> res.verdict.name.replace("_", " ")
                is CorvusCheckResult.QuoteResult -> res.quoteVerdict.name.replace("_", " ")
                else -> "UNKNOWN"
            }
            "• ${sub.text}: $verdict"
        }
    }
}
