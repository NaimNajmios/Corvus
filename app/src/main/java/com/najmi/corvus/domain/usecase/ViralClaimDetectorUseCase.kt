package com.najmi.corvus.domain.usecase

import android.util.Log
import com.najmi.corvus.data.local.ViralHoaxDao
import com.najmi.corvus.data.local.ViralHoaxEntity
import com.najmi.corvus.data.remote.JunkipediaClient
import com.najmi.corvus.domain.model.CorvusCheckResult
import java.util.UUID
import javax.inject.Inject

class ViralClaimDetectorUseCase @Inject constructor(
    private val junkipediaClient: JunkipediaClient,
    private val viralHoaxDao: ViralHoaxDao
) {
    companion object {
        private const val TAG = "ViralClaimDetector"
        private const val SIMILARITY_THRESHOLD_HARD = 0.85f
        private const val SIMILARITY_THRESHOLD_SOFT = 0.60f
    }

    suspend fun check(claim: String): CorvusCheckResult.ViralHoaxResult? {
        // 1. Check local cache
        val localHoaxes = viralHoaxDao.getAllHoaxes()
        val bestLocalMatch = findBestMatch(claim, localHoaxes.map { it.claim to it })
        
        if (bestLocalMatch != null && bestLocalMatch.first >= SIMILARITY_THRESHOLD_HARD) {
            Log.d(TAG, "Hard match found in local cache: ${bestLocalMatch.first}")
            return mapToResult(claim, bestLocalMatch.second, bestLocalMatch.first)
        }

        // 2. Check remote Junkipedia
        val remoteHits = junkipediaClient.search(claim)
        val bestRemoteMatch = findBestMatch(claim, remoteHits.map { it.text to it })

        if (bestRemoteMatch != null && bestRemoteMatch.first >= SIMILARITY_THRESHOLD_SOFT) {
            val hit = bestRemoteMatch.second
            // Cache it
            val entity = ViralHoaxEntity(
                id = hit.id,
                claim = hit.text,
                summary = hit.label ?: "Known misinformation pattern detected.",
                debunkUrls = hit.debunk_url ?: "",
                firstSeen = hit.first_seen
            )
            viralHoaxDao.insertHoax(entity)

            if (bestRemoteMatch.first >= SIMILARITY_THRESHOLD_HARD) {
                Log.d(TAG, "Hard match found in Junkipedia: ${bestRemoteMatch.first}")
                return mapToResult(claim, entity, bestRemoteMatch.first)
            }
        }

        return null
    }

    private fun <T> findBestMatch(query: String, targets: List<Pair<String, T>>): Pair<Float, T>? {
        return targets.map { (text, item) ->
            calculateJaccardSimilarity(query, text) to item
        }.maxByOrNull { it.first }
    }

    private fun calculateJaccardSimilarity(s1: String, s2: String): Float {
        val tokens1 = s1.lowercase().split(Regex("\\W+")).filter { it.isNotBlank() }.toSet()
        val tokens2 = s2.lowercase().split(Regex("\\W+")).filter { it.isNotBlank() }.toSet()
        
        if (tokens1.isEmpty() && tokens2.isEmpty()) return 1.0f
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0f
        
        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size
        
        return intersection.toFloat() / union.toFloat()
    }

    private fun mapToResult(
        inputClaim: String, 
        hoax: ViralHoaxEntity, 
        similarity: Float
    ): CorvusCheckResult.ViralHoaxResult {
        return CorvusCheckResult.ViralHoaxResult(
            id = UUID.randomUUID().toString(),
            claim = inputClaim,
            matchedClaim = hoax.claim,
            summary = hoax.summary,
            debunkUrls = hoax.debunkUrls.split(",").filter { it.isNotBlank() },
            confidence = similarity,
            firstSeen = hoax.firstSeen,
            methodology = com.najmi.corvus.domain.model.MethodologyMetadata(
                pipelineStepsCompleted = listOf(
                    com.najmi.corvus.domain.model.PipelineStepResult(
                        com.najmi.corvus.domain.model.PipelineStep.CHECKING_VIRAL_DATABASE, 
                        "Matched known misinformation pattern (${(similarity * 100).toInt()}% match)"
                    )
                ),
                claimTypeDetected = com.najmi.corvus.domain.model.ClaimType.GENERAL,
                sourcesRetrieved = hoax.debunkUrls.split(",").filter { it.isNotBlank() }.size,
                avgSourceCredibility = 100,
                llmProviderUsed = "Deterministic Matcher",
                checkedAt = System.currentTimeMillis()
            )
        )
    }
}
