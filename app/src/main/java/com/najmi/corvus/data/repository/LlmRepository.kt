package com.najmi.corvus.data.repository

import android.util.Log
import com.najmi.corvus.data.remote.CerebrasClient
import com.najmi.corvus.data.remote.GeminiClient
import com.najmi.corvus.data.remote.GroqClient
import com.najmi.corvus.data.remote.OpenRouterClient
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.Source
import com.najmi.corvus.domain.model.Verdict
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

enum class LlmProvider {
    GEMINI,
    GROQ,
    CEREBRAS,
    OPENROUTER
}

@Serializable
data class LlmAnalysisResponse(
    val verdict: String,
    val confidence: Float,
    val explanation: String,
    @SerialName("key_facts") val keyFacts: List<String> = emptyList(),
    @SerialName("sources_used") val sourcesUsed: List<Int> = emptyList()
)

@Singleton
class LlmRepository @Inject constructor(
    private val geminiClient: GeminiClient,
    private val groqClient: GroqClient,
    private val cerebrasClient: CerebrasClient,
    private val openRouterClient: OpenRouterClient,
    private val json: Json
) {
    companion object {
        private const val TAG = "LlmRepository"
        private const val MAX_SOURCES = 3
        private const val MAX_SNIPPET_LENGTH = 150
        private const val MAX_RETRIES = 2
        private const val RETRY_DELAY_MS = 2000L
    }

    suspend fun analyze(
        claim: String,
        sources: List<Source>,
        provider: LlmProvider = LlmProvider.GEMINI,
        claimType: ClaimType = ClaimType.GENERAL
    ): CorvusCheckResult.GeneralResult {
        val limitedSources = sources.take(MAX_SOURCES)
        val prompt = buildPrompt(claim, limitedSources, claimType)
        
        Log.d(TAG, "Analyzing with ${provider.name}, sources: ${limitedSources.size}, prompt length: ${prompt.length}")
        
        var lastError: Exception? = null
        
        repeat(MAX_RETRIES + 1) { attempt ->
            try {
                // ... (existing try-catch logic remains same)
                val responseText = when (provider) {
                    LlmProvider.GEMINI -> geminiClient.generateContent(prompt)
                    LlmProvider.GROQ -> groqClient.chat(prompt)
                    LlmProvider.CEREBRAS -> cerebrasClient.chat(prompt)
                    LlmProvider.OPENROUTER -> openRouterClient.chat(prompt)
                }
                
                Log.d(TAG, "Got response, parsing...")
                return parseResponse(responseText, limitedSources, claimType)
                
            } catch (e: Exception) {
                // ... (existing error handling remains same)
                lastError = e
                delay(RETRY_DELAY_MS)
            }
        }
        
        throw lastError ?: Exception("LLM analysis failed")
    }

    private fun buildPrompt(claim: String, sources: List<Source>, claimType: ClaimType): String {
        val typeContext = when (claimType) {
            ClaimType.STATISTICAL -> "This is a statistical claim. Focus on numerical accuracy and dates."
            ClaimType.SCIENTIFIC -> "This is a scientific claim. Focus on peer-reviewed evidence."
            ClaimType.HISTORICAL -> "This is a historical claim. Focus on dates and primary sources."
            ClaimType.PERSON_FACT -> "This is a fact about a person. Focus on roles and relationships."
            ClaimType.CURRENT_EVENT -> "This is a current event. Focus on the most recent reports."
            else -> ""
        }

        val sourcesList = sources.mapIndexed { index, source ->
            val snippet = source.snippet?.take(MAX_SNIPPET_LENGTH) ?: "No preview"
            "[$index] ${source.title}\nPublisher: ${source.publisher ?: "Unknown"}\nType: ${source.sourceType}\n${snippet}...\n"
        }.joinToString("\n")

        return """
Fact-check this claim using the sources below. $typeContext
Respond ONLY with valid JSON (no markdown):

CLAIM: "$claim"
TYPE: $claimType

SOURCES:
$sourcesList

Respond ONLY with this exact JSON format:
{"verdict":"TRUE|FALSE|MISLEADING|PARTIALLY_TRUE|UNVERIFIABLE","confidence":0.0-1.0,"explanation":"brief explanation","key_facts":["fact1","fact2"],"sources_used":[0,1]}
        """.trimIndent()
    }

    private fun parseResponse(responseText: String, allSources: List<Source>, claimType: ClaimType): CorvusCheckResult.GeneralResult {
        Log.d(TAG, "Raw response: ${responseText.take(300)}")
        
        val cleanedText = responseText
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val parsed = json.decodeFromString<LlmAnalysisResponse>(cleanedText)
            val usedSources = parsed.sourcesUsed.mapNotNull { allSources.getOrNull(it) }

            CorvusCheckResult.GeneralResult(
                claimType = claimType,
                verdict = parseVerdict(parsed.verdict),
                confidence = parsed.confidence.coerceIn(0f, 1f),
                explanation = parsed.explanation,
                keyFacts = parsed.keyFacts,
                sources = usedSources.ifEmpty { allSources }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parse failed: ${e.message}")
            throw Exception("Failed to parse LLM response: ${e.message}")
        }
    }

    private fun parseVerdict(verdict: String): Verdict {
        return when (verdict.uppercase().trim().removeSurrounding("\"")) {
            "TRUE" -> Verdict.TRUE
            "FALSE" -> Verdict.FALSE
            "MISLEADING" -> Verdict.MISLEADING
            "PARTIALLY_TRUE" -> Verdict.PARTIALLY_TRUE
            "UNVERIFIABLE" -> Verdict.UNVERIFIABLE
            else -> {
                Log.w(TAG, "Unknown verdict: $verdict")
                Verdict.UNVERIFIABLE
            }
        }
    }
}
