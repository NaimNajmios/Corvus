package com.najmi.corvus.data.repository

import android.util.Log
import com.najmi.corvus.data.remote.CerebrasClient
import com.najmi.corvus.data.remote.GeminiClient
import com.najmi.corvus.data.remote.GroqClient
import com.najmi.corvus.data.remote.OpenRouterClient
import com.najmi.corvus.domain.model.CorvusResult
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
        provider: LlmProvider = LlmProvider.GEMINI
    ): CorvusResult {
        val limitedSources = sources.take(MAX_SOURCES)
        val prompt = buildPrompt(claim, limitedSources)
        
        Log.d(TAG, "Analyzing with ${provider.name}, sources: ${limitedSources.size}, prompt length: ${prompt.length}")
        
        var lastError: Exception? = null
        
        repeat(MAX_RETRIES + 1) { attempt ->
            try {
                val responseText = when (provider) {
                    LlmProvider.GEMINI -> {
                        Log.d(TAG, "Calling Gemini (attempt ${attempt + 1})...")
                        geminiClient.generateContent(prompt)
                    }
                    LlmProvider.GROQ -> {
                        Log.d(TAG, "Calling Groq (attempt ${attempt + 1})...")
                        groqClient.chat(prompt)
                    }
                    LlmProvider.CEREBRAS -> {
                        Log.d(TAG, "Calling Cerebras (attempt ${attempt + 1})...")
                        cerebrasClient.chat(prompt)
                    }
                    LlmProvider.OPENROUTER -> {
                        Log.d(TAG, "Calling OpenRouter (attempt ${attempt + 1})...")
                        openRouterClient.chat(prompt)
                    }
                }
                
                Log.d(TAG, "Got response, parsing...")
                return parseResponse(responseText, limitedSources)
                
            } catch (e: Exception) {
                lastError = e
                val isRateLimit = e.message?.contains("429", ignoreCase = true) == true ||
                                   e.message?.contains("rate", ignoreCase = true) == true ||
                                   e.message?.contains("quota", ignoreCase = true) == true
                
                Log.w(TAG, "${provider.name} attempt ${attempt + 1} failed: ${e.message}")
                
                if (isRateLimit && attempt < MAX_RETRIES) {
                    Log.d(TAG, "Rate limited, waiting ${RETRY_DELAY_MS}ms before retry...")
                    delay(RETRY_DELAY_MS * (attempt + 1))
                } else if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAY_MS)
                }
            }
        }
        
        throw lastError ?: Exception("LLM analysis failed after ${MAX_RETRIES + 1} attempts")
    }

    private fun buildPrompt(claim: String, sources: List<Source>): String {
        val sourcesList = sources.mapIndexed { index, source ->
            val snippet = source.snippet?.take(MAX_SNIPPET_LENGTH) ?: "No preview"
            "[$index] ${source.title}\nPublisher: ${source.publisher ?: "Unknown"}\n${snippet}...\n"
        }.joinToString("\n")

        return """
Fact-check this claim using the sources below. Respond ONLY with valid JSON (no markdown):

CLAIM: "$claim"

SOURCES:
$sourcesList

Respond ONLY with this exact JSON format:
{"verdict":"TRUE|FALSE|MISLEADING|PARTIALLY_TRUE|UNVERIFIABLE","confidence":0.0-1.0,"explanation":"brief explanation","key_facts":["fact1","fact2"],"sources_used":[0,1]}
        """.trimIndent()
    }

    private fun parseResponse(responseText: String, allSources: List<Source>): CorvusResult {
        Log.d(TAG, "Raw response: ${responseText.take(300)}")
        
        val cleanedText = responseText
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val parsed = json.decodeFromString<LlmAnalysisResponse>(cleanedText)
            Log.d(TAG, "Parsed verdict: ${parsed.verdict}")
            val usedSources = parsed.sourcesUsed.mapNotNull { allSources.getOrNull(it) }

            CorvusResult(
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
