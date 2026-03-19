package com.najmi.corvus.data.remote

import com.najmi.corvus.data.repository.TavilyRepository
import com.najmi.corvus.domain.model.CredibilityTier
import com.najmi.corvus.domain.model.Source
import com.najmi.corvus.domain.model.SourceType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HansardClient @Inject constructor(
    private val tavilyRepository: TavilyRepository
) {
    /**
     * Proxies parliamentary record search via Tavily scoped to parlimen.gov.my
     */
    suspend fun searchHansard(speaker: String, keywords: String): List<Source> {
        val query = "$speaker $keywords site:parlimen.gov.my"
        return tavilyRepository.search(query, maxResults = 3).map {
            it.copy(
                sourceType = SourceType.OFFICIAL_TRANSCRIPT,
                credibilityTier = CredibilityTier.PRIMARY
            )
        }
    }
}
