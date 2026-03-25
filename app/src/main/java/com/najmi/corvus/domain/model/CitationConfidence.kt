package com.najmi.corvus.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CitationConfidence {
    VERIFIED,       // Strong keyword overlap found in cited source (score >= 0.7)
    PARTIAL,        // Partial overlap found (score 0.4–0.69)
    LOW_CONFIDENCE, // Minimal or no overlap found (score < 0.4)
    UNATTRIBUTED    // source_index was null — general knowledge claim
}

@Serializable
data class FactVerification(
    val factIndex: Int,
    val confidence: CitationConfidence,
    val coverageScore: Float,           // 0.0–1.0
    val matchedFragment: String? = null,
    val matchedSourceIndex: Int? = null
)

@Serializable
enum class ExplanationConfidence {
    WELL_GROUNDED,    // >= 0.75 grounding ratio
    MOSTLY_GROUNDED,  // 0.5–0.74
    PARTIALLY_GROUNDED, // 0.25–0.49
    POORLY_GROUNDED   // < 0.25
}

@Serializable
data class ExplanationVerification(
    val overallConfidence: ExplanationConfidence,
    val groundedSentences: Int,
    val totalSentences: Int,
    val groundingRatio: Float
)
