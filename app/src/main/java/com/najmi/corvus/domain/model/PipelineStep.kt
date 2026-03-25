package com.najmi.corvus.domain.model

data class CheckingStatus(
    val status: String,
    val progress: Int,
    val isComplete: Boolean = false
) {
    companion object {
        val IDLE = CheckingStatus("Idle", 0)
        val CHECKING_VIRAL_DATABASE = CheckingStatus("Checking viral database...", 15)
        val CHECKING_KNOWN_FACTS = CheckingStatus("Checking known facts...", 30)
        val DISSECTING = CheckingStatus("Dissecting claim...", 45)
        val CHECKING_SUB_CLAIMS = CheckingStatus("Checking sub-claims...", 60)
        val RETRIEVING_SOURCES = CheckingStatus("Retrieving sources...", 75)
        val ANALYZING = CheckingStatus("Drafting analysis...", 60)
        val VERIFYING = CheckingStatus("Verifying draft...", 75)
        val GROUNDING_CHECK = CheckingStatus("Grounding check...", 90)
        val DONE = CheckingStatus("Analysis complete", 100, true)

        fun fromStep(step: String): CheckingStatus {
            return when (step) {
                "CHECKING_VIRAL_DATABASE" -> CHECKING_VIRAL_DATABASE
                "CHECKING_KNOWN_FACTS" -> CHECKING_KNOWN_FACTS
                "DISSECTING" -> DISSECTING
                "CHECKING_SUB_CLAIMS" -> CHECKING_SUB_CLAIMS
                "RETRIEVING_SOURCES" -> RETRIEVING_SOURCES
                "ANALYZING" -> ANALYZING
                "VERIFYING" -> VERIFYING
                "GROUNDING_CHECK" -> GROUNDING_CHECK
                "DONE" -> DONE
                else -> IDLE
            }
        }
    }
}

@Deprecated("Use CheckingStatus data class instead", ReplaceWith("CheckingStatus"))
enum class PipelineStep {
    IDLE,
    CHECKING_VIRAL_DATABASE,
    CHECKING_KNOWN_FACTS,
    DISSECTING,
    CHECKING_SUB_CLAIMS,
    RETRIEVING_SOURCES,
    ANALYZING,
    VERIFYING,
    GROUNDING_CHECK,
    DONE
}

@Deprecated("Use CheckingStatus directly instead", ReplaceWith("CheckingStatus"))
fun PipelineStep.displayLabel(): String = when (this) {
    PipelineStep.IDLE -> "Idle"
    PipelineStep.CHECKING_VIRAL_DATABASE -> "Checking Viral Database"
    PipelineStep.CHECKING_KNOWN_FACTS -> "Checking Known Facts"
    PipelineStep.DISSECTING -> "Dissecting"
    PipelineStep.CHECKING_SUB_CLAIMS -> "Sub-Claims"
    PipelineStep.RETRIEVING_SOURCES -> "Retrieving Sources"
    PipelineStep.ANALYZING -> "Drafting Analysis"
    PipelineStep.VERIFYING -> "Verifying Draft"
    PipelineStep.GROUNDING_CHECK -> "Grounding Check"
    PipelineStep.DONE -> "Done"
}

fun CheckingStatus.displayLabel(): String = status
