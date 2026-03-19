package com.najmi.corvus.domain.usecase

import android.util.Log
import com.najmi.corvus.data.remote.GroqClient
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.ClassifiedClaim
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
private data class ClassifierResponse(
    val type: String,
    val speaker: String? = null,
    val quoted_text: String? = null,
    val claimed_date: String? = null,
    val entities: List<String> = emptyList()
)

class ClaimClassifierUseCase @Inject constructor(
    private val groqClient: GroqClient,
    private val json: Json
) {
    companion object {
        private const val TAG = "ClaimClassifier"
    }

    suspend fun classify(raw: String): ClassifiedClaim {
        val prompt = """
            Analyze the following claim and extract structured information.
            
            Claim: "$raw"
            
            Respond ONLY with valid JSON:
            {
              "type": "QUOTE|STATISTICAL|HISTORICAL|SCIENTIFIC|CURRENT_EVENT|PERSON_FACT|GENERAL",
              "speaker": "name if quote claim, else null",
              "quoted_text": "extracted verbatim quote if type is QUOTE, else null",
              "claimed_date": "date context if present, else null",
              "entities": ["list", "of", "key", "named", "entities"]
            }
        """.trimIndent()

        return try {
            val response = groqClient.chat(prompt)
            parseClassifierResponse(raw, response)
        } catch (e: Exception) {
            Log.e(TAG, "Classification failed: ${e.message}")
            ClassifiedClaim(raw, ClaimType.GENERAL)
        }
    }

    private fun parseClassifierResponse(raw: String, response: String): ClassifiedClaim {
        val cleanedText = response
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val parsed = json.decodeFromString<ClassifierResponse>(cleanedText)
            ClassifiedClaim(
                raw = raw,
                type = mapType(parsed.type),
                speaker = parsed.speaker,
                quotedText = parsed.quoted_text,
                claimedDate = parsed.claimed_date,
                entities = parsed.entities
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse classifier response: ${e.message}")
            ClassifiedClaim(raw, ClaimType.GENERAL)
        }
    }

    private fun mapType(type: String): ClaimType {
        return try {
            ClaimType.valueOf(type.uppercase().trim())
        } catch (e: Exception) {
            ClaimType.GENERAL
        }
    }
}
