package com.najmi.corvus.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Source(
    val title: String,
    val url: String,
    val publisher: String? = null,
    val snippet: String? = null,
    val publishedDate: String? = null,
    val isLocalSource: Boolean = false,
    val sourceType: SourceType = SourceType.WEB_SEARCH,
    val credibilityTier: CredibilityTier = CredibilityTier.GENERAL
)

enum class SourceType {
    FACT_CHECK_DB,      // Google Fact Check, Sebenarnya
    OFFICIAL_TRANSCRIPT,// Hansard, PMO, ministry sites
    GOVERNMENT_DATA,    // DOSM, data.gov.my, World Bank
    NEWS_ARCHIVE,       // Bernama, The Star, FMT
    KNOWLEDGE_BASE,     // Wikipedia, Wikidata
    ACADEMIC,           // PubMed, Semantic Scholar
    WEB_SEARCH          // Tavily, Google CS general results
}

enum class CredibilityTier {
    PRIMARY,    // Official transcripts, government databases
    VERIFIED,   // Established fact-check publishers, Bernama
    GENERAL,    // General news outlets
    UNKNOWN     // Unclassified web sources
}
