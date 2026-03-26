package com.najmi.corvus.domain.usecase

import android.util.Log
import com.najmi.corvus.data.repository.GoogleFactCheckRepository
import com.najmi.corvus.data.repository.TavilyRepository
import com.najmi.corvus.domain.model.AggregatedSourceSet
import com.najmi.corvus.domain.model.ClassifiedClaim
import com.najmi.corvus.domain.model.CredibilityTier
import com.najmi.corvus.domain.model.RewrittenQuery
import com.najmi.corvus.domain.model.Source
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MultiQueryRetriever @Inject constructor(
    private val tavilyRepository: TavilyRepository,
    private val googleFactCheckRepository: GoogleFactCheckRepository
) {
    companion object {
        private const val TAG = "MultiQueryRetriever"
        const val MAX_RESULTS_PER_QUERY = 4
        const val MAX_FINAL_SOURCES = 7
        const val URL_SIMILARITY_THRESHOLD = 0.85f
    }

    suspend fun retrieve(
        rewrittenQuery: RewrittenQuery,
        classified: ClassifiedClaim
    ): AggregatedSourceSet = coroutineScope {
        val rawResults: List<Pair<String, List<Source>>> = rewrittenQuery.searchQueries.map { query ->
            async {
                val sources = fetchFromSources(query, classified)
                query to sources
            }
        }.awaitAll()

        val allSources = rawResults.flatMap { (_, sources) -> sources }
        val queriesUsed = rawResults.map { (query, _) -> query }
        val totalRaw = allSources.size

        val deduplicated = deduplicateSources(allSources)
        val ranked = rankSources(deduplicated, rewrittenQuery.coreQuestion)
            .take(MAX_FINAL_SOURCES)

        AggregatedSourceSet(
            sources = ranked,
            queryUsed = queriesUsed,
            totalRawResults = totalRaw,
            deduplicatedCount = deduplicated.size
        )
    }

    private suspend fun fetchFromSources(
        query: String,
        classified: ClassifiedClaim
    ): List<Source> = coroutineScope {
        val tavilySources = async {
            runCatching {
                tavilyRepository.search(query, maxResults = MAX_RESULTS_PER_QUERY)
            }.getOrDefault(emptyList())
        }

        val googleSources = async {
            if (isMalaysianContext(classified)) {
                runCatching {
                    val gfc = googleFactCheckRepository.search(query)
                    gfc?.sources ?: emptyList()
                }.getOrDefault(emptyList())
            } else emptyList()
        }

        tavilySources.await() + googleSources.await()
    }

    private fun deduplicateSources(sources: List<Source>): List<Source> {
        val seen = mutableSetOf<String>()
        val unique = mutableListOf<Source>()

        for (source in sources) {
            val key = normalizeUrl(source.url)
            if (key !in seen) {
                seen.add(key)
                unique.add(source)
            }
        }

        return unique
    }

    private fun normalizeUrl(url: String): String {
        return url.lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore("?")
            .trimEnd('/')
    }

    private fun rankSources(sources: List<Source>, coreQuestion: String): List<Source> {
        val questionKeywords = coreQuestion.lowercase().split(" ")
            .filter { it.length > 3 }

        return sources.sortedWith(
            compareByDescending<Source> { it.credibilityTier.ordinal }
                .thenByDescending {
                    val titleLower = it.title.lowercase()
                    questionKeywords.count { kw -> titleLower.contains(kw) }
                }
                .thenByDescending {
                    it.publicationDate?.epochDay ?: 0L
                }
        )
    }

    private fun isMalaysianContext(classified: ClassifiedClaim): Boolean {
        val malayKeywords = listOf(
            "malaysia", "kuala lumpur", "putrajaya", "sabah", "sarawak",
            "melayu", "ringgit", "dewan rakyat", "parlimen", "malaysian"
        )
        val text = (classified.raw + " " + classified.entities.joinToString(" ")).lowercase()
        return malayKeywords.any { text.contains(it) }
    }
}
