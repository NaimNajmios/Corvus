package com.najmi.corvus.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TopicCredibilityProfile(
    val overall: Int,
    val byTopic: Map<ClaimType, Int> = emptyMap(),
    val flags: Set<CredibilityFlag> = emptySet()
)

@Serializable
enum class CredibilityFlag {
    STRONG_SCIENTIFIC_REPORTING,
    WEAK_SCIENTIFIC_REPORTING,
    GOVERNMENT_AFFILIATED,
    SATIRE,
    CLICKBAIT_HEADLINES,
    PAYWALLED,
    FACT_CHECKER,
    PRIMARY_SOURCE,
    SYNDICATED,
    USER_GENERATED
}

enum class RatingAge {
    FRESH,       // < 6 months
    RECENT,      // 6-18 months
    STALE,       // 18-36 months
    VERY_STALE   // 3+ years
}
