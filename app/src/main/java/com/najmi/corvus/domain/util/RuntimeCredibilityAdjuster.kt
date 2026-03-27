package com.najmi.corvus.domain.util

import com.najmi.corvus.domain.model.*

object RuntimeCredibilityAdjuster {

    fun adjustForConsensus(
        sources: List<Source>,
        claimType: ClaimType
    ): List<Source> {
        val highCredSources = sources.filter {
            (it.outletRating?.credibility ?: 0) >= 75
        }

        // Consensus bonus: 3+ high-cred sources agreeing adds 5 points to each
        val consensusBonus = if (highCredSources.size >= 3) 5 else 0

        // Primary source bonus: official gov/transcript source present → boost all
        val hasPrimarySource = sources.any {
            it.credibilityTier == CredibilityTier.PRIMARY
        }
        val primaryBonus = if (hasPrimarySource) 8 else 0

        return sources.map { source ->
            val rating = source.outletRating ?: return@map source
            val adjustedCredibility = (rating.credibility + consensusBonus + primaryBonus).coerceAtMost(100)

            source.copy(
                outletRating = rating.copy(
                    credibility = adjustedCredibility
                )
            )
        }
    }

    fun penaliseLoneSource(
        source: Source,
        allSources: List<Source>
    ): Source {
        val agreeingCount = allSources.count { other ->
            other.url != source.url &&
            roughlySameContent(source.snippet, other.snippet)
        }

        if (agreeingCount == 0 && allSources.size >= 3) {
            val rating = source.outletRating ?: return source
            val penalised = (rating.credibility * 0.85f).toInt()
            return source.copy(
                outletRating = rating.copy(credibility = penalised)
            )
        }

        return source
    }

    private fun roughlySameContent(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        val aWords = a.lowercase().split(" ").filter { it.length > 4 }.take(8).toSet()
        val bWords = b.lowercase().split(" ").filter { it.length > 4 }.take(8).toSet()
        if (aWords.isEmpty()) return false
        val overlap = aWords.intersect(bWords).size.toFloat() / aWords.size
        return overlap > 0.5f
    }
}
