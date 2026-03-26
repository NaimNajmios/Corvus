package com.najmi.corvus.domain.usecase

import com.najmi.corvus.domain.model.ImpliedTimeline
import com.najmi.corvus.domain.model.Source
import com.najmi.corvus.domain.model.SourceMismatch
import com.najmi.corvus.domain.model.TemporalClaimProfile
import com.najmi.corvus.domain.model.TemporalMismatchReport
import com.najmi.corvus.domain.model.TemporalUrgency
import com.najmi.corvus.domain.model.Verdict
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceTemporalMismatchDetector @Inject constructor() {

    companion object {
        const val ZOMBIE_THRESHOLD_DAYS = 180
        const val STRONG_MISMATCH_DAYS = 365
    }

    fun detect(
        sources: List<Source>,
        profile: TemporalClaimProfile,
        currentDate: LocalDate = LocalDate.now()
    ): TemporalMismatchReport {
        if (profile.temporalUrgency == TemporalUrgency.LOW) {
            return TemporalMismatchReport(
                hasSignificantMismatch = false,
                oldestSourceAge = null,
                newestSourceAge = null,
                sourcesWithDates = sources.count { it.publicationDate != null },
                sourcesWithoutDates = sources.count { it.publicationDate == null },
                mismatchDetails = emptyList(),
                suggestedVerdict = null
            )
        }

        val datedSources = sources.mapIndexedNotNull { index, source ->
            val epochDay = source.publicationDate?.epochDay ?: return@mapIndexedNotNull null
            val sourceDate = LocalDate.ofEpochDay(epochDay)
            val ageDays = ChronoUnit.DAYS.between(sourceDate, currentDate).toInt()

            if (ageDays > 0) {
                SourceMismatch(
                    sourceIndex = index,
                    sourceDate = source.publicationDate?.formattedDisplay ?: sourceDate.toString(),
                    ageDays = ageDays,
                    publisher = source.publisher
                )
            } else null
        }

        val significantMismatches = datedSources.filter { it.ageDays > ZOMBIE_THRESHOLD_DAYS }
        val strongMismatches = datedSources.filter { it.ageDays > STRONG_MISMATCH_DAYS }

        val hasSignificantMismatch = profile.impliedTimeline == ImpliedTimeline.CURRENT &&
            significantMismatches.size >= (datedSources.size * 0.6).toInt() &&
            datedSources.isNotEmpty()

        val suggestedVerdict = if (hasSignificantMismatch &&
            strongMismatches.size >= (datedSources.size * 0.5).toInt()
        ) {
            Verdict.MISLEADING
        } else null

        return TemporalMismatchReport(
            hasSignificantMismatch = hasSignificantMismatch,
            oldestSourceAge = datedSources.maxOfOrNull { it.ageDays },
            newestSourceAge = datedSources.minOfOrNull { it.ageDays },
            sourcesWithDates = datedSources.size,
            sourcesWithoutDates = sources.size - datedSources.size,
            mismatchDetails = significantMismatches,
            suggestedVerdict = suggestedVerdict
        )
    }
}
