package com.najmi.corvus.domain.usecase

import android.util.Log
import com.najmi.corvus.data.remote.LlmResponse
import com.najmi.corvus.domain.router.LlmProviderRouter
import com.najmi.corvus.domain.model.ClassifiedClaim
import com.najmi.corvus.domain.model.RewrittenQuery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton
import com.najmi.corvus.domain.util.TokenCollector
import com.najmi.corvus.domain.model.TokenStep


@Singleton
class QueryRewriterUseCase @Inject constructor(
    private val router: LlmProviderRouter,
    private val tokenCollector: TokenCollector
) {
    companion object {
        private const val TAG = "QueryRewriter"
        const val MAX_TOKENS = 200
        const val TIMEOUT_MS = 3000L
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun rewrite(classified: ClassifiedClaim): RewrittenQuery {
        val prompt = buildPrompt(classified)

        return try {
            val response = router.execute(prompt)
            tokenCollector.collect(response.usage.copy(
                step = TokenStep.QUERY_REWRITING,
                provider = "LlmProviderRouter",
                model = "Routed"
            ))
            parseRewrittenQuery(response.text, classified.raw)
        } catch (e: Exception) {
            Log.w(TAG, "Query rewriting failed: ${e.message}, using fallback")
            buildFallbackQuery(classified.raw)
        }
    }

    private fun buildPrompt(classified: ClassifiedClaim): String {
        val claimTypeName = classified.type?.name ?: "GENERAL"
        
        return """
You are a search query optimizer for a fact-checking system.

Your job: Take a claim (which may be biased, vague, or conversational) and generate
2-3 neutral, keyword-dense search queries that will retrieve the most relevant
factual evidence from a search engine.

RULES:
1. NEUTRALISE — Remove all emotionally charged, biased, or rhetorical language.
   "evil mayor stole funds" → "mayor [name] corruption allegations funds"
2. KEYWORD-DENSE — Use specific nouns, names, dates, and figures. No filler words.
3. DIVERSE — Each query should approach the claim from a different angle or
   use different terminology to maximise coverage.
4. PRECISE ENTITIES — Use full official names, not nicknames or pronouns.
5. TEMPORAL — If the claim references a time period, include it in at least one query.
6. MALAYSIAN CONTEXT — If the claim is about Malaysia, include "Malaysia" in
   at least one query to bias results toward local sources.
7. MAX 3 QUERIES — Do not generate more. Quality over quantity.

CLAIM: "${classified.raw}"
CLAIM TYPE: $claimTypeName
ENTITIES DETECTED: ${classified.entities.joinToString(", ").ifBlank { "None detected" }}

Respond ONLY with valid JSON:
{
  "core_question": "The single most important factual question this claim raises",
  "search_queries": [
    "keyword dense query 1",
    "keyword dense query 2",
    "keyword dense query 3"
  ],
  "query_rationale": [
    "Why query 1 was chosen",
    "Why query 2 was chosen",
    "Why query 3 was chosen"
  ]
}
        """.trimIndent()
    }

    private fun parseRewrittenQuery(raw: String, originalClaim: String): RewrittenQuery {
        return try {
            val cleaned = raw.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val jsonObj = json.parseToJsonElement(cleaned).jsonObject

            val queries: List<String> = jsonObj["search_queries"]?.jsonArray
                ?.mapNotNull { element ->
                    (element as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                }
                ?.take(3)
                ?: listOf(originalClaim)

            val rationale: List<String> = jsonObj["query_rationale"]?.jsonArray
                ?.mapNotNull { element ->
                    (element as? kotlinx.serialization.json.JsonPrimitive)?.content
                }
                ?: emptyList()

            RewrittenQuery(
                coreQuestion = jsonObj["core_question"]?.jsonPrimitive?.content ?: originalClaim,
                searchQueries = queries.ifEmpty { listOf(originalClaim) },
                queryRationale = rationale,
                originalClaim = originalClaim
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse rewritten query: ${e.message}")
            buildFallbackQuery(originalClaim)
        }
    }

    private fun buildFallbackQuery(claim: String) = RewrittenQuery(
        coreQuestion = claim,
        searchQueries = listOf(claim),
        queryRationale = listOf("Fallback: using original claim"),
        originalClaim = claim
    )
}
