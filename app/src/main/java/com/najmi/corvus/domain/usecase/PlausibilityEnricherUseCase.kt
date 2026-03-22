package com.najmi.corvus.domain.usecase

import android.util.Log
import com.najmi.corvus.data.remote.GroqClient
import com.najmi.corvus.domain.model.PlausibilityAssessment
import com.najmi.corvus.domain.model.PlausibilityScore
import com.najmi.corvus.domain.model.Source
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
private data class PlausibilityResponse(
    val score: String,
    val reasoning: String,
    @SerialName("closest_evidence") val closestEvidence: String? = null
)

class PlausibilityEnricherUseCase @Inject constructor(
    private val groqClient: GroqClient,
    private val json: Json
) {
    companion object {
        private const val TAG = "PlausibilityEnricher"
    }

    suspend fun enrich(
        claim: String,
        sources: List<Source>,
        initialAssessment: PlausibilityAssessment?
    ): PlausibilityAssessment {

        // Skip enrichment if initial assessment is already strongly scored
        if (initialAssessment?.score == PlausibilityScore.IMPLAUSIBLE ||
            initialAssessment?.score == PlausibilityScore.PROBABLE) {
            return initialAssessment
        }

        val sourcesContext = sources.take(3).mapIndexed { i, s -> 
            "[${i+1}] ${s.title}: ${s.snippet?.take(200)}" 
        }.joinToString("\n")

        val prompt = """
            A fact-check of the following claim returned UNVERIFIABLE.
            
            CLAIM: "$claim"
            
            RETRIEVED SOURCES (for context):
            $sourcesContext
            
            Based on established knowledge, science, documented history, and the
            sources above — how plausible is this claim, even if it cannot be
            directly verified?
            
            Respond ONLY with valid JSON:
            {
              "score": "IMPLAUSIBLE|UNLIKELY|NEUTRAL|PLAUSIBLE|PROBABLE",
              "reasoning": "1-2 sentences",
              "closest_evidence": "Best available evidence hint, even if inconclusive"
            }
        """.trimIndent()

        return try {
            val response = groqClient.chat(prompt)
            val cleanedText = response
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            
            val parsed = json.decodeFromString<PlausibilityResponse>(cleanedText)
            val score = runCatching { 
                PlausibilityScore.valueOf(parsed.score.uppercase()) 
            }.getOrDefault(PlausibilityScore.NEUTRAL)
            
            PlausibilityAssessment(
                score = score,
                reasoning = parsed.reasoning,
                closestEvidence = parsed.closestEvidence
            )
        } catch (e: Exception) {
            Log.e(TAG, "Enrichment failed: ${e.message}")
            initialAssessment ?: PlausibilityAssessment(PlausibilityScore.NEUTRAL, "Analysis failed", null)
        }
    }
}
