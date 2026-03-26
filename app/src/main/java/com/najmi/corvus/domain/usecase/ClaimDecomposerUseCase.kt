package com.najmi.corvus.domain.usecase

import android.util.Log
import com.najmi.corvus.data.remote.LlmResponse
import com.najmi.corvus.domain.router.LlmProviderRouter
import com.najmi.corvus.domain.model.SubClaim
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import com.najmi.corvus.domain.util.TokenCollector
import com.najmi.corvus.domain.model.TokenStep

@Serializable
data class DecomposerJson(
    val is_compound: Boolean,
    val sub_claims: List<String>
)

sealed class DecompositionResult {
    data class Single(val claim: String) : DecompositionResult()
    data class Compound(val original: String, val subClaims: List<SubClaim>) : DecompositionResult()
}


class ClaimDecomposerUseCase @Inject constructor(
    private val router: LlmProviderRouter,
    private val json: Json,
    private val tokenCollector: TokenCollector
) {
    companion object {
        private const val TAG = "ClaimDecomposer"
    }

    suspend fun decompose(raw: String): DecompositionResult {
        val prompt = """
            Analyze this statement and determine if it contains multiple independent factual claims.

            Statement: "$raw"

            A compound claim contains two or more assertions that can each be independently 
            verified as true or false (e.g. joined by "while", "and", "but", "as", "despite", 
            "even though", or structured as separate sentences).

            Respond ONLY with valid JSON:
            {
              "is_compound": true,
              "sub_claims": [
                "first atomic claim",
                "second atomic claim"
              ]
            }

            Rules:
            - Maximum 5 sub-claims. If more exist, group related ones.
            - Each sub-claim must be a complete, self-contained, checkable statement.
            - Do not split unless genuinely independent facts. "The sky is blue and clear" is NOT compound.
            - If is_compound is false, sub_claims should be empty.
        """.trimIndent()

        return try {
            val response = router.execute(prompt)
            tokenCollector.collect(response.usage.copy(
                step = TokenStep.DECOMPOSITION,
                provider = "LlmProviderRouter",
                model = "Routed"
            ))
            val jsonString = extractJson(response.text)
            val parsed = json.decodeFromString<DecomposerJson>(jsonString)

            if (parsed.is_compound && parsed.sub_claims.size >= 2) {
                DecompositionResult.Compound(
                    original = raw,
                    subClaims = parsed.sub_claims.mapIndexed { i, text ->
                        SubClaim(text = text, index = i)
                    }
                )
            } else {
                DecompositionResult.Single(raw)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Decomposition failed: ${e.message}")
            DecompositionResult.Single(raw)
        }
    }

    private fun extractJson(content: String): String {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start == -1 || end == -1) return content
        return content.substring(start, end + 1)
    }
}
