package com.najmi.corvus.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ClaimType {
    QUOTE,          // "X said Y"
    STATISTICAL,    // "Malaysia's GDP grew by 4.2%"
    HISTORICAL,     // "The Baling Talks happened in 1955"
    SCIENTIFIC,     // "Vitamin C prevents COVID"
    CURRENT_EVENT,  // "Floods hit Kelantan last week"
    PERSON_FACT,    // "Anwar Ibrahim is the 10th PM"
    GENERAL         // Catch-all
}

fun ClaimType.displayLabel(): String = when (this) {
    ClaimType.QUOTE -> "Quote"
    ClaimType.STATISTICAL -> "Statistical"
    ClaimType.HISTORICAL -> "Historical"
    ClaimType.SCIENTIFIC -> "Scientific"
    ClaimType.CURRENT_EVENT -> "Current Event"
    ClaimType.PERSON_FACT -> "Person Fact"
    ClaimType.GENERAL -> "General"
}
