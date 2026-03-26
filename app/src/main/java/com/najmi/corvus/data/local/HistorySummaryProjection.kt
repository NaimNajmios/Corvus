package com.najmi.corvus.data.local

/**
 * A Room projection used to fetch only history metadata, excluding the heavy JSON data.
 * This prevents CursorWindow (2MB) overflow when fetching large amounts of history.
 */
data class HistorySummaryProjection(
    val id: String,
    val claim: String,
    val resultType: String,
    val verdict: String,
    val confidence: Float,
    val checkedAt: Long,
    val harmLevel: String = "NONE",
    val harmCategory: String = "NONE",
    val plausibilityScore: String? = null
)
