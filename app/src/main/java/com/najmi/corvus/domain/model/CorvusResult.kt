package com.najmi.corvus.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

@Serializable
sealed class CorvusCheckResult {
    abstract val id: String
    abstract val claim: String
    abstract val confidence: Float
    abstract val sources: List<Source>
    abstract val providerUsed: String
    abstract val checkedAt: Long
    abstract val entityContext: EntityContext?
    abstract val methodology: MethodologyMetadata?

    fun withEntityContext(context: EntityContext?): CorvusCheckResult {
        return when (this) {
            is GeneralResult -> this.copy(entityContext = context)
            is QuoteResult -> this.copy(entityContext = context)
            is CompositeResult -> this.copy(entityContext = context)
            is ViralHoaxResult -> this.copy(entityContext = context)
        }
    }

    @Serializable
    data class GeneralResult(
        override val id: String = UUID.randomUUID().toString(),
        override val claim: String = "",
        val verdict: Verdict = Verdict.UNVERIFIABLE,
        override val confidence: Float = 0f,
        val explanation: String = "",
        val keyFacts: List<GroundedFact> = emptyList(),
        override val sources: List<Source> = emptyList(),
        override val providerUsed: String = "unknown",
        val language: ClaimLanguage = ClaimLanguage.UNKNOWN,
        override val checkedAt: Long = System.currentTimeMillis(),
        val isFromKnownFactCheck: Boolean = false,
        val claimType: ClaimType = ClaimType.GENERAL,
        val confidenceTimeline: List<ConfidencePoint> = emptyList(),
        override val entityContext: EntityContext? = null,
        val harmAssessment: HarmAssessment = HarmAssessment(),
        val plausibility: PlausibilityAssessment? = null,
        val kernelOfTruth: KernelOfTruth? = null,
        val missingContext: MissingContextInfo? = null,
        override val methodology: MethodologyMetadata? = null,
        val explanationVerification: ExplanationVerification? = null,
        val actorProvider: LlmProvider? = null,
        val criticProvider: LlmProvider? = null,
        val correctionsLog: List<String>? = null,
        val reasoningScratchpad: String? = null,
        val retrievalMetadata: RetrievalMetadata? = null,
        val temporalMismatch: TemporalMismatchReport? = null,
        val recencySignal: RecencySignal? = null,
        val viralDetection: ViralDetectionResult? = null,
        val verificationStatus: VerificationStatus = VerificationStatus.COMPLETED,
        val noSourceReasons: List<NoSourceReason> = emptyList(),
        val helpRequests: List<HelpRequestInfo> = emptyList(),
        val apiErrorMessage: String? = null,
        val savedProgress: CorvusCheckResult? = null
    ) : CorvusCheckResult()

    @Serializable
    data class QuoteResult(
        override val id: String = UUID.randomUUID().toString(),
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
        val confidenceTimeline: List<ConfidencePoint> = emptyList(),
        override val entityContext: EntityContext? = null,
        override val methodology: MethodologyMetadata? = null,
        val harmAssessment: HarmAssessment = HarmAssessment(),
        val plausibility: PlausibilityAssessment? = null,
        val keyFacts: List<GroundedFact> = emptyList(),
        val verificationStatus: VerificationStatus = VerificationStatus.COMPLETED,
        val noSourceReasons: List<NoSourceReason> = emptyList(),
        val helpRequests: List<HelpRequestInfo> = emptyList(),
        val apiErrorMessage: String? = null
    ) : CorvusCheckResult()

