package com.najmi.corvus.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
sealed class CorvusCheckResult {
    abstract val id: String
    abstract val claim: String
    abstract val confidence: Float
    abstract val sources: List<Source>
    abstract val providerUsed: String
    abstract val checkedAt: Long

    @Serializable
    data class GeneralResult(
        override val id: String = UUID.randomUUID().toString(),
        override val claim: String = "",
        val verdict: Verdict,
        override val confidence: Float,
        val explanation: String,
        val keyFacts: List<String>,
        override val sources: List<Source>,
        override val providerUsed: String = "unknown",
        val language: ClaimLanguage = ClaimLanguage.UNKNOWN,
        override val checkedAt: Long = System.currentTimeMillis(),
        val isFromKnownFactCheck: Boolean = false,
        val claimType: ClaimType = ClaimType.GENERAL
    ) : CorvusCheckResult()

    @Serializable
    data class QuoteResult(
        override val id: String = UUID.randomUUID().toString(),
        override val claim: String = "",
        val quoteVerdict: QuoteVerdict,
        override val confidence: Float,
        val speaker: String,
        val originalQuote: String?,
        val submittedQuote: String,
        val originalSource: Source?,
        val originalDate: String?,
        val contextExplanation: String,
        override val sources: List<Source>,
        val isVerbatim: Boolean,
        val contextAccurate: Boolean,
        override val providerUsed: String = "unknown",
        override val checkedAt: Long = System.currentTimeMillis()
    ) : CorvusCheckResult()
}

@Serializable
enum class ClaimLanguage {
    ENGLISH,
    BAHASA_MALAYSIA,
    MIXED,
    UNKNOWN
}
