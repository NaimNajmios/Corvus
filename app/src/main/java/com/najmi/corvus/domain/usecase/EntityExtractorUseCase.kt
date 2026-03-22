package com.najmi.corvus.domain.usecase

import com.najmi.corvus.data.remote.GroqClient
import com.najmi.corvus.domain.model.ClassifiedClaim
import javax.inject.Inject

class EntityExtractorUseCase @Inject constructor(
    private val groqClient: GroqClient
) {
    companion object {
        private val NAMED_ENTITY_PATTERN = Regex(
            """(?:^|\s)([A-Z][a-z]+(?:\s+[A-Z][a-z]+){0,3})""" // Simple NER regex
        )
    }

    suspend fun extract(claim: String, classified: ClassifiedClaim): ExtractedEntity? {
        // 1. If classifier already found entities — use them first
        if (classified.entities.isNotEmpty()) {
            return ExtractedEntity(
                name = classified.entities.first(),
                isSpeaker = (classified.speaker != null && classified.entities.first().contains(classified.speaker!!)),
                claimInvolveCurrentStatus = involvesCurrentStatus(claim)
            )
        }

        // 2. Regex-based extraction — free, instant
        val regexMatch = NAMED_ENTITY_PATTERN.find(claim)
        if (regexMatch != null) {
            return ExtractedEntity(
                name = regexMatch.groupValues[1].trim(),
                isSpeaker = false,
                claimInvolveCurrentStatus = involvesCurrentStatus(claim)
            )
        }

        // 3. LLM extraction — only if no entity found above
        return try {
            val response = groqClient.chat(
                """
                Extract the primary named entity (person, organisation, or place)
                from this claim. Return ONLY the entity name, nothing else.
                If no named entity exists, return: NONE
                
                Claim: "$claim"
                """.trimIndent()
            )
            val name = response.trim().removeSurrounding("\"")
            if (name == "NONE" || name.isBlank() || name.length > 100) null
            else ExtractedEntity(
                name = name,
                isSpeaker = false,
                claimInvolveCurrentStatus = involvesCurrentStatus(claim)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun involvesCurrentStatus(claim: String): Boolean {
        val statusKeywords = listOf(
            "is", "are", "currently", "now", "still", "serves as",
            "menjadi", "sedang", "masih", "kini"
        )
        val lower = claim.lowercase()
        return statusKeywords.any { lower.contains(Regex("\\b$it\\b")) }
    }
}

data class ExtractedEntity(
    val name: String,
    val isSpeaker: Boolean,
    val claimInvolveCurrentStatus: Boolean
)
