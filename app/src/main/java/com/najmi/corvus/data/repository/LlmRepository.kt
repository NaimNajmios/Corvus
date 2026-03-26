package com.najmi.corvus.data.repository

import android.util.Log
import com.najmi.corvus.domain.router.LlmProviderRouter
import com.najmi.corvus.data.remote.llm.SourceContextBuilder
import com.najmi.corvus.domain.model.*
import com.najmi.corvus.domain.util.HarmPreScreener
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// Enum moved to com.najmi.corvus.domain.model.LlmProvider

@Serializable
data class LlmGroundedFact(
    val statement: String,
    @SerialName("source_index") val sourceIndex: Int?,
    @SerialName("is_direct_quote") val isDirectQuote: Boolean = false
)

@Serializable
data class LlmKernelOfTruth(
    @SerialName("true_parts") val trueParts: List<LlmGroundedFact>,
    @SerialName("false_parts") val falseParts: List<LlmGroundedFact>,
    @SerialName("twist_explanation") val twistExplanation: String
)

@Serializable
data class LlmMissingContext(
    val content: String,
    @SerialName("context_type") val contextType: String
)

@Serializable
data class LlmAnalysisResponse(
    @SerialName("evidentiary_analysis") val evidentiaryAnalysis: String? = null,
    val verdict: String,
    val confidence: Float,
    val explanation: String,
    @SerialName("key_facts") val keyFacts: List<LlmGroundedFact> = emptyList(),
    @SerialName("kernel_of_truth") val kernelOfTruth: LlmKernelOfTruth? = null,
    @SerialName("missing_context") val missingContext: LlmMissingContext? = null,
    @SerialName("sources_used") val sourcesUsed: List<Int> = emptyList(),
    @SerialName("harm_assessment") val harmAssessment: HarmAssessment? = null,
    @SerialName("plausibility") val plausibility: PlausibilityResponse? = null
)

@Serializable
data class PlausibilityResponse(
    val score: String,
    val reasoning: String,
    @SerialName("closest_evidence") val closestEvidence: String? = null
)

