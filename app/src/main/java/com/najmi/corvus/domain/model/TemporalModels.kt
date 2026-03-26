package com.najmi.corvus.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TemporalClaimProfile(
    val impliedTimeline: ImpliedTimeline,
    val claimDateSignals: List<String>,
    val isLikelyZombie: Boolean,
    val temporalUrgency: TemporalUrgency
)

@Serializable
enum class ImpliedTimeline {
    CURRENT,
    RECENT,
    HISTORICAL,
    TIMELESS,
    UNDETECTED
}

@Serializable
enum class TemporalUrgency {
    HIGH,
    MEDIUM,
    LOW
}

@Serializable
data class TemporalMismatchReport(
    val hasSignificantMismatch: Boolean,
    val oldestSourceAge: Int?,
    val newestSourceAge: Int?,
    val sourcesWithDates: Int,
    val sourcesWithoutDates: Int,
    val mismatchDetails: List<SourceMismatch>,
    val suggestedVerdict: Verdict?
)

@Serializable
data class SourceMismatch(
    val sourceIndex: Int,
    val sourceDate: String,
    val ageDays: Int,
    val publisher: String?
)
