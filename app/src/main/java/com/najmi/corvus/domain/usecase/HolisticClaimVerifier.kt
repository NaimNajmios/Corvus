package com.najmi.corvus.domain.usecase

import android.util.Log
import com.najmi.corvus.data.remote.LlmResponse
import com.najmi.corvus.data.remote.SourceContextBuilder
import com.najmi.corvus.domain.model.*
import com.najmi.corvus.domain.router.LlmProviderRouter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import com.najmi.corvus.domain.util.TokenCollector
import com.najmi.corvus.domain.model.TokenStep

@Singleton
class HolisticClaimVerifier @Inject constructor(
    private val router: LlmProviderRouter,
    private val contextBuilder: SourceContextBuilder,
    private val tokenCollector: TokenCollector
) {
    companion object {
        private const val TAG = "HolisticVerifier"
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun verify(
        originalClaim: String,
        subClaims: List<SubClaim>,
        allSources: List<Source>
    ): HolisticVerificationResult {
        if (subClaims.isEmpty() || allSources.isEmpty()) {
            return HolisticVerificationResult(
                holisticVerdict = Verdict.UNVERIFIABLE,
                holisticExplanation = "Insufficient data for holistic verification",
                issuesFound = emptyList(),
                confidence = 0f
            )
        }

        val limitedSources = allSources.take(15)
        val sourceContext = contextBuilder.build(limitedSources, LlmProvider.GEMINI)

        val subclaimSummary = buildSubclaimSummary(subClaims)
        val subclaimVerdicts = subClaims.mapIndexed { index, sub ->
            val verdict = when (val res = sub.result) {
                is CorvusCheckResult.GeneralResult -> res.verdict
                is CorvusCheckResult.QuoteResult -> when (res.quoteVerdict) {
                    QuoteVerdict.VERIFIED -> Verdict.TRUE
                    QuoteVerdict.FABRICATED -> Verdict.FALSE
                    else -> Verdict.PARTIALLY_TRUE
                }
                else -> Verdict.UNVERIFIABLE
            }
            "Sub-claim ${index + 1} [${verdict.name}]: ${sub.text}"
        }.joinToString("\n")

        val prompt = buildHolisticPrompt(originalClaim, subclaimSummary, subclaimVerdicts, sourceContext)

        return try {
            val response = router.execute(prompt, LlmProvider.GEMINI)
            tokenCollector.collect(response.usage.copy(
                step = TokenStep.ACTOR_PASS,
                provider = LlmProvider.GEMINI.name,
                model = "Routed"
            ))
            parseHolisticResponse(response.text, originalClaim)
        } catch (e: Exception) {
            Log.e(TAG, "Holistic verification failed: ${e.message}")
            HolisticVerificationResult(
                holisticVerdict = Verdict.UNVERIFIABLE,
                holisticExplanation = "Holistic verification failed: ${e.message}",
                issuesFound = emptyList(),
                confidence = 0f
            )
        }
    }

    private fun buildSubclaimSummary(subClaims: List<SubClaim>): String {
        return subClaims.joinToString("\n\n") { sub ->
            val explanation = when (val res = sub.result) {
                is CorvusCheckResult.GeneralResult -> res.explanation
                is CorvusCheckResult.QuoteResult -> res.keyFacts.joinToString("; ") { it.statement }
                else -> "No explanation available"
            }
            "Sub-claim: ${sub.text}\nAnalysis: $explanation"
        }
    }

    private fun buildHolisticPrompt(
        originalClaim: String,
        subclaimSummary: String,
        subclaimVerdicts: String,
        sourceContext: String
    ): String = """
You are a senior fact-checking editor performing a FINAL HOLISTIC REVIEW.

Your job is to evaluate the ORIGINAL COMPOUND CLAIM as a WHOLE to catch
deception patterns that individual sub-claim checks might miss.

ORIGINAL COMPOUND CLAIM: "$originalClaim"

SUBCLAIM VERIFICATION RESULTS:
$subclaimVerdicts

SUBCLAIM ANALYSES:
$subclaimSummary

SOURCES (aggregated from all subclaim verifications):
$sourceContext

YOUR TASK:
Evaluate if the ORIGINAL COMPOUND CLAIM is TRUE, FALSE, or MISLEADING when
considered as a WHOLE. Specifically look for:

1. MISSING_CONTEXT: Facts are technically true but lack crucial context
2. SELECTIVE_TRUTH: Only favorable aspects were presented, ignoring counter-evidence
3. CHERRY_PICKING: Selectively choosing data points to mislead
4. NARRATIVE_SHIFT: The combination of true subclaims creates a false narrative
5. OMISSION: Important information that changes the verdict was omitted
6. MISLEADING_COMBINATION: True claims combined in a way that misleads

IMPORTANT: Even if all sub-claims are TRUE individually, the COMPOUND claim
can still be MISLEADING if the overall narrative is deceptive.

OUTPUT FORMAT — Respond ONLY with valid JSON:
{
  "holistic_verdict": "TRUE|FALSE|MISLEADING|PARTIALLY_TRUE|UNVERIFIABLE",
  "holistic_explanation": "Your detailed explanation of the holistic verdict",
  "confidence": 0.85,
  "evidentiary_analysis": "Step-by-step analysis of how sources support or contradict the compound claim",
  "issues_found": [
    {
      "type": "MISSING_CONTEXT|SELECTIVE_TRUTH|CHERRY_PICKING|NARRATIVE_SHIFT|OMISSION|MISLEADING_COMBINATION",
      "description": "Description of the issue",
      "affected_subclaims": [1, 2],
      "severity": "LOW|MODERATE|HIGH"
    }
  ],
  "corrections_applied": ["Any corrections to the subclaim analysis"]
}
    """.trimIndent()

    private suspend fun parseHolisticResponse(raw: String, originalClaim: String): HolisticVerificationResult =
        withContext(Dispatchers.Default) {
            try {
                val cleaned = raw.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                val jsonObj = json.parseToJsonElement(cleaned).jsonObject

                val verdictStr = jsonObj["holistic_verdict"]?.jsonPrimitive?.content?.uppercase() ?: "UNVERIFIABLE"
                val verdict = when (verdictStr.trim().removeSurrounding("\"")) {
                    "TRUE" -> Verdict.TRUE
                    "FALSE" -> Verdict.FALSE
                    "MISLEADING" -> Verdict.MISLEADING
                    "PARTIALLY_TRUE" -> Verdict.PARTIALLY_TRUE
                    else -> Verdict.UNVERIFIABLE
                }

                val confidence = jsonObj["confidence"]?.jsonPrimitive?.content?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.5f
                val explanation = jsonObj["holistic_explanation"]?.jsonPrimitive?.content ?: ""
                val analysis = jsonObj["evidentiary_analysis"]?.jsonPrimitive?.content
                val corrections = jsonObj["corrections_applied"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.content }
                    ?: emptyList()

                val issues = jsonObj["issues_found"]?.jsonArray?.mapNotNull { element ->
                    try {
                        val obj = element.jsonObject
                        val typeStr = obj["type"]?.jsonPrimitive?.content?.uppercase() ?: return@mapNotNull null
                        val type = when (typeStr.removeSurrounding("\"")) {
                            "MISSING_CONTEXT" -> HolisticIssueType.MISSING_CONTEXT
                            "SELECTIVE_TRUTH" -> HolisticIssueType.SELECTIVE_TRUTH
                            "CHERRY_PICKING" -> HolisticIssueType.CHERRY_PICKING
                            "NARRATIVE_SHIFT" -> HolisticIssueType.NARRATIVE_SHIFT
                            "OMISSION" -> HolisticIssueType.OMISSION
                            "MISLEADING_COMBINATION" -> HolisticIssueType.MISLEADING_COMBINATION
                            else -> return@mapNotNull null
                        }
                        val severityStr = obj["severity"]?.jsonPrimitive?.content?.uppercase() ?: "MODERATE"
                        val severity = when (severityStr.removeSurrounding("\"")) {
                            "LOW" -> IssueSeverity.LOW
                            "HIGH" -> IssueSeverity.HIGH
                            else -> IssueSeverity.MODERATE
                        }
                        val affectedSubclaims = obj["affected_subclaims"]?.jsonArray
                            ?.mapNotNull { it.jsonPrimitive.content.toIntOrNull() }
                            ?: emptyList()

                        HolisticIssue(
                            type = type,
                            description = obj["description"]?.jsonPrimitive?.content ?: "",
                            affectedSubclaims = affectedSubclaims,
                            severity = severity
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

                HolisticVerificationResult(
                    holisticVerdict = verdict,
                    holisticExplanation = explanation,
                    issuesFound = issues,
                    confidence = confidence,
                    reasoningScratchpad = analysis,
                    correctionsApplied = corrections
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse holistic response: ${e.message}")
                HolisticVerificationResult(
                    holisticVerdict = Verdict.UNVERIFIABLE,
                    holisticExplanation = "Failed to parse holistic verification response",
                    issuesFound = emptyList(),
                    confidence = 0f
                )
            }
        }
}
