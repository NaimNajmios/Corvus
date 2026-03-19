package com.najmi.corvus.domain.model

import java.util.UUID

data class CorvusResult(
    val id: String = UUID.randomUUID().toString(),
    val claim: String = "",
    val verdict: Verdict,
    val confidence: Float,
    val explanation: String,
    val keyFacts: List<String>,
    val sources: List<Source>,
    val providerUsed: String = "unknown",
    val language: ClaimLanguage = ClaimLanguage.UNKNOWN,
    val checkedAt: Long = System.currentTimeMillis(),
    val isFromKnownFactCheck: Boolean = false
)

enum class ClaimLanguage {
    ENGLISH,
    BAHASA_MALAYSIA,
    MIXED,
    UNKNOWN
}
