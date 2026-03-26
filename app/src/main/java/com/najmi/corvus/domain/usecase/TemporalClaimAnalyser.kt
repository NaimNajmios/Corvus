package com.najmi.corvus.domain.usecase

import com.najmi.corvus.domain.model.ImpliedTimeline
import com.najmi.corvus.domain.model.TemporalClaimProfile
import com.najmi.corvus.domain.model.TemporalUrgency
import java.time.LocalDate
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemporalClaimAnalyser @Inject constructor() {

    private val currentSignals = listOf(
        "sekarang", "kini", "tadi", "baru", "hari ini", "petang ini",
        "now", "today", "just", "breaking", "tonight", "this morning",
        "currently", "happening", "right now", "at this moment"
    )

    private val recentSignals = listOf(
        "semalam", "minggu lepas", "baru baru ini",
        "yesterday", "last week", "last night", "recently",
        "this month", "this year", "few days ago"
    )

    private val dateYearPattern = Pattern.compile("""\b(20\d{2})\b""")

    fun analyze(claim: String, currentDate: LocalDate = LocalDate.now()): TemporalClaimProfile {
        val lower = claim.lowercase()
        
        val yearsInClaim = mutableListOf<String>()
        val yearMatcher = dateYearPattern.matcher(claim)
        while (yearMatcher.find()) {
            yearsInClaim.add(yearMatcher.group())
        }

        val isCurrent = currentSignals.any { lower.contains(it) }
        val isRecent = recentSignals.any { lower.contains(it) }
        val isHistorical = yearsInClaim.any {
            it.toIntOrNull()?.let { year -> year < currentDate.year - 1 } == true
        }

        val timeline = when {
            isCurrent -> ImpliedTimeline.CURRENT
            isRecent -> ImpliedTimeline.RECENT
            isHistorical -> ImpliedTimeline.HISTORICAL
            yearsInClaim.isNotEmpty() -> ImpliedTimeline.HISTORICAL
            else -> ImpliedTimeline.TIMELESS
        }

        val isLikelyZombie = isCurrent || isRecent

        val urgency = when {
            isCurrent -> TemporalUrgency.HIGH
            isRecent -> TemporalUrgency.MEDIUM
            else -> TemporalUrgency.LOW
        }

        return TemporalClaimProfile(
            impliedTimeline = timeline,
            claimDateSignals = yearsInClaim,
            isLikelyZombie = isLikelyZombie,
            temporalUrgency = urgency
        )
    }
}
