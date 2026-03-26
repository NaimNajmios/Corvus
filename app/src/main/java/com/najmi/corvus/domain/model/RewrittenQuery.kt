package com.najmi.corvus.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RewrittenQuery(
    val coreQuestion: String,
    val searchQueries: List<String>,
    val queryRationale: List<String>,
    val originalClaim: String
)

@Serializable
data class AggregatedSourceSet(
    val sources: List<Source>,
    val queryUsed: List<String>,
    val totalRawResults: Int,
    val deduplicatedCount: Int
)

@Serializable
data class RetrievalMetadata(
    val originalClaim: String,
    val rewrittenQueries: List<String>,
    val coreQuestion: String,
    val totalRawSources: Int,
    val dedupedSources: Int,
    val finalSources: Int
)
