package com.najmi.corvus.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class HistorySummary(
    val id: String,
    val claim: String,
    val resultType: String,
    val verdict: String,
    val confidence: Float,
    val checkedAt: Long,
    val harmLevel: String = "NONE",
    val harmCategory: String = "NONE",
    val plausibilityScore: String? = null,
    val providerUsed: String = "unknown"
)
