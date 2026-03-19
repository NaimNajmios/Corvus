package com.najmi.corvus.domain.model

enum class PipelineStep {
    IDLE,
    CHECKING_KNOWN_FACTS,
    RETRIEVING_SOURCES,
    ANALYZING,
    DONE
}
