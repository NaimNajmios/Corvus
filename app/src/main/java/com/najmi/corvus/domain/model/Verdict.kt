package com.najmi.corvus.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class Verdict {
    TRUE,
    FALSE,
    MISLEADING,
    PARTIALLY_TRUE,
    UNVERIFIABLE,
    CHECKING,
    NOT_A_CLAIM
}

@Serializable
enum class QuoteVerdict {
    VERIFIED,           // Confirmed verbatim with primary source
    PARAPHRASED,        // Sentiment correct, wording differs
    OUT_OF_CONTEXT,     // Real quote, misleading framing
    MISATTRIBUTED,      // Quote exists but wrong speaker
    FABRICATED,         // No evidence it was ever said
    SATIRE_ORIGIN,      // Originated from satire, reshared as real
    UNVERIFIABLE        // Insufficient sources
}