@Singleton
class LlmRepository @Inject constructor(
    private val router: LlmProviderRouter,
    private val json: Json,
    private val sourceContextBuilder: SourceContextBuilder
) {
    companion object {
        private const val TAG = "LlmRepository"
        private const val MAX_SOURCES = 10
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
        val sourceContext = sourceContextBuilder.build(limitedSources, provider)
        val prompt = buildPrompt(claim, sourceContext, claimType)
        
        Log.d(TAG, "Analyzing with ${provider.name}, sources length: ${sourceContext.length}, prompt length: ${prompt.length}")
        
        var lastError: Exception? = null
        
        repeat(MAX_RETRIES + 1) { attempt ->
            try {
                // ... (existing try-catch logic remains same)
                val responseText = router.execute(prompt, provider)
                
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

    private fun buildPrompt(claim: String, sourceContext: String, claimType: ClaimType): String {
        val typeContext = when (claimType) {
            ClaimType.STATISTICAL -> "This is a statistical claim. Focus on numerical accuracy and dates."
            ClaimType.SCIENTIFIC -> "This is a scientific claim. Focus on peer-reviewed evidence."
            ClaimType.HISTORICAL -> "This is a historical claim. Focus on dates and primary sources."
            ClaimType.PERSON_FACT -> "This is a fact about a person. Focus on roles and relationships."
            ClaimType.CURRENT_EVENT -> "This is a current event. Focus on the most recent reports."
            else -> ""
        }

        val harmHint = HarmPreScreener.preScreen(claim)?.let {
            "\nHINT: This claim may contain ${it.name} harm signals. Evaluate carefully."
        } ?: ""

        return """
Fact-check this claim using the sources below. $typeContext
$harmHint

OUTPUT SCHEMA RULES:
You MUST output the JSON keys in the EXACT ORDER listed below.
Do NOT reorder them. This order is deliberate:
the verdict must be derived from your analysis, not the other way around.

Step 1 — evidentiary_analysis:
  Walk through each source methodically. For each source, note:
  - What does it explicitly say about the claim?
  - Does it support, contradict, or not address the claim?
  - How credible is this source relative to the others?
  Then synthesise what the combined evidence shows.
  This field MUST be at least 3 sentences. Write your full reasoning here.

Step 2 — key_facts (grounded citations)
Step 3 — kernel_of_truth (if MISLEADING or PARTIALLY_TRUE)
Step 4 — missing_context (if applicable)
Step 5 — harm_assessment
Step 6 — plausibility (if UNVERIFIABLE)
Step 7 — confidence (0.0–1.0, based on evidence strength, not gut feeling)
Step 8 — verdict (TRUE|FALSE|MISLEADING|PARTIALLY_TRUE|UNVERIFIABLE)
Step 9 — explanation (A clean, reader-facing summary of your evidentiary_analysis)
Step 10 — sources_used (indices)

IMPORTANT: The verdict is the LAST reasoning step, not the first.

EXPLANATION — EVIDENTIARY FRAMING:
Write the explanation in an evidentiary, attribution-forward tone. 
Do NOT state facts as absolute truths. DO attribute every claim to its evidence source using phrases like "According to [publisher]..." or "Reports from [publisher] indicate...". 
Avoid first-person ("I found...") and omniscient declarations ("The truth is...").

KEY FACTS — GROUNDED CITATIONS:
For each key fact, you MUST identify which source it comes from.
- source_index refers to the zero-based index of the source in the SOURCES list below.
- If a fact comes from your general knowledge and not from the provided sources, set source_index to null.
- If the fact is a verbatim quote, set is_direct_quote to true.
- Maximum 5 key facts.

CLAIM: "$claim"
TYPE: $claimType

SOURCES:
$sourceContext

Respond ONLY with this exact JSON format in the exact key order:
{
  "evidentiary_analysis": "Your step-by-step reasoning through the evidence.",
  "key_facts": [
    { "statement": "fact", "source_index": 0, "is_direct_quote": false }
  ],
  "kernel_of_truth": {
    "true_parts": [{ "statement": "...", "source_index": 1, "is_direct_quote": false }],
    "false_parts": [{ "statement": "...", "source_index": null, "is_direct_quote": false }],
    "twist_explanation": "how it was twisted"
  },
  "missing_context": {
    "content": "absent context",
    "context_type": "TEMPORAL|GEOGRAPHIC|ATTRIBUTION|STATISTICAL|SELECTIVE|GENERAL"
  },
  "harm_assessment": {
    "level": "NONE|LOW|MODERATE|HIGH",
    "category": "NONE|HEALTH|SAFETY|RACIAL_ETHNIC|RELIGIOUS|POLITICAL|FINANCIAL",
    "reason": "explanation"
  },
  "plausibility": {
    "score": "IMPLAUSIBLE|UNLIKELY|NEUTRAL|PLAUSIBLE|PROBABLE",
    "reasoning": "reasoning",
    "closest_evidence": "hint"
  },
  "confidence": 0.0,
  "verdict": "TRUE|FALSE|MISLEADING|PARTIALLY_TRUE|UNVERIFIABLE",
  "explanation": "evidentiary explanation",
  "sources_used": [0,1]
}
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
            val verdict = parseVerdict(parsed.verdict)

            CorvusCheckResult.GeneralResult(
                 claimType = claimType,
                verdict = verdict,
                confidence = parsed.confidence.coerceIn(0f, 1f),
                explanation = parsed.explanation,
                keyFacts = parsed.keyFacts.map { 
                    GroundedFact(it.statement, it.sourceIndex, it.isDirectQuote) 
                },
                sources = usedSources.ifEmpty { allSources },
                harmAssessment = parsed.harmAssessment ?: HarmAssessment(),
                plausibility = parsed.plausibility?.let { resp ->
                    val score = runCatching { PlausibilityScore.valueOf(resp.score.uppercase()) }.getOrDefault(PlausibilityScore.NEUTRAL)
                    PlausibilityAssessment(score, resp.reasoning, resp.closestEvidence)
                },
                kernelOfTruth = parsed.kernelOfTruth?.let { kot ->
                    KernelOfTruth(
                        trueParts = kot.trueParts.map { GroundedFact(it.statement, it.sourceIndex, it.isDirectQuote) },
                        falseParts = kot.falseParts.map { GroundedFact(it.statement, it.sourceIndex, it.isDirectQuote) },
                        twistExplanation = kot.twistExplanation
                    )
                },
                missingContext = parsed.missingContext?.let { mc ->
                    MissingContextInfo(
                        content = mc.content,
                        contextType = runCatching { ContextType.valueOf(mc.contextType.uppercase()) }.getOrDefault(ContextType.GENERAL)
                    )
                },
                reasoningScratchpad = parsed.evidentiaryAnalysis
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
