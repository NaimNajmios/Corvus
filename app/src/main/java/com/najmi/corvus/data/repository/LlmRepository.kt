package com.najmi.corvus.data.repository

import com.najmi.corvus.data.remote.GeminiClient
import com.najmi.corvus.data.remote.GroqClient
import com.najmi.corvus.domain.model.CorvusResult
import com.najmi.corvus.domain.model.Source
import com.najmi.corvus.domain.model.Verdict
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

enum class LlmProvider {
    GEMINI,
    GROQ
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
    private val json: Json
) {
    suspend fun analyze(
        claim: String,
        sources: List<Source>,
        provider: LlmProvider = LlmProvider.GEMINI
    ): CorvusResult {
        val prompt = buildPrompt(claim, sources)
        
        val responseText = try {
            when (provider) {
                LlmProvider.GEMINI -> geminiClient.generateContent(prompt)
                LlmProvider.GROQ -> groqClient.chat(prompt)
            }
        } catch (e: Exception) {
            throw e
        }

        return parseResponse(responseText, sources)
    }

    private fun buildPrompt(claim: String, sources: List<Source>): String {
        val sourcesList = sources.mapIndexed { index, source ->
            "[$index] ${source.title}\nURL: ${source.url}\nPublisher: ${source.publisher ?: "Unknown"}\nContent: ${source.snippet ?: "No preview available"}\n"
        }.joinToString("\n")

        return """
You are a fact-checking assistant. Analyze the claim below using the provided source articles.

CLAIM: "$claim"

SOURCES:
$sourcesList

Instructions:
- Assess whether the claim is factually accurate based on the sources
- If sources are insufficient, return UNVERIFIABLE
- Be concise and neutral
- Do not fabricate information not in the sources
- Consider whether the claim is fully true, partially true, misleading, or false

Respond ONLY with valid JSON, no markdown, no preamble:
{
  "verdict": "TRUE|FALSE|MISLEADING|PARTIALLY_TRUE|UNVERIFIABLE",
  "confidence": 0.0,
  "explanation": "2-3 sentence explanation",
  "key_facts": ["fact 1", "fact 2"],
  "sources_used": [0, 1]
}
        """.trimIndent()
    }

    private fun parseResponse(responseText: String, allSources: List<Source>): CorvusResult {
        val cleanedText = responseText
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val parsed = json.decodeFromString<LlmAnalysisResponse>(cleanedText)
            val usedSources = parsed.sourcesUsed.mapNotNull { allSources.getOrNull(it) }

            CorvusResult(
                verdict = parseVerdict(parsed.verdict),
                confidence = parsed.confidence.coerceIn(0f, 1f),
                explanation = parsed.explanation,
                keyFacts = parsed.keyFacts,
                sources = usedSources.ifEmpty { allSources.take(3) }
            )
        } catch (e: Exception) {
            throw Exception("Failed to parse LLM response: ${e.message}")
        }
    }

    private fun parseVerdict(verdict: String): Verdict {
        return when (verdict.uppercase().trim()) {
            "TRUE" -> Verdict.TRUE
            "FALSE" -> Verdict.FALSE
            "MISLEADING" -> Verdict.MISLEADING
            "PARTIALLY_TRUE" -> Verdict.PARTIALLY_TRUE
            "UNVERIFIABLE" -> Verdict.UNVERIFIABLE
            else -> Verdict.UNVERIFIABLE
        }
    }
}
