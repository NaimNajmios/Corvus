package com.najmi.corvus.domain.usecase

import com.najmi.corvus.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class RagVerifierUseCase @Inject constructor() {

    companion object {
        const val VERIFIED_THRESHOLD       = 0.70f
        const val PARTIAL_THRESHOLD        = 0.40f
        const val WELL_GROUNDED_RATIO      = 0.75f
        const val MOSTLY_GROUNDED_RATIO    = 0.50f
        const val PARTIALLY_GROUNDED_RATIO = 0.25f

        // Max chars of source content to search — balance coverage vs performance
        const val MAX_SOURCE_CONTENT_CHARS = 5000
    }

    // ── Fact verification ────────────────────────────────────────────────

    fun verifyFacts(
        facts: List<GroundedFact>,
        sources: List<Source>
    ): List<GroundedFact> {
        return facts.mapIndexed { index, fact ->
            val verification = verifyFact(index, fact, sources)
            fact.copy(verification = verification)
        }
    }

    private fun verifyFact(
        index: Int,
        fact: GroundedFact,
        sources: List<Source>
    ): FactVerification {

        // UNATTRIBUTED — no source index, cannot verify
        if (fact.sourceIndex == null) {
            return FactVerification(
                factIndex         = index,
                confidence        = CitationConfidence.UNATTRIBUTED,
                coverageScore     = 0f,
                matchedFragment   = null,
                matchedSourceIndex = null
            )
        }

        // Get the cited source
        val citedSource = sources.getOrNull(fact.sourceIndex)
            ?: return FactVerification(
                factIndex         = index,
                confidence        = CitationConfidence.LOW_CONFIDENCE,
                coverageScore     = 0f,
                matchedFragment   = null,
                matchedSourceIndex = fact.sourceIndex
            )

        // Search for evidence in cited source first
        val (score, fragment) = searchForEvidence(fact.statement, citedSource)

        // If cited source score is low, try all other sources
        val (finalScore, finalFragment, finalSourceIndex) = if (score >= PARTIAL_THRESHOLD) {
            Triple(score, fragment, fact.sourceIndex)
        } else {
            // Try other sources — maybe LLM cited the wrong index
            val bestAlternative = sources
                .mapIndexed { i, s -> Triple(i, searchForEvidence(fact.statement, s), s) }
                .filter { (i, _, _) -> i != fact.sourceIndex }
                .maxByOrNull { (_, result, _) -> result.first }

            val altScore = bestAlternative?.second?.first ?: 0f
            if (altScore > score) {
                Triple(
                    altScore,
                    bestAlternative?.second?.second,
                    bestAlternative?.first
                )
            } else {
                Triple(score, fragment, fact.sourceIndex)
            }
        }

        val confidence = when {
            finalScore >= VERIFIED_THRESHOLD -> CitationConfidence.VERIFIED
            finalScore >= PARTIAL_THRESHOLD  -> CitationConfidence.PARTIAL
            else                             -> CitationConfidence.LOW_CONFIDENCE
        }

        return FactVerification(
            factIndex          = index,
            confidence         = confidence,
            coverageScore      = finalScore,
            matchedFragment    = finalFragment,
            matchedSourceIndex = finalSourceIndex
        )
    }

    // ── Evidence search ──────────────────────────────────────────────────

    private fun searchForEvidence(
        statement: String,
        source: Source
    ): Pair<Float, String?> {

        // Build searchable text from source — prefer rawContent, fall back to snippet
        val sourceText = (source.rawContent ?: source.snippet ?: "")
            .take(MAX_SOURCE_CONTENT_CHARS)
            .lowercase()

        if (sourceText.isBlank()) return 0f to null

        val keywords = FactKeywordExtractor.extract(statement)
        val numerics = FactKeywordExtractor.extractNumerics(statement)

        if (keywords.isEmpty()) return 0f to null

        // 1. Numeric matching — high weight (numbers are most verifiable)
        val numericScore = if (numerics.isNotEmpty()) {
            val matched = numerics.count { num -> sourceText.contains(num) }
            (matched.toFloat() / numerics.size) * 0.5f  // Numerics weight 50%
        } else 0f

        // 2. Keyword matching
        val keywordScore = run {
            val matched = keywords.count { kw -> sourceText.contains(kw) }
            (matched.toFloat() / keywords.size) * 0.5f  // Keywords weight 50%
        }

        val totalScore = numericScore + keywordScore

        // 3. Extract the best matching fragment for UI display
        val fragment = if (totalScore >= PARTIAL_THRESHOLD) {
            extractBestFragment(keywords, sourceText)
        } else null

        return totalScore to fragment
    }

    // Find the sentence in the source that contains the most keywords
    private fun extractBestFragment(
        keywords: List<String>,
        sourceText: String
    ): String? {
        val sentences = sourceText.split(Regex("[.!?]"))

        return sentences
            .map { sentence ->
                val matchCount = keywords.count { kw -> sentence.lowercase().contains(kw) }
                sentence.trim() to matchCount
            }
            .filter { (_, count) -> count >= 2 }
            .maxByOrNull { (_, count) -> count }
            ?.first
            ?.take(200)   // Cap fragment length for UI
            ?.let { "\"...${it.trim()}...\"" }
    }

    // ── Explanation verification ─────────────────────────────────────────

    fun verifyExplanation(
        explanation: String,
        sources: List<Source>
    ): ExplanationVerification {

        // Split explanation into individual sentences
        val sentences = explanation
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length > 20 }   // Skip very short sentences

        if (sentences.isEmpty()) {
            return ExplanationVerification(
                overallConfidence = ExplanationConfidence.POORLY_GROUNDED,
                groundedSentences = 0,
                totalSentences    = 0,
                groundingRatio    = 0f
            )
        }

        // Check each sentence against all sources
        val groundedCount = sentences.count { sentence ->
            val bestScore = sources.maxOfOrNull { source ->
                searchForEvidence(sentence, source).first
            } ?: 0f
            bestScore >= PARTIAL_THRESHOLD
        }

        val ratio = groundedCount.toFloat() / sentences.size

        val confidence = when {
            ratio >= WELL_GROUNDED_RATIO      -> ExplanationConfidence.WELL_GROUNDED
            ratio >= MOSTLY_GROUNDED_RATIO    -> ExplanationConfidence.MOSTLY_GROUNDED
            ratio >= PARTIALLY_GROUNDED_RATIO -> ExplanationConfidence.PARTIALLY_GROUNDED
            else                              -> ExplanationConfidence.POORLY_GROUNDED
        }

        return ExplanationVerification(
            overallConfidence = confidence,
            groundedSentences = groundedCount,
            totalSentences    = sentences.size,
            groundingRatio    = ratio
        )
    }
}
