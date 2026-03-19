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
