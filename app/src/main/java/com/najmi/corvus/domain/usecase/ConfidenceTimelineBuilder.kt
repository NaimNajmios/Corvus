package com.najmi.corvus.domain.usecase

import com.najmi.corvus.data.repository.HistoryRepository
import com.najmi.corvus.domain.model.ConfidencePoint
import com.najmi.corvus.domain.model.CorvusCheckResult
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ConfidenceTimelineBuilder @Inject constructor(
    private val historyRepository: HistoryRepository
) {
    /**
     * Finds historical matches for a claim and builds a timeline of confidence scores.
     * Includes the current (new) result as the final point.
     */
    suspend fun buildTimeline(
        claim: String, 
        currentConfidence: Float,
        currentTimestamp: Long = System.currentTimeMillis(),
        sourceTitle: String? = "Latest Analysis"
    ): List<ConfidencePoint> {
        // Simple word-based match for historical context
        val words = claim.lowercase().split(Regex("\\W+")).filter { it.length > 3 }
        
        val history = historyRepository.getAllHistorySummaries().first()
        val related = history.filter { past ->
            val pastWords = past.claim.lowercase().split(Regex("\\W+"))
            words.any { it in pastWords }
        }.sortedBy { it.checkedAt }

        val timeline = related.map { 
            ConfidencePoint(
                timestamp = it.checkedAt,
                confidence = it.confidence,
                sourceTitle = it.providerUsed
            )
        }.toMutableList()

        // Append current result
        timeline.add(ConfidencePoint(currentTimestamp, currentConfidence, sourceTitle))
        
        // Ensure strictly sorted by time and distinct (roughly)
        return timeline.distinctBy { it.timestamp / 1000 }.sortedBy { it.timestamp }
    }
}
