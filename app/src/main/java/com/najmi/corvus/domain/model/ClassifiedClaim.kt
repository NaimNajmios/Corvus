package com.najmi.corvus.domain.model

data class ClassifiedClaim(
    val raw: String,
    val type: ClaimType,
    val speaker: String? = null,        // Populated if QUOTE
    val quotedText: String? = null,     // Extracted quote if QUOTE
    val claimedDate: String? = null,    // Date context if present
    val entities: List<String> = emptyList()
)