    @Serializable
    data class CompositeResult(
        override val id: String = UUID.randomUUID().toString(),
        override val claim: String = "",
        val subClaims: List<SubClaim> = emptyList(),
        val compositeVerdict: Verdict = Verdict.UNVERIFIABLE,
        override val confidence: Float = 0f,
        val compositeSummary: String = "",
        override val sources: List<Source> = emptyList(),
        override val providerUsed: String = "Corvus Aggregator",
        override val checkedAt: Long = System.currentTimeMillis(),
        val confidenceTimeline: List<ConfidencePoint> = emptyList(),
        override val entityContext: EntityContext? = null,
        override val methodology: MethodologyMetadata? = null,
        val verificationStatus: VerificationStatus = VerificationStatus.COMPLETED,
        val noSourceReasons: List<NoSourceReason> = emptyList(),
        val reasoningScratchpad: String? = null,
        val correctionsLog: List<String>? = null,
        val retrievalMetadata: RetrievalMetadata? = null,
        val holisticVerdict: Verdict? = null,
        val holisticIssues: List<HolisticIssue> = emptyList(),
        val holisticExplanation: String? = null,
        val holisticConfidence: Float? = null,
        val holisticCorrections: List<String> = emptyList()
    ) : CorvusCheckResult()

    @Serializable
    data class ViralHoaxResult(
        override val id: String = UUID.randomUUID().toString(),
        override val claim: String = "",
        val matchedClaim: String = "",
        val summary: String = "",
        val debunkUrls: List<String> = emptyList(),
        override val confidence: Float = 0f,
        val firstSeen: String? = null,
        override val sources: List<Source> = emptyList(),
        override val providerUsed: String = "Viral Detector",
        override val checkedAt: Long = 0,
        val confidenceTimeline: List<ConfidencePoint> = emptyList(),
        override val entityContext: EntityContext? = null,
        override val methodology: MethodologyMetadata? = null,
        val verificationStatus: VerificationStatus = VerificationStatus.COMPLETED,
        val noSourceReasons: List<NoSourceReason> = emptyList()
    ) : CorvusCheckResult()
}

@Serializable
data class SubClaim(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val index: Int,
    val result: CorvusCheckResult? = null,
    val harmAssessment: HarmAssessment? = null  // NEW
)

@Serializable
enum class VerificationStatus {
    COMPLETED,
    NO_SOURCES_FOUND,
    API_FAILURE,
    LOW_CONFIDENCE
}

@Serializable
data class ConfidencePoint(
    val timestamp: Long,
    val confidence: Float,
    val sourceTitle: String? = null,
    val sourceIndex: Int? = null,
    val previousConfidence: Float? = null,
    val changeReason: String? = null,
    val contradictingSourceIndex: Int? = null,
    val id: String = java.util.UUID.randomUUID().toString()
)

@Serializable
enum class NoSourceReason {
    BREAKING_NEWS,
    RECENT_EVENT,
    NICHE_TOPIC,
    NO_INTERNET,
    API_ERROR
}

@Serializable
enum class ConfidenceChangeReason {
    SOURCE_ADDED,
    SOURCE_CONTRADICTED,
    EVIDENCE_WEAKENED,
    NEW_ANALYSIS,
    GROUNDING_CHECK
}

@Serializable
data class HelpRequestInfo(
    val type: HelpRequestType,
    val description: String
)

@Serializable
enum class HelpRequestType {
    MORE_RECENT_SOURCES,
    PRIMARY_SOURCES,
    OFFICIAL_STATEMENTS,
    PEER_REVIEWED,
    ADDITIONAL_CONTEXT
}

@Serializable
enum class ClaimLanguage {
    ENGLISH,
    BAHASA_MALAYSIA,
    MIXED,
    UNKNOWN
}

@Serializable
enum class HarmLevel {
    NONE,       // Factually wrong but harmless
    LOW,        // Minor misinformation
    MODERATE,   // Could cause meaningful harm
    HIGH        // Dangerous
}

@Serializable
enum class HarmCategory {
    NONE,
    HEALTH,
    SAFETY,
    RACIAL_ETHNIC,
    RELIGIOUS,
    POLITICAL,
    FINANCIAL
}

@Serializable
data class HarmAssessment(
    val level: HarmLevel = HarmLevel.NONE,
    val category: HarmCategory = HarmCategory.NONE,
    val reason: String = ""
)

