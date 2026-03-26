package com.najmi.corvus.domain.remote.llm

import com.najmi.corvus.domain.model.ImpliedTimeline
import com.najmi.corvus.domain.model.Source
import com.najmi.corvus.domain.model.TemporalClaimProfile
import com.najmi.corvus.domain.model.TemporalMismatchReport
import com.najmi.corvus.domain.model.TemporalUrgency
import com.najmi.corvus.domain.usecase.SourceTemporalMismatchDetector
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object TemporalPromptInjector {

    fun buildTemporalContext(
        currentDate: LocalDate = LocalDate.now(),
        profile: TemporalClaimProfile,
        mismatchReport: TemporalMismatchReport,
        sources: List<Source>
    ): String = buildString {
        appendLine("TEMPORAL CONTEXT:")
        appendLine("Current date: ${currentDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))}")
        appendLine()

        appendLine("Claim's implied timeline: ${profile.impliedTimeline.name}")
        if (profile.claimDateSignals.isNotEmpty()) {
            appendLine("Date signals in claim: ${profile.claimDateSignals.joinToString(", ")}")
        }
        appendLine()

        appendLine("SOURCE PUBLICATION DATES:")
        sources.forEachIndexed { index, source ->
            val dateInfo = source.publicationDate?.let {
                "${it.formattedDisplay} (${it.confidence.name})"
            } ?: "DATE UNKNOWN"
            appendLine("  Source [$index] ${source.publisher ?: "Unknown"}: $dateInfo")
        }
        appendLine()

        if (mismatchReport.hasSignificantMismatch) {
            appendLine("TEMPORAL MISMATCH DETECTED:")
            appendLine("The claim implies a current or recent event, but ${mismatchReport.mismatchDetails.size} source(s)")
            appendLine("are more than ${SourceTemporalMismatchDetector.ZOMBIE_THRESHOLD_DAYS} days old.")
            mismatchReport.mismatchDetails.forEach { mismatch ->
                appendLine("  - Source [${mismatch.sourceIndex}] (${mismatch.publisher}): ${mismatch.ageDays} days old")
            }
            appendLine()
        }

        appendLine(buildTemporalCheckInstruction(profile, mismatchReport))
    }

    private fun buildTemporalCheckInstruction(
        profile: TemporalClaimProfile,
        mismatchReport: TemporalMismatchReport
    ): String = buildString {
        appendLine("TEMPORAL CHECK RULES (MANDATORY):")
        appendLine()

        when (profile.impliedTimeline) {
            ImpliedTimeline.CURRENT, ImpliedTimeline.RECENT -> {
                appendLine("1. This claim implies a CURRENT or RECENT event.")
                appendLine("   You MUST check the publication dates of all sources.")
                appendLine()
                appendLine("2. ZOMBIE CLAIM RULE:")
                appendLine("   If the sources describe an event that occurred more than 6 months")
                appendLine("   before today's date, and the claim implies this is recent:")
                appendLine("   -> You MUST set verdict to MISLEADING")
                appendLine("   -> You MUST set missing_context.context_type to TEMPORAL")
                appendLine("   -> Explain that sources describe an old event presented as current")
                appendLine()
                appendLine("3. UNDATED SOURCE RULE:")
                appendLine("   If a source has no publication date, do NOT assume it is recent.")
                appendLine("   Note the missing date in your reasoning and weight it lower.")

                if (mismatchReport.hasSignificantMismatch) {
                    appendLine()
                    appendLine("4. PRE-CHECK RESULT: A temporal mismatch has already been detected.")
                    appendLine("   Sources are significantly older than the claim implies.")
                    appendLine("   The burden of proof for NOT returning MISLEADING is HIGH.")
                    mismatchReport.suggestedVerdict?.let {
                        appendLine("   Suggested verdict based on temporal analysis: ${it.name}")
                        appendLine("   Override only if content evidence strongly contradicts this.")
                    }
                }
            }

            ImpliedTimeline.HISTORICAL -> {
                appendLine("1. This claim references a historical event.")
                appendLine("   Source dates are expected to be old — this is NOT a zombie signal.")
                appendLine("   Focus on whether sources describe the SAME historical event.")
                appendLine()
                appendLine("2. DATE ACCURACY RULE:")
                appendLine("   If the claim states a specific date (e.g., 'in 2019') but sources")
                appendLine("   indicate a different date, flag this as MISLEADING with TEMPORAL context.")
            }

            ImpliedTimeline.TIMELESS -> {
                appendLine("1. This claim makes no specific temporal reference.")
                appendLine("   Check source dates to see if the claim has become outdated.")
                appendLine("   If sources are from 3+ years ago and the topic may have changed,")
                appendLine("   note this in missing_context as TEMPORAL.")
            }

            ImpliedTimeline.UNDETECTED -> {
                appendLine("1. No temporal signals detected in the claim.")
                appendLine("   Note source publication dates in your reasoning.")
            }
        }
    }
}
