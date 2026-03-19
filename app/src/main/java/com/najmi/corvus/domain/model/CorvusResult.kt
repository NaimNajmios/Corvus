package com.najmi.corvus.domain.model

data class CorvusResult(
    val verdict: Verdict,
    val confidence: Float,
    val explanation: String,
    val keyFacts: List<String>,
    val sources: List<Source>
)
