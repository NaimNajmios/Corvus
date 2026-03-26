package com.najmi.corvus.domain.usecase

import android.util.Log
import com.najmi.corvus.data.remote.LlmResponse
import com.najmi.corvus.domain.router.LlmProviderRouter
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.ClassifiedClaim
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import com.najmi.corvus.domain.util.TokenCollector
import com.najmi.corvus.domain.model.TokenStep

@Serializable
private data class ClassifierResponse(
    val type: String,
    val speaker: String? = null,
    val quoted_text: String? = null,
    val claimed_date: String? = null,
    val entities: List<String> = emptyList()
)

class ClaimClassifierUseCase @Inject constructor(
    private val router: LlmProviderRouter,
    private val json: Json,
    private val tokenCollector: TokenCollector
) {
    companion object {
        private const val TAG = "ClaimClassifier"
    }

    suspend fun classify(raw: String): ClassifiedClaim {
        val prompt = prompt(raw)

        return try {
            val response = router.execute(prompt)
            tokenCollector.collect(response.usage.copy(
                step = TokenStep.CLASSIFICATION,
                provider = "LlmProviderRouter",
                model = "Routed"
            ))
            parseClassifierResponse(raw, response.text)
        } catch (e: Exception) {
            Log.e(TAG, "Classification failed: ${e.message}")
            ClassifiedClaim(raw, ClaimType.GENERAL)
        }
    }

    private fun prompt(raw: String) = """
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
