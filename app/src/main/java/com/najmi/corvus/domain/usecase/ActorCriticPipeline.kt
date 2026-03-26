package com.najmi.corvus.domain.usecase

import android.util.Log
import com.najmi.corvus.data.local.UserPreferences
import com.najmi.corvus.data.remote.CerebrasClient
import com.najmi.corvus.data.remote.GeminiClient
import com.najmi.corvus.data.remote.GroqClient
import com.najmi.corvus.data.remote.MistralClient
import com.najmi.corvus.data.remote.OpenRouterClient
import com.najmi.corvus.data.remote.cohere.CohereClient
import com.najmi.corvus.data.remote.cohere.CohereQuotaExceededException
import com.najmi.corvus.data.remote.llm.SourceContextBuilder
import com.najmi.corvus.domain.model.*
import com.najmi.corvus.domain.router.LlmProviderHealthTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import javax.inject.Inject

class ActorCriticPipeline @Inject constructor(
    private val groqClient: GroqClient,
    private val geminiClient: GeminiClient,
    private val cerebrasClient: CerebrasClient,
    private val openRouterClient: OpenRouterClient,
    private val mistralClient: MistralClient,
    private val cohereClient: CohereClient,
    private val contextBuilder: SourceContextBuilder,
    private val healthTracker: LlmProviderHealthTracker,
    private val providerSelector: ActorCriticProviderSelector,
    private val json: Json
) {
    companion object {
        private const val TAG = "ActorCriticPipeline"
    }

    suspend fun analyze(
        claim: String,
        classified: ClassifiedClaim,
        sources: List<Source>,
        prefs: UserPreferences,
        onStepChange: suspend (PipelineStep) -> Unit = {}
    ): CorvusCheckResult.GeneralResult {
        val limitedSources = sources.take(10)
        
        val assignment = providerSelector.select(classified)
        val actorContext = contextBuilder.build(limitedSources, assignment.actor)
        val criticContext = contextBuilder.build(limitedSources, assignment.critic)

        Log.d(TAG, "Actor pass starting: ${assignment.actor}")
        Log.d(TAG, "Critic pass starting: ${assignment.critic}")

        onStepChange(PipelineStep.ANALYZING)
        val actorPrompt = buildActorPrompt(claim, classified, actorContext)
        val actorRaw = completePrompt(assignment.actor, actorPrompt)
        val actorDraft = parseActorDraft(actorRaw)

        Log.d(TAG, "Critic pass starting: ${assignment.critic}")

        onStepChange(PipelineStep.VERIFYING)
        
        val actorUsedIndices = actorDraft.sourcesUsed.toSet()
        val criticSources = limitedSources.filterIndexed { index, _ -> 
            actorUsedIndices.contains(index) 
        }.toMutableList()
        
        val unusedSources = limitedSources.filterIndexed { index, _ -> !actorUsedIndices.contains(index) }
            .take(2)
        criticSources.addAll(unusedSources)
        
        val condensedCriticContext = contextBuilder.build(criticSources, assignment.critic)
        
        val criticPrompt = buildCriticPrompt(claim, condensedCriticContext, actorDraft)
        
        var criticRaw: String
        var finalCriticProvider = assignment.critic
        
        try {
            criticRaw = completePrompt(assignment.critic, criticPrompt)
        } catch (e: CohereQuotaExceededException) {
            Log.w(TAG, "Cohere quota exceeded: ${e.message}. Falling back to standard Critic.")
            criticRaw = completePrompt(LlmProvider.GEMINI, criticPrompt)
            finalCriticProvider = LlmProvider.GEMINI
        } catch (e: Exception) {
            Log.e(TAG, "Primary Critic (${assignment.critic}) failed: ${e.message}")
            
            if (isRetryableError(e) && assignment.critic != LlmProvider.GEMINI && assignment.actor != LlmProvider.GEMINI) {
                Log.d(TAG, "Attempting fallback Critic: GEMINI")
                try {
                    criticRaw = completePrompt(LlmProvider.GEMINI, criticPrompt)
                    finalCriticProvider = LlmProvider.GEMINI
                } catch (fallbackEx: Exception) {
                    Log.e(TAG, "Fallback Critic (GEMINI) also failed. Reraising original error.")
                    throw e
                }
            } else {
                throw e
            }
        }
        
        val finalResult = parseCriticOutput(criticRaw, limitedSources)

        return finalResult.copy(
            actorProvider = assignment.actor,
            criticProvider = finalCriticProvider,
            correctionsLog = actorDraft.unsupportedAssumptions + (finalResult.correctionsLog ?: emptyList())
        )
    }

    private suspend fun completePrompt(provider: LlmProvider, prompt: String): String {
        return try {
            when (provider) {
                LlmProvider.GEMINI -> geminiClient.generateContent(prompt)
                LlmProvider.GROQ -> groqClient.chat(prompt)
                LlmProvider.CEREBRAS -> cerebrasClient.chat(prompt)
                LlmProvider.OPENROUTER -> openRouterClient.chat(prompt)
                LlmProvider.MISTRAL_SABA -> mistralClient.chatSaba(prompt)
                LlmProvider.MISTRAL_SMALL -> mistralClient.chatSmall(prompt)
                LlmProvider.COHERE_R -> cohereClient.chatR(prompt)
                LlmProvider.COHERE_R_PLUS -> cohereClient.chatRPlus(prompt)
            }
        } catch (e: Exception) {
            healthTracker.reportError(provider.name)
            throw e
        }
    }

    private fun isRetryableError(e: Exception): Boolean {
        val message = e.message ?: return false
        return message.contains("429", ignoreCase = true) ||
               message.contains("rate", ignoreCase = true) ||
               message.contains("quota", ignoreCase = true) ||
               message.contains("timeout", ignoreCase = true) ||
               message.contains("503", ignoreCase = true) ||
               message.contains("unavailable", ignoreCase = true)
    }

    private suspend fun parseActorDraft(raw: String): ActorDraft = withContext(Dispatchers.Default) {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        try {
            json.decodeFromString<ActorDraft>(cleaned)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ActorDraft: ${e.message}")
            // Fallback draft so Critic can still try
            ActorDraft(
                reasoningScratchpad = "Failed to parse draft",
                draftExplanation = "LLM failed to output parseable fallback."
            )
        }
    }

    private suspend fun parseCriticOutput(raw: String, allSources: List<Source>): CorvusCheckResult.GeneralResult = withContext(Dispatchers.Default) {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val parsed = json.decodeFromString<JsonObject>(cleaned)

        val reasoningScratchpad = parsed["evidentiary_analysis"]?.jsonPrimitive?.content
        val verdictStr = parsed["verdict"]?.jsonPrimitive?.content ?: "UNVERIFIABLE"
        val verdict = when (verdictStr.uppercase().trim().removeSurrounding("\"")) {
            "TRUE" -> Verdict.TRUE
            "FALSE" -> Verdict.FALSE
            "MISLEADING" -> Verdict.MISLEADING
            "PARTIALLY_TRUE" -> Verdict.PARTIALLY_TRUE
            "UNVERIFIABLE" -> Verdict.UNVERIFIABLE
            else -> Verdict.UNVERIFIABLE
        }

        val confidence = parsed["confidence"]?.jsonPrimitive?.floatOrNull?.coerceIn(0f, 1f) ?: 0.5f
        val explanation = parsed["explanation"]?.jsonPrimitive?.content ?: ""
        
        val keyFacts = parsed["key_facts"]?.jsonArray?.map { el ->
            val obj = el.jsonObject
            GroundedFact(
                statement = obj["statement"]?.jsonPrimitive?.content ?: "",
                sourceIndex = obj["source_index"]?.jsonPrimitive?.intOrNull,
                isDirectQuote = obj["is_direct_quote"]?.jsonPrimitive?.booleanOrNull ?: false
            )
        } ?: emptyList()

        val sourcesUsedIndices = parsed["sources_used"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull } ?: emptyList()
        val usedSources = sourcesUsedIndices.mapNotNull { allSources.getOrNull(it) }

        val harmLevel = parsed["harm_assessment"]?.jsonObject?.get("level")?.jsonPrimitive?.content ?: "NONE"
        val harmCat = parsed["harm_assessment"]?.jsonObject?.get("category")?.jsonPrimitive?.content ?: "NONE"
        val harmAssessment = HarmAssessment(
            level = runCatching { HarmLevel.valueOf(harmLevel) }.getOrDefault(HarmLevel.NONE),
            category = runCatching { HarmCategory.valueOf(harmCat) }.getOrDefault(HarmCategory.NONE),
            reason = parsed["harm_assessment"]?.jsonObject?.get("reason")?.jsonPrimitive?.content ?: ""
        )

        val correctionsLog = parsed["corrections_made"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

        CorvusCheckResult.GeneralResult(
            verdict = verdict,
            confidence = confidence,
            explanation = explanation,
            keyFacts = keyFacts,
            sources = usedSources.ifEmpty { allSources },
            harmAssessment = harmAssessment,
            reasoningScratchpad = reasoningScratchpad,
            correctionsLog = correctionsLog,
            claimType = ClaimType.GENERAL
        )
    }

    private fun buildActorPrompt(
        claim: String,
        classified: ClassifiedClaim,
        sourceContext: String
    ): String = """
You are a fact-checking analyst. Your job is to draft a preliminary fact-check
of the following claim based solely on the provided sources.

CLAIM: "$claim"
CLAIM TYPE: ${classified.type.name}

SOURCES:
$sourceContext

YOUR TASK:
1. Read all sources carefully.
2. Identify which sources (if any) directly support or contradict the claim.
3. Draft a preliminary verdict and explanation.

IMPORTANT RULES:
- Base your analysis EXCLUSIVELY on the provided sources.
- Do not use internal training knowledge to fill gaps.
- If sources are insufficient, draft verdict as UNVERIFIABLE.
- For each key fact, note the specific source index it comes from.
- This is a DRAFT — it will be reviewed and corrected.

OUTPUT FORMAT — Respond ONLY with valid JSON in EXACT order below:
{
  "evidentiary_analysis": "Step-by-step walkthrough of what each source says and how it relates to the claim",
  "draft_key_facts": [
    {
      "statement": "...",
      "source_index": 0,
      "is_direct_quote": false,
      "source_text_evidence": "The exact text from the source that supports this fact"
    }
  ],
  "unsupported_assumptions": ["List any claims in the explanation not directly backed by sources"],
  "sources_used": [0, 1],
  "draft_confidence": 0.0,
  "draft_verdict": "TRUE|FALSE|MISLEADING|PARTIALLY_TRUE|UNVERIFIABLE",
  "draft_explanation": "Evidentiary explanation attributed to sources"
}
""".trimIndent()

    private fun buildCriticPrompt(
        claim: String,
        sourceContext: String,
        actorDraft: ActorDraft
    ): String = """
You are a senior fact-checking editor. A junior analyst has produced a preliminary
fact-check. Your job is to rigorously verify it against the source evidence and
correct any errors.

ORIGINAL CLAIM: "$claim"

SOURCES (same sources the analyst had access to):
$sourceContext

ANALYST'S DRAFT:
Verdict    : ${actorDraft.draftVerdict}
Confidence : ${actorDraft.draftConfidence}
Explanation: ${actorDraft.draftExplanation}

Key facts drafted:
${actorDraft.draftKeyFacts.mapIndexed { i, f ->
    "  [$i] \"${f.statement}\" — attributed to Source [${f.sourceIndex}]\n" +
    "      Analyst's evidence: ${f.sourceTextEvidence ?: "None provided"}"
}.joinToString("\n")}

Unsupported assumptions flagged by analyst:
${actorDraft.unsupportedAssumptions.joinToString("\n") { "  - $it" }}

YOUR REVIEW TASKS — for each item, verify then correct:
1. CITATION AUDIT: For each key fact, check whether the quoted
   source_text_evidence actually appears in the assigned source.
   If it does not, either find the correct source index or strip the citation.
2. VERDICT AUDIT: Does the evidence actually support the drafted verdict?
   If the analyst has been sycophantic, correct the verdict.
3. CONFIDENCE AUDIT: Is the confidence score appropriate for the evidence quality?
4. EXPLANATION AUDIT: Rewrite any sentence that asserts something not explicitly
   stated in the provided sources.

OUTPUT FORMAT — Respond ONLY with valid JSON in EXACT order below:
{
  "evidentiary_analysis": "Your step-by-step audit of the analyst's draft",
  "key_facts": [
    {
      "statement": "...",
      "source_index": 0,
      "is_direct_quote": false
    }
  ],
  "harm_assessment": {
    "level": "NONE|LOW|MODERATE|HIGH",
    "category": "NONE|HEALTH|SAFETY|RACIAL_ETHNIC|RELIGIOUS|POLITICAL|FINANCIAL",
    "reason": ""
  },
  "corrections_made": [
    "List of specific corrections made to the analyst's draft"
  ],
  "sources_used": [0],
  "confidence": 0.0,
  "verdict": "TRUE|FALSE|MISLEADING|PARTIALLY_TRUE|UNVERIFIABLE",
  "explanation": "Corrected, evidence-attributed explanation"
}
""".trimIndent()
}
