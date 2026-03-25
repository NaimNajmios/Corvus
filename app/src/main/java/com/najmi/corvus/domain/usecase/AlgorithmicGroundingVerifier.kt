package com.najmi.corvus.domain.usecase

import com.najmi.corvus.domain.model.CitationConfidence
import com.najmi.corvus.domain.model.FactVerification
import com.najmi.corvus.domain.model.GroundedFact
import com.najmi.corvus.domain.model.Source
import com.najmi.corvus.domain.util.StringSimilarity
import javax.inject.Inject

class AlgorithmicGroundingVerifier @Inject constructor() {

    companion object {
        // Similarity thresholds
        const val DIRECT_QUOTE_VERIFIED_THRESHOLD = 0.80f   // 80% match = verified quote
        const val DIRECT_QUOTE_PARTIAL_THRESHOLD  = 0.55f   // 55-79% = partial match
        // Below 55% → citation stripped, flagged LOW_CONFIDENCE

        // Confidence penalties applied to overall result
        const val FABRICATED_CITATION_PENALTY     = 0.15f   // Per fabricated direct quote
        const val MAX_TOTAL_PENALTY               = 0.40f   // Cap total penalty
    }

    data class GroundingVerificationResult(
        val verifiedFacts         : List<GroundedFact>,
        val totalConfidencePenalty: Float,
        val fabricatedCitations   : List<FabricatedCitation>
    )

    data class FabricatedCitation(
        val factIndex      : Int,
        val originalStatement: String,
        val claimedSourceIndex: Int,
        val bestSimilarityScore: Float
    )

    fun verify(
        facts   : List<GroundedFact>,
        sources : List<Source>
    ): GroundingVerificationResult {

        val verifiedFacts          = mutableListOf<GroundedFact>()
        val fabricatedCitations    = mutableListOf<FabricatedCitation>()
        var totalPenalty           = 0f

        facts.forEachIndexed { index, fact ->

            // Only algorithmically verify direct quotes
            // Paraphrases handled by RAG Verifier
            if (!fact.isDirectQuote || fact.sourceIndex == null) {
                verifiedFacts.add(fact)
                return@forEachIndexed
            }

            val claimedSource = sources.getOrNull(fact.sourceIndex)
            val sourceText    = claimedSource?.rawContent
                ?: claimedSource?.snippet
                ?: ""

            if (sourceText.isBlank()) {
                // Source has no text to verify against — cannot confirm, flag it
                verifiedFacts.add(
                    fact.copy(
                        isDirectQuote = false,   // Downgrade from direct quote
                        sourceIndex   = null,    // Strip unverifiable citation
                        verification  = FactVerification(
                            factIndex          = index,
                            confidence         = CitationConfidence.LOW_CONFIDENCE,
                            coverageScore      = 0f,
                            matchedFragment    = null,
                            matchedSourceIndex = null
                        )
                    )
                )
                totalPenalty = minOf(totalPenalty + FABRICATED_CITATION_PENALTY, MAX_TOTAL_PENALTY)
                return@forEachIndexed
            }

            // Extract the quoted portion from the statement
            val quotedText = extractQuotedText(fact.statement)
                ?: fact.statement   // If no quotes found, use full statement

            // Search for the quote in the source text
            val similarityScore = StringSimilarity.bestSubstringMatch(quotedText, sourceText)

            when {
                similarityScore >= DIRECT_QUOTE_VERIFIED_THRESHOLD -> {
                    // Quote verified — find and attach the matched fragment
                    val fragment = findMatchedFragment(quotedText, sourceText)
                    verifiedFacts.add(
                        fact.copy(
                            verification = FactVerification(
                                factIndex          = index,
                                confidence         = CitationConfidence.VERIFIED,
                                coverageScore      = similarityScore,
                                matchedFragment    = fragment,
                                matchedSourceIndex = fact.sourceIndex
                            )
                        )
                    )
                }

                similarityScore >= DIRECT_QUOTE_PARTIAL_THRESHOLD -> {
                    // Partial match — downgrade from direct quote but keep citation
                    verifiedFacts.add(
                        fact.copy(
                            isDirectQuote = false,   // No longer claiming verbatim
                            verification  = FactVerification(
                                factIndex          = index,
                                confidence         = CitationConfidence.PARTIAL,
                                coverageScore      = similarityScore,
                                matchedFragment    = null,
                                matchedSourceIndex = fact.sourceIndex
                            )
                        )
                    )
                }

                else -> {
                    // Fabricated citation — strip it
                    fabricatedCitations.add(
                        FabricatedCitation(
                            factIndex              = index,
                            originalStatement      = fact.statement,
                            claimedSourceIndex     = fact.sourceIndex,
                            bestSimilarityScore    = similarityScore
                        )
                    )
                    totalPenalty = minOf(
                        totalPenalty + FABRICATED_CITATION_PENALTY,
                        MAX_TOTAL_PENALTY
                    )

                    // Strip citation — fact remains but is unattributed
                    verifiedFacts.add(
                        fact.copy(
                            sourceIndex   = null,
                            isDirectQuote = false,
                            verification  = FactVerification(
                                factIndex          = index,
                                confidence         = CitationConfidence.LOW_CONFIDENCE,
                                coverageScore      = similarityScore,
                                matchedFragment    = null,
                                matchedSourceIndex = null
                            )
                        )
                    )
                }
            }
        }

        return GroundingVerificationResult(
            verifiedFacts          = verifiedFacts,
            totalConfidencePenalty = totalPenalty,
            fabricatedCitations    = fabricatedCitations
        )
    }

    // Extract text within quotation marks from a fact statement
    private fun extractQuotedText(statement: String): String? {
        val patterns = listOf(
            Regex(""""([^"]{10,})""""),          // Standard double quotes
            Regex("""'([^']{10,})'"""),           // Single quotes
            Regex("""\u201C([^\u201D]{10,})\u201D""")  // Unicode smart quotes
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(statement)?.groupValues?.getOrNull(1)
        }
    }

    // Find the sentence in source text that best matches the quote
    private fun findMatchedFragment(quote: String, sourceText: String): String? {
        val sentences = sourceText.split(Regex("[.!?]\\s+"))
        return sentences
            .map { sentence ->
                sentence to StringSimilarity.normalizedLevenshtein(
                    quote.lowercase().take(100),
                    sentence.lowercase().take(100)
                )
            }
            .filter { (_, score) -> score >= 0.50f }
            .maxByOrNull { (_, score) -> score }
            ?.first
            ?.let { "\"...${it.trim()}...\"" }
    }
}
