package com.najmi.corvus.domain.model

fun CorvusCheckResult.toShareText(): String {
    val r = this
    val sourceUrls = r.sources.joinToString("\n") { "• ${it.url}" }
    
    return buildString {
        appendLine("🔍 Fact Check by Corvus")
        appendLine()
        appendLine("Claim: ${r.claim}")
        appendLine()
        
        when (r) {
            is CorvusCheckResult.GeneralResult -> {
                appendLine("Verdict: ${r.verdict.name.replace("_", " ")}")
                if (r.verdict == Verdict.UNVERIFIABLE && r.plausibility != null) {
                    appendLine("Plausibility: ${r.plausibility.score.name}")
                }
                appendLine("Confidence: ${(r.confidence * 100).toInt()}%")
                
                if (r.harmAssessment.level != HarmLevel.NONE) {
                    appendLine()
                    appendLine("⚠️ HARM RISK: ${r.harmAssessment.level}")
                    appendLine("Category: ${r.harmAssessment.category}")
                    appendLine("Reason: ${r.harmAssessment.reason}")
                }

                appendLine()
                appendLine("Explanation:")
                appendLine(r.explanation)
                if (r.keyFacts.isNotEmpty()) {
                    appendLine()
                    appendLine("Key Facts:")
                    r.keyFacts.forEach { appendLine("• $it") }
                }
            }
            is CorvusCheckResult.QuoteResult -> {
                appendLine("Verdict: ${r.quoteVerdict.name.replace("_", " ")}")
                if (r.quoteVerdict == QuoteVerdict.UNVERIFIABLE && r.plausibility != null) {
                    appendLine("Plausibility: ${r.plausibility.score.name}")
                }
                appendLine("Speaker: ${r.speaker}")
                appendLine("Confidence: ${(r.confidence * 100).toInt()}%")

                if (r.harmAssessment.level != HarmLevel.NONE) {
                    appendLine()
                    appendLine("⚠️ HARM RISK: ${r.harmAssessment.level}")
                    appendLine("Category: ${r.harmAssessment.category}")
                    appendLine("Reason: ${r.harmAssessment.reason}")
                }

                appendLine()
                appendLine("Context:")
                appendLine(r.contextExplanation)
            }
            is CorvusCheckResult.CompositeResult -> {
                appendLine("Overall Verdict: ${r.compositeVerdict.name.replace("_", " ")}")
                appendLine("Avg. Confidence: ${(r.confidence * 100).toInt()}%")
                appendLine()
                appendLine("Sub-claims:")
                appendLine(r.compositeSummary)
            }
            is CorvusCheckResult.ViralHoaxResult -> {
                appendLine("🚨 KNOWN HOAX DETECTED")
                appendLine("Match: ${r.matchedClaim}")
                appendLine("Summary: ${r.summary}")
                if (r.debunkUrls.isNotEmpty()) {
                    appendLine("Sources: ${r.debunkUrls.joinToString(", ")}")
                }
            }
        }

        if (r.sources.isNotEmpty()) {
            appendLine()
            appendLine("Sources:")
            append(sourceUrls)
        }
    }
}
