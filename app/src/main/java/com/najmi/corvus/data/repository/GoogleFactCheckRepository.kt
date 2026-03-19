package com.najmi.corvus.data.repository

import com.najmi.corvus.data.remote.ClaimReview
import com.najmi.corvus.data.remote.GoogleFactCheckClient
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.Source
import com.najmi.corvus.domain.model.Verdict
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleFactCheckRepository @Inject constructor(
    private val client: GoogleFactCheckClient
) {
    suspend fun search(claim: String): CorvusCheckResult.GeneralResult? {
        return try {
            val response = client.search(claim)
            val firstReview = response.claims
                .flatMap { it.claimReview }
                .firstOrNull { it.languageCode == "en" }
                ?: return null

            parseVerdict(firstReview)?.let { verdict ->
                CorvusCheckResult.GeneralResult(
                    verdict = verdict,
                    confidence = 0.85f,
                    explanation = buildExplanation(firstReview),
                    keyFacts = emptyList(),
                    sources = listOf(
                        Source(
                            title = firstReview.title,
                            url = firstReview.url,
                            publisher = firstReview.publisher.name
                        )
                    )
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseVerdict(review: ClaimReview): Verdict? {
        val rating = review.textualRating.lowercase()
        return when {
            rating.contains("true") && !rating.contains("false") -> Verdict.TRUE
            rating.contains("false") || rating.contains("pants") -> Verdict.FALSE
            rating.contains("misleading") || rating.contains("half") || rating.contains("partial") -> Verdict.MISLEADING
            rating.contains("unverified") || rating.contains("unproven") -> Verdict.UNVERIFIABLE
            else -> null
        }
    }

    private fun buildExplanation(review: ClaimReview): String {
        return "${review.publisher.name} rates this claim as \"${review.textualRating}\"."
    }
}
