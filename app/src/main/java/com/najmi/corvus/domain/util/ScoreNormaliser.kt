package com.najmi.corvus.domain.util

import com.najmi.corvus.domain.model.MbfcCategory

object ScoreNormaliser {

    // MBFC factual reporting → 0–100
    fun normaliseMbfc(category: MbfcCategory): Int = when (category) {
        MbfcCategory.VERY_HIGH_FACTUAL -> 92
        MbfcCategory.HIGH_FACTUAL      -> 78
        MbfcCategory.MOSTLY_FACTUAL    -> 63
        MbfcCategory.MIXED_FACTUAL     -> 45
        MbfcCategory.LOW_FACTUAL       -> 28
        MbfcCategory.VERY_LOW_FACTUAL  -> 12
        MbfcCategory.SATIRE            -> 50   // Accurate-but-satirical is its own thing
        MbfcCategory.CONSPIRACY         -> 8
        MbfcCategory.PRO_SCIENCE       -> 88
        MbfcCategory.SCIENCE           -> 82
        MbfcCategory.GOVERNMENT        -> 70   // Accurate but may be partisan
    }

    // Ad Fontes reliability score → 0–100
    // Ad Fontes uses 0–64 scale
    fun normaliseAdFontes(reliability: Float): Int =
        ((reliability / 64f) * 100).toInt().coerceIn(0, 100)

    // NewsGuard score → 0–100
    // NewsGuard already uses 0–100 but with specific criteria weights
    fun normaliseNewsGuard(score: Int): Int = score.coerceIn(0, 100)
}
