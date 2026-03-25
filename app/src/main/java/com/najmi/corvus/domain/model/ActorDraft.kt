package com.najmi.corvus.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ActorDraft(
    @SerialName("evidentiary_analysis") val reasoningScratchpad: String = "",
    @SerialName("draft_verdict") val draftVerdict: String = "UNVERIFIABLE",
    @SerialName("draft_confidence") val draftConfidence: Float = 0f,
    @SerialName("draft_explanation") val draftExplanation: String = "",
    @SerialName("draft_key_facts") val draftKeyFacts: List<ActorGroundedFact> = emptyList(),
    @SerialName("sources_used") val sourcesUsed: List<Int> = emptyList(),
    @SerialName("unsupported_assumptions") val unsupportedAssumptions: List<String> = emptyList()
)

@Serializable
data class ActorGroundedFact(
    val statement: String,
    @SerialName("source_index") val sourceIndex: Int? = null,
    @SerialName("is_direct_quote") val isDirectQuote: Boolean = false,
    @SerialName("source_text_evidence") val sourceTextEvidence: String? = null
)
