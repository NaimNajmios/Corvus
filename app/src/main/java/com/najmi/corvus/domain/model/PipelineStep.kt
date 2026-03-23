package com.najmi.corvus.domain.model

enum class PipelineStep {
    IDLE,
    CHECKING_VIRAL_DATABASE,
    CHECKING_KNOWN_FACTS,
    DISSECTING,
    CHECKING_SUB_CLAIMS,
    RETRIEVING_SOURCES,
    ANALYZING,
    DONE
}

fun PipelineStep.displayLabel(): String = when (this) {
    PipelineStep.IDLE -> "Idle"
    PipelineStep.CHECKING_VIRAL_DATABASE -> "Checking Viral Database"
    PipelineStep.CHECKING_KNOWN_FACTS -> "Checking Known Facts"
    PipelineStep.DISSECTING -> "Dissecting"
    PipelineStep.CHECKING_SUB_CLAIMS -> "Checking Sub-Claims"
    PipelineStep.RETRIEVING_SOURCES -> "Retrieving Sources"
    PipelineStep.ANALYZING -> "Analyzing"
    PipelineStep.DONE -> "Done"
}
