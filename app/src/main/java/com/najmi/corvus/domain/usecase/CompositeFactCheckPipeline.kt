package com.najmi.corvus.domain.usecase

import com.najmi.corvus.domain.usecase.DecompositionResult

import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.PipelineStep
import com.najmi.corvus.domain.model.Verdict
import com.najmi.corvus.domain.model.SubClaim
import com.najmi.corvus.domain.model.QuoteVerdict
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class CompositeFactCheckPipeline @Inject constructor(
    private val viralDetector: ViralClaimDetectorUseCase,
    private val decomposer: ClaimDecomposerUseCase,
    private val factCheckUseCase: CorvusFactCheckUseCase,
    private val timelineBuilder: ConfidenceTimelineBuilder
) {
    suspend fun check(
        raw: String,
        onStepChange: (PipelineStep) -> Unit
    ): CorvusCheckResult = coroutineScope {
        onStepChange(PipelineStep.CHECKING_VIRAL_DATABASE)
        val viralHit = viralDetector.check(raw)
        if (viralHit != null) {
            onStepChange(PipelineStep.DONE)
            return@coroutineScope viralHit
        }

        onStepChange(PipelineStep.DISSECTING)
        
        val result = when (val decomposed = decomposer.decompose(raw)) {
            is DecompositionResult.Single -> {
                factCheckUseCase.check(decomposed.claim, onStepChange)
            }
            is DecompositionResult.Compound -> {
                checkCompound(decomposed, onStepChange)
            }
        }

        enrichWithTimeline(result)
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
        onStepChange: (PipelineStep) -> Unit
    ): CorvusCheckResult.CompositeResult = coroutineScope {
        // Parallel check for each sub-claim
        // We use a simplified onStepChange that doesn't trigger for each sub-claim
        // or we could show something like "Checking sub-claim 1/N..."
        
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
        
        onStepChange(PipelineStep.DONE)
        
        CorvusCheckResult.CompositeResult(
            claim = compound.original,
            subClaims = updatedSubClaims,
            compositeVerdict = deriveCompositeVerdict(updatedSubClaims),
            confidence = updatedSubClaims.map { it.result?.confidence ?: 0f }.average().toFloat(),
            compositeSummary = buildCompositeSummary(updatedSubClaims),
            sources = updatedSubClaims.flatMap { it.result?.sources ?: emptyList() }.distinctBy { it.url }
        )
    }

    private fun deriveCompositeVerdict(subClaims: List<SubClaim>): Verdict {
        val verdicts = subClaims.mapNotNull { 
            (it.result as? CorvusCheckResult.GeneralResult)?.verdict 
                ?: (it.result as? CorvusCheckResult.QuoteResult)?.let { qr ->
                    // Map QuoteVerdict to Verdict for aggregation
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

    private fun buildCompositeSummary(subClaims: List<SubClaim>): String {
        return subClaims.joinToString("\n") { sub ->
            val verdict = when (val res = sub.result) {
                is CorvusCheckResult.GeneralResult -> res.verdict.name
                is CorvusCheckResult.QuoteResult -> res.quoteVerdict.name
                else -> "UNKNOWN"
            }
            "• ${sub.text}: $verdict"
        }
    }
}