@Serializable
enum class PlausibilityScore {
    IMPLAUSIBLE,
    UNLIKELY,
    NEUTRAL,
    PLAUSIBLE,
    PROBABLE
}

@Serializable
data class PlausibilityAssessment(
    val score: PlausibilityScore,
    val reasoning: String,
    val closestEvidence: String? = null
)

@Serializable(with = GroundedFactSerializer::class)
data class GroundedFact(
    val statement: String,
    val sourceIndex: Int?,
    val isDirectQuote: Boolean = false,
    val verification: FactVerification? = null,
    val id: String = java.util.UUID.randomUUID().toString()
)

object GroundedFactSerializer : KSerializer<GroundedFact> {
    override val descriptor: SerialDescriptor = GroundedFactSurrogate.serializer().descriptor

    override fun deserialize(decoder: Decoder): GroundedFact {
        val input = decoder as? JsonDecoder ?: throw SerializationException("Expected JsonDecoder")
        val element = input.decodeJsonElement()
        return if (element is JsonPrimitive && element.isString) {
            GroundedFact(element.content, null, false, null, java.util.UUID.randomUUID().toString())
        } else {
            val surrogate = input.json.decodeFromJsonElement(GroundedFactSurrogate.serializer(), element)
            GroundedFact(surrogate.statement, surrogate.sourceIndex, surrogate.isDirectQuote, surrogate.verification, surrogate.id)
        }
    }

    override fun serialize(encoder: Encoder, value: GroundedFact) {
        val surrogate = GroundedFactSurrogate(value.statement, value.sourceIndex, value.isDirectQuote, value.verification, value.id)
        encoder.encodeSerializableValue(GroundedFactSurrogate.serializer(), surrogate)
    }
}

@Serializable
private data class GroundedFactSurrogate(
    val statement: String,
    val sourceIndex: Int?,
    val isDirectQuote: Boolean = false,
    val verification: FactVerification? = null,
    val id: String = java.util.UUID.randomUUID().toString()
)

@Serializable
data class KernelOfTruth(
    val trueParts: List<GroundedFact>,
    val falseParts: List<GroundedFact>,
    val twistExplanation: String
)

@Serializable
enum class ContextType {
    TEMPORAL,
    GEOGRAPHIC,
    ATTRIBUTION,
    STATISTICAL,
    SELECTIVE,
    GENERAL
}

@Serializable
data class MissingContextInfo(
    val content: String,
    val contextType: ContextType
)

@Serializable
data class MethodologyMetadata(
    val pipelineStepsCompleted: List<PipelineStepResult>,
    val claimTypeDetected: ClaimType,
    val sourcesRetrieved: Int,
    val avgSourceCredibility: Int,
    val llmProviderUsed: String,
    val checkedAt: Long,
    val routingRationale: String = "",
    val tokens: CheckTokenReport? = null
)

@Serializable
data class PipelineStepResult(
    val step: PipelineStep,
    val outcome: String,
    val id: String = java.util.UUID.randomUUID().toString()
)

fun HarmCategory.displayLabel(): String = when (this) {
    HarmCategory.NONE -> "None"
    HarmCategory.HEALTH -> "Health"
    HarmCategory.SAFETY -> "Safety"
    HarmCategory.RACIAL_ETHNIC -> "Racial/Ethnic"
    HarmCategory.RELIGIOUS -> "Religious"
    HarmCategory.POLITICAL -> "Political"
    HarmCategory.FINANCIAL -> "Financial"
}

@Serializable
enum class RecencySignal {
    BREAKING,
    RECENT,
    HISTORICAL,
    NOT_APPLICABLE
}

@Serializable
sealed class ViralDetectionResult {
    @Serializable
    data object None : ViralDetectionResult()

    @Serializable
    data class KnownHoax(
        val matchedClaim: String,
        val similarityScore: Float,
        val debunkUrls: List<String>
    ) : ViralDetectionResult()

    @Serializable
    data class PossiblyRelated(
        val claim: String,
        val similarityScore: Float
    ) : ViralDetectionResult()
}
