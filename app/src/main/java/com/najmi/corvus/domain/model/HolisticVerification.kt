package com.najmi.corvus.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class HolisticIssueType {
    MISSING_CONTEXT,
    SELECTIVE_TRUTH,
    CHERRY_PICKING,
    NARRATIVE_SHIFT,
    OMISSION,
    MISLEADING_COMBINATION
}

@Serializable
data class HolisticIssue(
    val type: HolisticIssueType,
    val description: String,
    val affectedSubclaims: List<Int> = emptyList(),
    val severity: IssueSeverity = IssueSeverity.MODERATE
)

@Serializable
enum class IssueSeverity {
    LOW,
    MODERATE,
    HIGH
}

data class HolisticVerificationResult(
    val holisticVerdict: Verdict,
    val holisticExplanation: String,
    val issuesFound: List<HolisticIssue>,
    val confidence: Float,
    val reasoningScratchpad: String? = null,
    val correctionsApplied: List<String> = emptyList()
)
