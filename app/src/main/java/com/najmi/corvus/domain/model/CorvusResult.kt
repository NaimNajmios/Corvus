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
        override val id: String = "",
        override val claim: String = "",
        val verdict: Verdict = Verdict.UNVERIFIABLE,
        override val confidence: Float = 0f,
        val explanation: String = "",
        val keyFacts: List<String> = emptyList(),
        override val sources: List<Source> = emptyList(),
        override val providerUsed: String = "unknown",
        val language: ClaimLanguage = ClaimLanguage.UNKNOWN,
        override val checkedAt: Long = System.currentTimeMillis(),
        val isFromKnownFactCheck: Boolean = false,
        val claimType: ClaimType = ClaimType.GENERAL,
        val confidenceTimeline: List<ConfidencePoint> = emptyList()
    ) : CorvusCheckResult()

    @Serializable
    data class QuoteResult(
        override val id: String = "",
        override val claim: String = "",
        val quoteVerdict: QuoteVerdict = QuoteVerdict.UNVERIFIABLE,
        override val confidence: Float = 0f,
        val speaker: String = "Unknown",
        val originalQuote: String? = null,
        val submittedQuote: String = "",
        val originalSource: Source? = null,
        val originalDate: String? = null,
        val contextExplanation: String = "",
        override val sources: List<Source> = emptyList(),
        val isVerbatim: Boolean = false,
        val contextAccurate: Boolean = false,
        override val providerUsed: String = "unknown",
        override val checkedAt: Long = System.currentTimeMillis(),
        val confidenceTimeline: List<ConfidencePoint> = emptyList()
    ) : CorvusCheckResult()

    @Serializable
    data class CompositeResult(
        override val id: String = "",
        override val claim: String = "",
        val subClaims: List<SubClaim> = emptyList(),
        val compositeVerdict: Verdict = Verdict.UNVERIFIABLE,
        override val confidence: Float = 0f,
        val compositeSummary: String = "",
        override val sources: List<Source> = emptyList(),
        override val providerUsed: String = "Corvus Aggregator",
        override val checkedAt: Long = System.currentTimeMillis(),
        val confidenceTimeline: List<ConfidencePoint> = emptyList()
    ) : CorvusCheckResult()

    @Serializable
    data class ViralHoaxResult(
        override val id: String = "",
        override val claim: String = "",
        val matchedClaim: String = "",
        val summary: String = "",
        val debunkUrls: List<String> = emptyList(),
        override val confidence: Float = 0f,
        val firstSeen: String? = null,
        override val sources: List<Source> = emptyList(),
        override val providerUsed: String = "Viral Detector",
        override val checkedAt: Long = 0,
        val confidenceTimeline: List<ConfidencePoint> = emptyList()
    ) : CorvusCheckResult()
}

@Serializable
data class SubClaim(
    val id: String = "",
    val text: String,
    val index: Int,
    val result: CorvusCheckResult? = null
)

@Serializable
data class ConfidencePoint(
    val timestamp: Long,
    val confidence: Float,
    val sourceTitle: String? = null
)

@Serializable
enum class ClaimLanguage {
    ENGLISH,
    BAHASA_MALAYSIA,
    MIXED,
    UNKNOWN
}
