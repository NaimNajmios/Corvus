# Corvus — Enhancement Plan VIII
## Pre-Retrieval Query Rewriting · Temporal Anchoring

> **Scope:** Two pre-LLM pipeline enhancements that address retrieval quality and temporal misinformation — the two most common failure modes in production fact-checking systems. Neither requires new APIs or additional cost. Both compound with every other enhancement already planned.

---

## Why These Two Fixes Are Foundational

Every enhancement in Plans I–VII assumes the retrieved sources are *relevant* and *temporally accurate*. If retrieval returns echo-chamber results for a biased query, even the best Actor-Critic pipeline reasons over bad evidence. If publication dates are missing or ignored, even perfect reasoning can be fooled by a 2019 video presented as breaking news.

These two fixes address the quality of inputs *before* the LLM sees them:

```
User claim (raw, biased, vague)
    ↓
[Fix 1: Query Rewriter]   → Neutral, keyword-dense queries → Better sources
[Fix 2: Temporal Anchor]  → Date-enforced prompt + source validation → Zombie claim detection
    ↓
Actor-Critic pipeline (Plans VII) now reasons over higher-quality, date-verified sources
```

---

---

# Fix 1 — Pre-Retrieval: LLM Query Rewriting

## The Problem

```kotlin
// GeneralFactCheckPipeline.kt — current state (the problem)
tavilyRepository.search(classified.raw, maxResults = 3)
```

`classified.raw` is the raw user input, passed directly to the search engine. This fails in three common scenarios:

**Scenario A — Biased framing:**
> *"Did the evil mayor really steal the funds?"*

Tavily receives "evil mayor steal funds" as the query. Returns results from partisan outlets that use similar language — confirming the bias rather than checking the fact.

**Scenario B — Vague or conversational:**
> *"I heard that the new tax thing will destroy the economy?"*

Tavily receives a vague, conversational query. Returns low-relevance results. LLM reasons over thin evidence.

**Scenario C — Compound claim (not yet decomposed):**
> *"Anwar raised taxes AND unemployment doubled under his watch"*

Tavily receives the full compound as one query. Returns results that may address only one sub-claim, giving incomplete evidence for both.

### The Fix

A fast, cheap LLM call (Groq, max 200 tokens) before any retrieval:

```
Raw claim → [Query Rewriter] → 2-3 neutral, keyword-dense search queries
                                    ↓
                            Tavily called once per query
                                    ↓
                            Results merged + deduplicated
                                    ↓
                            Richer, more balanced source set
```

---

## Query Rewriter Design

### Core Responsibilities

1. **Neutralise bias** — Strip charged language. Extract the underlying factual question.
2. **Generate keyword variants** — 2-3 queries, each optimised for search (not conversation).
3. **Temporal specificity** — Include year/date if claim references a specific time.
4. **Entity precision** — Use full, unambiguous entity names (not pronouns or nicknames).

### Prompt

```kotlin
const val QUERY_REWRITER_PROMPT = """
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

CLAIM: "{claim}"
CLAIM TYPE: {claim_type}
DETECTED LANGUAGE: {language}
ENTITIES DETECTED: {entities}

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

EXAMPLES:

Claim: "Did the evil mayor really steal the funds?"
Output:
{
  "core_question": "Did the mayor misappropriate public funds?",
  "search_queries": [
    "mayor corruption funds misappropriation",
    "mayor embezzlement investigation charges",
    "mayor public funds audit findings"
  ]
}

Claim: "The new tax law will bankrupt the middle class by 2026"
Output:
{
  "core_question": "What is the projected economic impact of the new tax law on middle-income earners by 2026?",
  "search_queries": [
    "new tax law 2026 middle class economic impact",
    "tax reform economic projections income bracket effects",
    "Malaysia tax law 2026 household income analysis"
  ]
}

Claim: "Anwar Ibrahim naik gaji sendiri"
Output:
{
  "core_question": "Did Anwar Ibrahim increase his own salary?",
  "search_queries": [
    "Anwar Ibrahim salary increase Prime Minister Malaysia",
    "gaji Perdana Menteri Malaysia 2024 kenaikan",
    "Malaysia PM remuneration parliament approval"
  ]
}
"""
```

---

## Data Models

```kotlin
// domain/model/RewrittenQuery.kt

data class RewrittenQuery(
    val coreQuestion   : String,
    val searchQueries  : List<String>,   // 2-3 items
    val queryRationale : List<String>,   // Parallel to searchQueries
    val originalClaim  : String
)

// Extended source set from multi-query retrieval
data class AggregatedSourceSet(
    val sources           : List<CorvusSource>,
    val queryUsed         : List<String>,    // Which queries produced sources
    val totalRawResults   : Int,             // Before deduplication
    val deduplicatedCount : Int              // After deduplication
)
```

---

## Phase 1.1 — Query Rewriter Use Case

```kotlin
// domain/usecase/QueryRewriterUseCase.kt

class QueryRewriterUseCase @Inject constructor(
    private val groqClient: GroqClient
) {
    companion object {
        const val MAX_TOKENS   = 200    // Short output — just queries
        const val TIMEOUT_MS   = 3000L  // Hard timeout — fast or skip
        const val FALLBACK_ON_FAILURE = true
    }

    suspend fun rewrite(classified: ClassifiedClaim): RewrittenQuery {
        val prompt = QUERY_REWRITER_PROMPT
            .replace("{claim}",      classified.raw)
            .replace("{claim_type}", classified.type.name)
            .replace("{language}",   classified.language.name)
            .replace("{entities}",   classified.entities.joinToString(", ")
                .ifBlank { "None detected" })

        return try {
            withTimeout(TIMEOUT_MS) {
                val raw = groqClient.complete(
                    prompt    = prompt,
                    model     = "llama-3.3-70b-versatile",
                    maxTokens = MAX_TOKENS
                )
                parseRewrittenQuery(raw, classified.raw)
            }
        } catch (e: Exception) {
            // Fallback — use raw claim as single query, never block retrieval
            buildFallbackQuery(classified.raw)
        }
    }

    private fun parseRewrittenQuery(raw: String, originalClaim: String): RewrittenQuery {
        return try {
            val json    = extractAndParseJson(raw)
            val queries = json["search_queries"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?.filter { it.isNotBlank() }
                ?.take(3)
                ?: listOf(originalClaim)

            RewrittenQuery(
                coreQuestion   = json["core_question"]
                    ?.jsonPrimitive?.content ?: originalClaim,
                searchQueries  = queries.ifEmpty { listOf(originalClaim) },
                queryRationale = json["query_rationale"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?: emptyList(),
                originalClaim  = originalClaim
            )
        } catch (e: Exception) {
            buildFallbackQuery(originalClaim)
        }
    }

    // If rewriter fails for any reason — fall back to raw claim, never break pipeline
    private fun buildFallbackQuery(claim: String) = RewrittenQuery(
        coreQuestion   = claim,
        searchQueries  = listOf(claim),
        queryRationale = listOf("Fallback: using original claim"),
        originalClaim  = claim
    )
}
```

---

## Phase 1.2 — Multi-Query Retrieval Aggregator

```kotlin
// domain/usecase/MultiQueryRetriever.kt

class MultiQueryRetriever @Inject constructor(
    private val tavilyRepository     : TavilyRepository,
    private val googleCsRepository   : GoogleCustomSearchRepository,
    private val rssCacheRepository   : RssCacheRepository
) {
    companion object {
        const val MAX_RESULTS_PER_QUERY  = 4    // 3 queries × 4 = 12 raw results
        const val MAX_FINAL_SOURCES      = 7    // After dedup — feed to LLM
        const val URL_SIMILARITY_THRESHOLD = 0.85f  // For URL-based dedup
    }

    suspend fun retrieve(
        rewrittenQuery : RewrittenQuery,
        classified     : ClassifiedClaim
    ): AggregatedSourceSet {

        // Run all queries in parallel — coroutines
        val rawResults: List<Pair<String, List<CorvusSource>>> = coroutineScope {
            rewrittenQuery.searchQueries.map { query ->
                async {
                    val sources = fetchFromAllSources(query, classified)
                    query to sources
                }
            }.awaitAll()
        }

        // Flatten all results
        val allSources    = rawResults.flatMap { (_, sources) -> sources }
        val queriesUsed   = rawResults.map { (query, _) -> query }
        val totalRaw      = allSources.size

        // Deduplicate
        val deduplicated  = deduplicateSources(allSources)

        // Rank by relevance and credibility
        val ranked = rankSources(
            sources    = deduplicated,
            coreQuestion = rewrittenQuery.coreQuestion
        ).take(MAX_FINAL_SOURCES)

        return AggregatedSourceSet(
            sources           = ranked,
            queryUsed         = queriesUsed,
            totalRawResults   = totalRaw,
            deduplicatedCount = deduplicated.size
        )
    }

    private suspend fun fetchFromAllSources(
        query      : String,
        classified : ClassifiedClaim
    ): List<CorvusSource> = coroutineScope {

        // 1. RSS cache — instant, offline
        val rssSources = async {
            runCatching {
                rssCacheRepository.search(query)
                    .map { it.toCorvusSource() }
            }.getOrDefault(emptyList())
        }

        // 2. Tavily — primary web retrieval
        val tavilySources = async {
            runCatching {
                tavilyRepository.search(query, maxResults = MAX_RESULTS_PER_QUERY)
            }.getOrDefault(emptyList())
        }

        // 3. Google Custom Search — MY-filtered for Malaysian claims
        val gscSources = async {
            if (classified.language == ClaimLanguage.BAHASA_MALAYSIA ||
                classified.entities.any { isMalaysianEntity(it) }) {
                runCatching {
                    googleCsRepository.search(query, maxResults = 3)
                }.getOrDefault(emptyList())
            } else emptyList()
        }

        (rssSources.await() + tavilySources.await() + gscSources.await())
    }

    // ── Deduplication ────────────────────────────────────────────────────

    private fun deduplicateSources(sources: List<CorvusSource>): List<CorvusSource> {
        val seen    = mutableSetOf<String>()
        val unique  = mutableListOf<CorvusSource>()

        for (source in sources) {
            val key = normalizeUrl(source.url ?: source.title)
            if (key !in seen) {
                seen.add(key)
                unique.add(source)
            }
        }

        return unique
    }

    // Normalize URL for deduplication — strip protocol, www, trailing slash, query params
    private fun normalizeUrl(url: String): String {
        return url.lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore("?")   // Strip query params
            .trimEnd('/')
    }

    // ── Ranking ──────────────────────────────────────────────────────────

    private fun rankSources(
        sources      : List<CorvusSource>,
        coreQuestion : String
    ): List<CorvusSource> {
        val questionKeywords = coreQuestion.lowercase().split(" ")
            .filter { it.length > 3 }

        return sources.sortedWith(
            compareByDescending<CorvusSource> {
                // Priority 1: Credibility tier
                it.credibilityTier.ordinal
            }.thenByDescending {
                // Priority 2: Keyword relevance in title
                val titleLower = it.title.lowercase()
                questionKeywords.count { kw -> titleLower.contains(kw) }
            }.thenByDescending {
                // Priority 3: Recency — sources with dates ranked above undated
                it.publicationDate?.let { date ->
                    runCatching { parseDate(date).toEpochDay() }.getOrDefault(0L)
                } ?: 0L
            }
        )
    }

    private fun isMalaysianEntity(entity: String): Boolean {
        val malayKeywords = listOf(
            "malaysia", "kuala lumpur", "putrajaya", "sabah", "sarawak",
            "melayu", "ringgit", "dewan rakyat", "parlimen"
        )
        return malayKeywords.any { entity.lowercase().contains(it) }
    }
}
```

---

## Phase 1.3 — Pipeline Integration

```kotlin
// domain/usecase/GeneralFactCheckPipeline.kt — updated retrieve() function

class GeneralFactCheckPipeline @Inject constructor(
    private val queryRewriter      : QueryRewriterUseCase,         // NEW
    private val multiQueryRetriever: MultiQueryRetriever,          // NEW
    private val actorCriticPipeline: ActorCriticPipeline,
    private val ragVerifier        : RagVerifierUseCase,
    private val algorithmicVerifier: AlgorithmicGroundingVerifier
) {
    suspend fun verify(classified: ClassifiedClaim): CorvusCheckResult {

        // ── Step 1: Query Rewriting (NEW) ────────────────────────────────
        val rewrittenQuery = queryRewriter.rewrite(classified)
        // ────────────────────────────────────────────────────────────────

        // ── Step 2: Multi-Query Retrieval (NEW) ──────────────────────────
        val sourceSet = multiQueryRetriever.retrieve(rewrittenQuery, classified)
        // ────────────────────────────────────────────────────────────────

        // ── Step 3: Actor-Critic Analysis (Plan VII) ─────────────────────
        val llmResult = actorCriticPipeline.analyze(
            claim      = rewrittenQuery.coreQuestion,  // Use neutralised question
            classified = classified,
            sources    = sourceSet.sources
        )
        // ────────────────────────────────────────────────────────────────

        // ── Steps 4–5: RAG + Algorithmic Verification (Plan VI + VII) ────
        val ragVerified = ragVerifier.verifyFacts(llmResult.keyFacts, sourceSet.sources)
        val groundingVerified = algorithmicVerifier.verify(ragVerified, sourceSet.sources)
        // ────────────────────────────────────────────────────────────────

        return llmResult.copy(
            keyFacts             = groundingVerified.verifiedFacts,
            confidence           = llmResult.confidence - groundingVerified.totalConfidencePenalty,
            retrievalMetadata    = RetrievalMetadata(         // NEW
                originalClaim     = classified.raw,
                rewrittenQueries  = rewrittenQuery.searchQueries,
                coreQuestion      = rewrittenQuery.coreQuestion,
                totalRawSources   = sourceSet.totalRawResults,
                dedupedSources    = sourceSet.deduplicatedCount,
                finalSources      = sourceSet.sources.size
            )
        )
    }
}

// Metadata attached to result — surfaced in Methodology Card
data class RetrievalMetadata(
    val originalClaim    : String,
    val rewrittenQueries : List<String>,
    val coreQuestion     : String,
    val totalRawSources  : Int,
    val dedupedSources   : Int,
    val finalSources     : Int
)
```

---

## Phase 1.4 — UI: Retrieval Transparency in Methodology Card

Expose query rewriting in the Methodology Card so users can see how Corvus translated their claim:

```kotlin
// In MethodologyCard — new "RETRIEVAL" section

result.retrievalMetadata?.let { meta ->
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = CorvusTheme.colors.border)
    Spacer(Modifier.height(8.dp))

    Text(
        "RETRIEVAL",
        style     = CorvusTheme.typography.labelSmall,
        color     = CorvusTheme.colors.textSecondary,
        fontFamily = IbmPlexMono
    )
    Spacer(Modifier.height(6.dp))

    // Core question derived from claim
    if (meta.coreQuestion != meta.originalClaim) {
        Column {
            Text(
                "Core question:",
                style = CorvusTheme.typography.caption,
                color = CorvusTheme.colors.textTertiary
            )
            Text(
                meta.coreQuestion,
                style = CorvusTheme.typography.caption,
                color = CorvusTheme.colors.textSecondary,
                fontStyle = FontStyle.Italic
            )
        }
        Spacer(Modifier.height(6.dp))
    }

    // Search queries used
    Column {
        Text(
            "Search queries (${meta.rewrittenQueries.size}):",
            style = CorvusTheme.typography.caption,
            color = CorvusTheme.colors.textTertiary
        )
        meta.rewrittenQueries.forEach { query ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    "›",
                    style = CorvusTheme.typography.caption,
                    color = CorvusTheme.colors.accent
                )
                Text(
                    "\"$query\"",
                    style     = CorvusTheme.typography.caption,
                    color     = CorvusTheme.colors.textSecondary,
                    fontFamily = IbmPlexMono
                )
            }
        }
    }

    Spacer(Modifier.height(6.dp))

    // Source aggregation stats
    MethodologyStatRow(
        "Sources retrieved",
        "${meta.totalRawSources} raw → ${meta.dedupedSources} unique → ${meta.finalSources} used"
    )
}
```

---

## Feature 1 Tasks

- [ ] Define `QUERY_REWRITER_PROMPT` constant with all 7 rules and 3 examples
- [ ] Implement `RewrittenQuery` data class
- [ ] Implement `AggregatedSourceSet` data class
- [ ] Implement `RetrievalMetadata` data class
- [ ] Implement `QueryRewriterUseCase` with 3-second timeout and fallback
- [ ] Implement `parseRewrittenQuery()` with fallback on parse failure
- [ ] Implement `buildFallbackQuery()` — raw claim as single query
- [ ] Implement `MultiQueryRetriever` with parallel coroutine fetching
- [ ] Implement `deduplicateSources()` with URL normalisation
- [ ] Implement `normalizeUrl()` — strip protocol, www, query params
- [ ] Implement `rankSources()` — credibility, keyword relevance, recency
- [ ] Implement `isMalaysianEntity()` keyword heuristic
- [ ] Replace `tavilyRepository.search(classified.raw)` in `GeneralFactCheckPipeline`
- [ ] Wire `QueryRewriterUseCase` and `MultiQueryRetriever` into Hilt
- [ ] Add `retrievalMetadata` field to `CorvusCheckResult.GeneralResult`
- [ ] Add retrieval section to `MethodologyCard` composable
- [ ] Unit test: `QueryRewriterUseCase` — biased claim, vague claim, BM claim
- [ ] Unit test: `buildFallbackQuery()` — returns raw claim on exception
- [ ] Unit test: `deduplicateSources()` — same URL different protocol, same URL different query params
- [ ] Unit test: `normalizeUrl()` — 8 URL format variants
- [ ] Unit test: `rankSources()` — credibility tier takes priority over keyword relevance
- [ ] Integration test: biased claim → rewritten queries neutral and keyword-dense
- [ ] Integration test: parallel retrieval from Tavily + RSS + Google CS
- [ ] Integration test: compound claim generates 3 diverse queries
- [ ] Performance test: query rewriting adds <= 1.5 seconds to total pipeline

**Estimated duration: 5 days**

---

---

# Fix 2 — Temporal Anchoring (Defeating Zombie Claims)

## The Problem

Zombie claims are the dominant form of Malaysian viral misinformation on WhatsApp:
- A video of a 2018 protest shared with caption "look what's happening NOW"
- A 2020 flood image shared during a 2024 election campaign
- A statistic from a 2019 report cited as "new data"

The current pipeline fails here in three compounding ways:

**Problem A:** `publicationDate` is optional on `CorvusSource`. Tavily sometimes returns it, sometimes doesn't. The LLM never knows whether a source is recent or stale.

**Problem B:** The LLM prompt has no concept of *today's date*. It cannot determine whether a source is temporally relevant to the claim.

**Problem C:** Even when source dates are available, the prompt has no instruction to check them. The LLM focuses on content, not temporality.

---

## The Fix

Three coordinated changes:

1. **Make `publicationDate` a first-class field** — extract aggressively from all source types, estimate where extraction fails
2. **Inject current date into the prompt** — give the LLM an absolute temporal anchor
3. **Add explicit temporal check instruction** — mandate MISLEADING verdict for temporal mismatches

---

## Phase 2.1 — Publication Date as First-Class Field

### Source Model Update

```kotlin
// domain/model/CorvusSource.kt — updated

data class CorvusSource(
    val title              : String,
    val url                : String,
    val publisher          : String?,
    val snippet            : String?,
    val rawContent         : String?,
    val isLocalSource      : Boolean = false,
    val sourceType         : SourceType = SourceType.WEB,
    val credibilityTier    : CredibilityTier = CredibilityTier.GENERAL,
    val outletRating       : OutletRating? = null,

    // UPDATED — was String?, now a typed model
    val publicationDate    : PublicationDate? = null
)

// Typed publication date — tracks confidence in the date
data class PublicationDate(
    val raw         : String,           // Raw string from source ("2024-03-15", "March 2024", etc.)
    val epochDay    : Long?,            // Parsed epoch day — null if unparseable
    val confidence  : DateConfidence,
    val formattedDisplay: String        // Human-readable: "15 Mar 2024" or "Mar 2024" or "~2024"
)

enum class DateConfidence {
    EXACT,       // Full date parsed: 2024-03-15
    MONTH_YEAR,  // Only month/year: March 2024
    YEAR_ONLY,   // Only year: 2024
    ESTIMATED,   // Estimated from content signals (e.g., event references)
    UNKNOWN      // Cannot determine
}
```

### Publication Date Extractor

```kotlin
// domain/util/PublicationDateExtractor.kt

object PublicationDateExtractor {

    // Common date patterns in Tavily/RSS responses
    private val DATE_PATTERNS = listOf(
        // ISO 8601
        Regex("""(\d{4})-(\d{2})-(\d{2})"""),
        // Common article formats
        Regex("""(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+(\d{4})""", RegexOption.IGNORE_CASE),
        Regex("""(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+(\d{1,2}),?\s+(\d{4})""", RegexOption.IGNORE_CASE),
        // Year-month only
        Regex("""(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+(\d{4})""", RegexOption.IGNORE_CASE),
        // Year only (last resort)
        Regex("""\b(20\d{2})\b""")
    )

    fun extract(rawDateString: String?): PublicationDate? {
        if (rawDateString.isNullOrBlank()) return null

        // Try ISO 8601 first (most reliable)
        DATE_PATTERNS[0].find(rawDateString)?.let { match ->
            val (year, month, day) = match.destructured
            return runCatching {
                val date    = LocalDate.of(year.toInt(), month.toInt(), day.toInt())
                PublicationDate(
                    raw              = rawDateString,
                    epochDay         = date.toEpochDay(),
                    confidence       = DateConfidence.EXACT,
                    formattedDisplay = date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
                )
            }.getOrNull()
        }

        // Try common article date formats
        for (i in 1..2) {
            DATE_PATTERNS[i].find(rawDateString)?.let { match ->
                return parseArticleDate(rawDateString, match, confidence = DateConfidence.EXACT)
            }
        }

        // Month-year only
        DATE_PATTERNS[3].find(rawDateString)?.let { match ->
            return parseMonthYearDate(rawDateString, match)
        }

        // Year only
        DATE_PATTERNS[4].find(rawDateString)?.let { match ->
            val year = match.groupValues[1].toIntOrNull() ?: return null
            return PublicationDate(
                raw              = rawDateString,
                epochDay         = LocalDate.of(year, 6, 15).toEpochDay(), // Mid-year estimate
                confidence       = DateConfidence.YEAR_ONLY,
                formattedDisplay = "~$year"
            )
        }

        return null
    }

    // Extract date from raw content when metadata is missing
    fun extractFromContent(content: String): PublicationDate? {
        // Look for date signals in first 500 chars (usually in article header/byline)
        val header = content.take(500)
        for (pattern in DATE_PATTERNS.take(3)) {  // Only try precise patterns
            pattern.find(header)?.let { match ->
                return extract(match.value)?.copy(confidence = DateConfidence.ESTIMATED)
            }
        }
        return null
    }

    private fun parseArticleDate(
        raw        : String,
        match      : MatchResult,
        confidence : DateConfidence
    ): PublicationDate? {
        return runCatching {
            val formatter1 = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
            val formatter2 = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
            val date = runCatching { LocalDate.parse(match.value.trim(), formatter1) }
                .getOrElse { LocalDate.parse(match.value.trim(), formatter2) }

            PublicationDate(
                raw              = raw,
                epochDay         = date.toEpochDay(),
                confidence       = confidence,
                formattedDisplay = date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
            )
        }.getOrNull()
    }

    private fun parseMonthYearDate(raw: String, match: MatchResult): PublicationDate? {
        return runCatching {
            val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
            val date      = YearMonth.parse(match.value.trim(), formatter)
                .atDay(1)

            PublicationDate(
                raw              = raw,
                epochDay         = date.toEpochDay(),
                confidence       = DateConfidence.MONTH_YEAR,
                formattedDisplay = date.format(DateTimeFormatter.ofPattern("MMM yyyy"))
            )
        }.getOrNull()
    }
}
```

### Source Enrichment — Date Extraction Pipeline

Apply date extraction at every source ingest point:

```kotlin
// In TavilyClient — enrich sources after API response
fun TavilyResult.toCorvusSource(): CorvusSource {
    val extractedDate = PublicationDateExtractor.extract(this.publishedDate)
        ?: PublicationDateExtractor.extractFromContent(this.rawContent ?: this.snippet ?: "")

    return CorvusSource(
        title           = this.title,
        url             = this.url,
        publisher       = extractPublisher(this.url),
        snippet         = this.snippet,
        rawContent      = this.rawContent,
        publicationDate = extractedDate,
        sourceType      = SourceType.WEB
    )
}

// In RssCacheEntity — RSS always has pubDate — extract it
fun RssCacheEntity.toCorvusSource(): CorvusSource {
    val date = PublicationDateExtractor.extract(this.publishedAt.toString())
        ?: PublicationDateExtractor.extract(
            Instant.ofEpochMilli(this.publishedAt)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
        )

    return CorvusSource(
        title           = this.title,
        url             = this.url,
        publisher       = this.publisher,
        snippet         = this.snippet,
        publicationDate = date,
        isLocalSource   = true,
        sourceType      = SourceType.NEWS_ARCHIVE
    )
}
```

---

## Phase 2.2 — Temporal Claim Analyser

Before the main LLM prompt is built, classify the claim's temporal nature:

```kotlin
// domain/usecase/TemporalClaimAnalyser.kt

data class TemporalClaimProfile(
    val impliedTimeline    : ImpliedTimeline,
    val claimDateSignals   : List<String>,   // Dates/periods mentioned in claim
    val isLikelyZombie     : Boolean,        // Heuristic pre-check
    val temporalUrgency    : TemporalUrgency
)

enum class ImpliedTimeline {
    CURRENT,         // "is", "now", "today", "this week" — implies present
    RECENT,          // "yesterday", "last week", "recently"
    HISTORICAL,      // "in 2018", "last year", specific past dates
    TIMELESS,        // No temporal signal — general claim
    UNDETECTED
}

enum class TemporalUrgency {
    HIGH,    // Claim implies very recent event — zombie check critical
    MEDIUM,  // Some temporal signal present
    LOW      // Historical or timeless claim — temporal check less critical
}

object TemporalClaimAnalyser {

    private val CURRENT_SIGNALS = listOf(
        "sekarang", "kini", "tadi", "baru", "hari ini", "petang ini",
        "now", "today", "just", "breaking", "tonight", "this morning",
        "currently", "happening", "right now", "at this moment"
    )

    private val RECENT_SIGNALS = listOf(
        "semalam", "minggu lepas", "baru-baru ini",
        "yesterday", "last week", "last night", "recently",
        "this month", "this year", "few days ago"
    )

    private val DATE_YEAR_PATTERN = Regex("""\b(20\d{2})\b""")

    fun analyze(claim: String, currentDate: LocalDate): TemporalClaimProfile {
        val lower          = claim.lowercase()
        val yearsInClaim   = DATE_YEAR_PATTERN.findAll(claim)
            .map { it.value }
            .toList()

        val isCurrent  = CURRENT_SIGNALS.any { lower.contains(it) }
        val isRecent   = RECENT_SIGNALS.any { lower.contains(it) }
        val isHistorical = yearsInClaim.any {
            it.toIntOrNull()?.let { year -> year < currentDate.year - 1 } == true
        }

        val timeline = when {
            isCurrent    -> ImpliedTimeline.CURRENT
            isRecent     -> ImpliedTimeline.RECENT
            isHistorical -> ImpliedTimeline.HISTORICAL
            else         -> ImpliedTimeline.TIMELESS
        }

        // Zombie heuristic: claim uses present-tense language but...
        // we can't know if sources will be old until retrieval
        // Flag as potentially zombie if claim implies current event
        val isLikelyZombie = isCurrent || isRecent

        val urgency = when {
            isCurrent -> TemporalUrgency.HIGH
            isRecent  -> TemporalUrgency.MEDIUM
            else      -> TemporalUrgency.LOW
        }

        return TemporalClaimProfile(
            impliedTimeline  = timeline,
            claimDateSignals = yearsInClaim,
            isLikelyZombie   = isLikelyZombie,
            temporalUrgency  = urgency
        )
    }
}
```

---

## Phase 2.3 — Source Temporal Mismatch Detector

After retrieval, run a deterministic pre-check before the LLM to flag potential zombie sources:

```kotlin
// domain/usecase/SourceTemporalMismatchDetector.kt

data class TemporalMismatchReport(
    val hasSignificantMismatch : Boolean,
    val oldestSourceAge        : Int?,     // Days old
    val newestSourceAge        : Int?,     // Days old
    val sourcesWithDates       : Int,
    val sourcesWithoutDates    : Int,
    val mismatchDetails        : List<SourceMismatch>,
    val suggestedVerdict       : Verdict?  // MISLEADING if strong mismatch detected
)

data class SourceMismatch(
    val sourceIndex  : Int,
    val sourceDate   : String,
    val ageDays      : Int,
    val publisher    : String?
)

class SourceTemporalMismatchDetector @Inject constructor() {

    companion object {
        // A source is "old" relative to a current claim if it's more than this many days
        const val ZOMBIE_THRESHOLD_DAYS    = 180   // 6 months
        const val STRONG_MISMATCH_DAYS     = 365   // 1 year — strong zombie signal
    }

    fun detect(
        sources       : List<CorvusSource>,
        profile       : TemporalClaimProfile,
        currentDate   : LocalDate
    ): TemporalMismatchReport {

        if (profile.temporalUrgency == TemporalUrgency.LOW) {
            // Historical or timeless claims — temporal mismatch is expected, not a red flag
            return TemporalMismatchReport(
                hasSignificantMismatch = false,
                oldestSourceAge        = null,
                newestSourceAge        = null,
                sourcesWithDates       = sources.count { it.publicationDate != null },
                sourcesWithoutDates    = sources.count { it.publicationDate == null },
                mismatchDetails        = emptyList(),
                suggestedVerdict       = null
            )
        }

        val datedSources = sources.mapIndexedNotNull { index, source ->
            val epochDay = source.publicationDate?.epochDay ?: return@mapIndexedNotNull null
            val sourceDate = LocalDate.ofEpochDay(epochDay)
            val ageDays    = ChronoUnit.DAYS.between(sourceDate, currentDate).toInt()

            if (ageDays > 0) {  // Only flag genuinely old sources
                SourceMismatch(
                    sourceIndex = index,
                    sourceDate  = source.publicationDate?.formattedDisplay ?: sourceDate.toString(),
                    ageDays     = ageDays,
                    publisher   = source.publisher
                )
            } else null
        }

        val significantMismatches = datedSources.filter { it.ageDays > ZOMBIE_THRESHOLD_DAYS }
        val strongMismatches      = datedSources.filter { it.ageDays > STRONG_MISMATCH_DAYS }

        // Determine if this is likely a zombie claim situation
        val hasSignificantMismatch = profile.impliedTimeline == ImpliedTimeline.CURRENT &&
            significantMismatches.size >= (datedSources.size * 0.6).toInt() &&
            datedSources.isNotEmpty()

        // Suggest MISLEADING if strong temporal mismatch detected
        val suggestedVerdict = if (hasSignificantMismatch &&
            strongMismatches.size >= (datedSources.size * 0.5).toInt()) {
            Verdict.MISLEADING
        } else null

        return TemporalMismatchReport(
            hasSignificantMismatch = hasSignificantMismatch,
            oldestSourceAge        = datedSources.maxOfOrNull { it.ageDays },
            newestSourceAge        = datedSources.minOfOrNull { it.ageDays },
            sourcesWithDates       = datedSources.size,
            sourcesWithoutDates    = sources.size - datedSources.size,
            mismatchDetails        = significantMismatches,
            suggestedVerdict       = suggestedVerdict
        )
    }
}
```

---

## Phase 2.4 — Prompt Temporal Injection

The temporal context is injected into the Actor prompt as a system-level instruction:

```kotlin
// domain/remote/llm/TemporalPromptInjector.kt

object TemporalPromptInjector {

    fun buildTemporalContext(
        currentDate    : LocalDate,
        profile        : TemporalClaimProfile,
        mismatchReport : TemporalMismatchReport,
        sources        : List<CorvusSource>
    ): String = buildString {

        // 1. Inject current date — absolute anchor
        appendLine("TEMPORAL CONTEXT:")
        appendLine("Current date: ${currentDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))}")
        appendLine()

        // 2. Claim's implied timeline
        appendLine("Claim's implied timeline: ${profile.impliedTimeline.name}")
        if (profile.claimDateSignals.isNotEmpty()) {
            appendLine("Date signals in claim: ${profile.claimDateSignals.joinToString(", ")}")
        }
        appendLine()

        // 3. Source date summary
        appendLine("SOURCE PUBLICATION DATES:")
        sources.forEachIndexed { index, source ->
            val dateInfo = source.publicationDate?.let {
                "${it.formattedDisplay} (${it.confidence.name})"
            } ?: "DATE UNKNOWN"
            appendLine("  Source [$index] ${source.publisher ?: "Unknown"}: $dateInfo")
        }
        appendLine()

        // 4. Mismatch warning if detected
        if (mismatchReport.hasSignificantMismatch) {
            appendLine("⚠ TEMPORAL MISMATCH DETECTED:")
            appendLine("The claim implies a current or recent event, but ${mismatchReport.mismatchDetails.size} source(s)")
            appendLine("are more than ${SourceTemporalMismatchDetector.ZOMBIE_THRESHOLD_DAYS} days old.")
            mismatchReport.mismatchDetails.forEach { mismatch ->
                appendLine("  - Source [${mismatch.sourceIndex}] (${mismatch.publisher}): ${mismatch.ageDays} days old")
            }
            appendLine()
        }

        // 5. Temporal check instruction
        appendLine(buildTemporalCheckInstruction(profile, mismatchReport))
    }

    private fun buildTemporalCheckInstruction(
        profile        : TemporalClaimProfile,
        mismatchReport : TemporalMismatchReport
    ): String = buildString {

        appendLine("TEMPORAL CHECK RULES (MANDATORY):")
        appendLine()

        when (profile.impliedTimeline) {
            ImpliedTimeline.CURRENT, ImpliedTimeline.RECENT -> {
                appendLine("1. This claim implies a CURRENT or RECENT event.")
                appendLine("   You MUST check the publication dates of all sources.")
                appendLine()
                appendLine("2. ZOMBIE CLAIM RULE:")
                appendLine("   If the sources describe an event that occurred more than 6 months")
                appendLine("   before today's date, and the claim implies this is recent:")
                appendLine("   → You MUST set verdict to MISLEADING")
                appendLine("   → You MUST set missing_context.context_type to TEMPORAL")
                appendLine("   → Explain that sources describe an old event presented as current")
                appendLine()
                appendLine("3. UNDATED SOURCE RULE:")
                appendLine("   If a source has no publication date, do NOT assume it is recent.")
                appendLine("   Note the missing date in your reasoning and weight it lower.")

                if (mismatchReport.hasSignificantMismatch) {
                    appendLine()
                    appendLine("4. ⚠ PRE-CHECK RESULT: A temporal mismatch has already been detected.")
                    appendLine("   Sources are significantly older than the claim implies.")
                    appendLine("   The burden of proof for NOT returning MISLEADING is HIGH.")
                    mismatchReport.suggestedVerdict?.let {
                        appendLine("   Suggested verdict based on temporal analysis: ${it.name}")
                        appendLine("   Override only if content evidence strongly contradicts this.")
                    }
                }
            }

            ImpliedTimeline.HISTORICAL -> {
                appendLine("1. This claim references a historical event.")
                appendLine("   Source dates are expected to be old — this is NOT a zombie signal.")
                appendLine("   Focus on whether sources describe the SAME historical event.")
                appendLine()
                appendLine("2. DATE ACCURACY RULE:")
                appendLine("   If the claim states a specific date (e.g., 'in 2019') but sources")
                appendLine("   indicate a different date, flag this as MISLEADING with TEMPORAL context.")
            }

            ImpliedTimeline.TIMELESS -> {
                appendLine("1. This claim makes no specific temporal reference.")
                appendLine("   Check source dates to see if the claim has become outdated.")
                appendLine("   If sources are from 3+ years ago and the topic may have changed,")
                appendLine("   note this in missing_context as TEMPORAL.")
            }

            else -> {
                appendLine("1. No temporal signals detected in the claim.")
                appendLine("   Note source publication dates in your reasoning.")
            }
        }
    }
}
```

### Inject into Actor Prompt Builder

```kotlin
// In buildActorPrompt() — insert temporal context before SOURCES section

fun buildActorPrompt(
    claim          : String,
    classified     : ClassifiedClaim,
    sourceContext  : String,
    temporalContext: String  // NEW parameter
): String = """
You are a fact-checking analyst. Your job is to draft a preliminary fact-check
of the following claim based solely on the provided sources.

CLAIM: "$claim"
CLAIM TYPE: ${classified.type.name}

$temporalContext

SOURCES:
$sourceContext

${CHAIN_OF_THOUGHT_INSTRUCTION}
${COT_JSON_SCHEMA}
""".trimIndent()
```

---

## Phase 2.5 — Post-LLM Temporal Verdict Override

If the LLM returns a non-MISLEADING verdict despite a confirmed strong temporal mismatch, apply a programmatic override:

```kotlin
// In GeneralFactCheckPipeline — after Actor-Critic analysis

fun applyTemporalOverride(
    llmResult      : CorvusCheckResult.GeneralResult,
    mismatchReport : TemporalMismatchReport,
    profile        : TemporalClaimProfile
): CorvusCheckResult.GeneralResult {

    // Only override when: strong mismatch detected + claim implies current/recent + LLM missed it
    val shouldOverride = mismatchReport.hasSignificantMismatch &&
        mismatchReport.suggestedVerdict == Verdict.MISLEADING &&
        profile.temporalUrgency == TemporalUrgency.HIGH &&
        llmResult.verdict != Verdict.MISLEADING &&
        llmResult.verdict != Verdict.UNVERIFIABLE

    if (!shouldOverride) return llmResult

    val oldestSourceAge = mismatchReport.oldestSourceAge ?: 0
    val overrideNote    = "TEMPORAL OVERRIDE: Sources are ${oldestSourceAge} days old. " +
        "Claim implies current event. Verdict adjusted from ${llmResult.verdict.name} to MISLEADING."

    return llmResult.copy(
        verdict      = Verdict.MISLEADING,
        confidence   = (llmResult.confidence * 0.75f).coerceAtLeast(0.3f),
        missingContext = llmResult.missingContext ?: MissingContextInfo(
            content     = "The sources describing this event are ${oldestSourceAge / 30} months old. " +
                "The claim implies this is a current event, but the evidence dates to an earlier period.",
            contextType = ContextType.TEMPORAL
        ),
        correctionsLog = (llmResult.correctionsLog ?: emptyList()) + listOf(overrideNote)
    )
}
```

---

## Phase 2.6 — UI: Source Date Display

Publication dates are now shown prominently on every source card:

```kotlin
// In SourceCard — updated date display with confidence indicator

@Composable
fun SourceDateDisplay(publicationDate: PublicationDate?) {
    if (publicationDate == null) {
        // No date available — show explicit warning
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(
                CorvusIcons.CalendarOff,
                modifier = Modifier.size(10.dp),
                tint     = CorvusTheme.colors.textTertiary
            )
            Text(
                "Date unknown",
                style     = CorvusTheme.typography.caption,
                color     = CorvusTheme.colors.textTertiary,
                fontStyle = FontStyle.Italic,
                fontFamily = IbmPlexMono
            )
        }
        return
    }

    val dateColor = when (publicationDate.confidence) {
        DateConfidence.EXACT       -> CorvusTheme.colors.textSecondary
        DateConfidence.MONTH_YEAR  -> CorvusTheme.colors.textSecondary
        DateConfidence.YEAR_ONLY   -> CorvusTheme.colors.textTertiary
        DateConfidence.ESTIMATED   -> CorvusTheme.colors.textTertiary
        DateConfidence.UNKNOWN     -> CorvusTheme.colors.textTertiary
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Icon(
            CorvusIcons.Calendar,
            modifier = Modifier.size(10.dp),
            tint     = dateColor
        )
        Text(
            publicationDate.formattedDisplay,
            style      = CorvusTheme.typography.caption,
            color      = dateColor,
            fontFamily = IbmPlexMono
        )
        // Show confidence qualifier for non-exact dates
        if (publicationDate.confidence == DateConfidence.ESTIMATED ||
            publicationDate.confidence == DateConfidence.YEAR_ONLY) {
            Text(
                "(estimated)",
                style      = CorvusTheme.typography.caption,
                color      = CorvusTheme.colors.textTertiary,
                fontStyle  = FontStyle.Italic
            )
        }
    }
}
```

### Zombie Claim Warning Banner

When temporal mismatch is detected, show a banner above the verdict card:

```kotlin
@Composable
fun ZombieClaimWarningBanner(mismatchReport: TemporalMismatchReport) {
    if (!mismatchReport.hasSignificantMismatch) return

    val oldestAge = mismatchReport.oldestSourceAge ?: return

    Card(
        border = BorderStroke(1.5.dp, CorvusTheme.colors.verdictMisleading),
        shape  = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = CorvusTheme.colors.verdictMisleading.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier              = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment     = Alignment.Top
        ) {
            Icon(
                CorvusIcons.Clock,
                modifier = Modifier.size(16.dp).padding(top = 2.dp),
                tint     = CorvusTheme.colors.verdictMisleading
            )
            Column {
                Text(
                    "TEMPORAL MISMATCH DETECTED",
                    style      = CorvusTheme.typography.labelSmall,
                    color      = CorvusTheme.colors.verdictMisleading,
                    fontFamily = IbmPlexMono,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "This claim implies a current event, but retrieved sources are " +
                    "${oldestAge / 30} month(s) old. This may be a recycled claim " +
                    "presented as recent news.",
                    style = CorvusTheme.typography.caption,
                    color = CorvusTheme.colors.textSecondary
                )
                if (mismatchReport.sourcesWithoutDates > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${mismatchReport.sourcesWithoutDates} source(s) have no publication date.",
                        style     = CorvusTheme.typography.caption,
                        color     = CorvusTheme.colors.textTertiary,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}
```

---

## Feature 2 Tasks

- [ ] Define `PublicationDate` data class with `DateConfidence` enum
- [ ] Implement `PublicationDateExtractor` — ISO 8601, article formats, month-year, year-only
- [ ] Implement `extractFromContent()` — date from article body first 500 chars
- [ ] Update `TavilyClient.toCorvusSource()` — extract date from `publishedDate` field and content
- [ ] Update `RssCacheEntity.toCorvusSource()` — extract date from epoch timestamp
- [ ] Update `GoogleFactCheckClient.toCorvusSource()` — extract date from review date field
- [ ] Update `BernamaRssClient` and all RSS clients — date always extracted
- [ ] Update `PubMedClient.toCorvusSource()` — date from publication year
- [ ] Implement `TemporalClaimProfile` and `ImpliedTimeline` enum
- [ ] Implement `TemporalClaimAnalyser.analyze()` — current/recent/historical/timeless detection
- [ ] Implement `TemporalMismatchReport` and `SourceMismatch` data classes
- [ ] Implement `SourceTemporalMismatchDetector.detect()`
- [ ] Implement `TemporalPromptInjector.buildTemporalContext()`
- [ ] Implement `buildTemporalCheckInstruction()` for all 4 implied timeline types
- [ ] Update `buildActorPrompt()` — inject `temporalContext` parameter
- [ ] Implement `applyTemporalOverride()` in `GeneralFactCheckPipeline`
- [ ] Add `SourceDateDisplay` composable to `SourceCard`
- [ ] Implement `ZombieClaimWarningBanner` composable
- [ ] Wire banner into `ResultScreen` LazyColumn before `VerdictCard`
- [ ] Update `CorvusHistoryEntity` — serialise `publicationDate` as JSON per source
- [ ] Unit test: `PublicationDateExtractor` — ISO 8601, article formats, year-only, null
- [ ] Unit test: `extractFromContent()` — date in first 500 chars vs buried in article
- [ ] Unit test: `TemporalClaimAnalyser` — current/recent/historical/timeless BM and EN
- [ ] Unit test: `SourceTemporalMismatchDetector` — strong mismatch, weak mismatch, no mismatch
- [ ] Unit test: `applyTemporalOverride()` — override applied, override skipped
- [ ] Integration test: 2018 protest video presented as current → MISLEADING + TEMPORAL context
- [ ] Integration test: historical claim with old sources → no override applied
- [ ] Integration test: current claim with recent sources → no mismatch banner
- [ ] UI test: `SourceDateDisplay` renders all 5 confidence states correctly
- [ ] UI test: `ZombieClaimWarningBanner` renders only when `hasSignificantMismatch == true`

**Estimated duration: 7 days**

---

---

## Combined Architecture — Both Fixes in Pipeline

```
Raw claim
    ↓
[Classifier] → ClassifiedClaim

[TemporalClaimAnalyser] → TemporalClaimProfile (Fix 2 — fast, local)
    ↓
[QueryRewriterUseCase] → RewrittenQuery (Fix 1 — Groq, 200 tokens, 3s timeout)
    ↓
[MultiQueryRetriever] → AggregatedSourceSet (Fix 1 — parallel, deduped, ranked)
    │   Uses: Tavily + RSS cache + Google CS
    │   Sources include: publicationDate (Fix 2 — extracted at ingest)
    ↓
[SourceTemporalMismatchDetector] → TemporalMismatchReport (Fix 2 — deterministic)
    ↓
[TemporalPromptInjector] → temporalContext string (Fix 2 — injected into actor prompt)
    ↓
[ActorCriticPipeline] → CorvusCheckResult draft (Plan VII)
    │   Actor receives: rewrittenQuery.coreQuestion, sourceContext, temporalContext
    │   Critic audits: temporal mismatch, citation accuracy, verdict correctness
    ↓
[applyTemporalOverride()] → Verdict adjusted if strong mismatch + LLM missed it (Fix 2)
    ↓
[AlgorithmicGroundingVerifier] → Citation stripped if fabricated (Plan VII)
    ↓
[RagVerifier] → Keyword/numeric confidence scoring (Plan VI)
    ↓
[KgEnricher] parallel → EntityContext (Plan V)
    ↓
Final CorvusCheckResult
    │
    ├── retrievalMetadata  (Fix 1 — shown in Methodology Card)
    ├── temporalMismatch   (Fix 2 — drives ZombieClaimWarningBanner)
    └── publicationDates   (Fix 2 — shown on every SourceCard)
```

---

## Combined Roadmap

| Fix | Feature | Duration | Build After |
|---|---|---|---|
| 2a | `PublicationDate` model + extractor | 2 days | None — do first |
| 2b | `TemporalClaimAnalyser` + `MismatchDetector` | 2 days | 2a |
| 1a | `QueryRewriterUseCase` | 2 days | None — parallel with 2a |
| 1b | `MultiQueryRetriever` + dedup + rank | 3 days | 1a |
| 2c | `TemporalPromptInjector` + prompt injection | 1 day | 2b, after Plan VII prompts finalised |
| 2d | `applyTemporalOverride()` + UI components | 2 days | 2c |
| **Total** | | **~12 days** | 2a and 1a can start in parallel |

---

## New Dependencies

```kotlin
// java.time — already available in minSdk 26+
// No new external libraries required
// StringSimilarity from Plan VII reused in TemporalMismatchDetector
```

---

## New API Keys

None. Query rewriting uses existing Groq key (same as Actor in Plan VII). Multi-query retrieval uses existing Tavily + Google CS keys.

**Quota impact — Fix 1:** 2-3 Tavily queries per check instead of 1. At 1,000 free queries/month: effectively reduces free checks to ~330-500/month. Mitigated by RSS cache handling many queries locally before hitting Tavily.

---

## Definition of Done

**Fix 1 — Query Rewriting**
- Biased claim ("evil mayor stole funds") → rewritten queries neutral and keyword-dense
- BM claim includes "Malaysia" in at least one query
- Query rewriter timeout (3s) fails gracefully to raw claim as single query
- Multi-query retrieval runs all queries in parallel via coroutines
- Deduplication strips same article from multiple queries
- Sources ranked: credibility tier > keyword relevance > recency
- `retrievalMetadata` populated on all successful checks
- Methodology Card shows rewritten queries and dedup stats
- Performance: query rewriting adds ≤ 1.5 seconds total

**Fix 2 — Temporal Anchoring**
- All Tavily sources have `publicationDate` extracted (EXACT or MONTH_YEAR confidence)
- RSS sources always have `publicationDate` from `publishedAt` epoch timestamp
- Sources with no extractable date show "Date unknown" on SourceCard
- Current date injected into Actor prompt as absolute anchor
- 2018 protest video presented as "current" → `MISLEADING` + `TEMPORAL` missing context
- Strong mismatch (>60% of sources >6 months old, current-timeline claim) triggers `ZombieClaimWarningBanner`
- `applyTemporalOverride()` fires when LLM misses strong mismatch
- Historical claim with old sources → no mismatch detected, no banner, no override
- All 4 `DateConfidence` states render correctly on `SourceDateDisplay`
- `(estimated)` qualifier shows for ESTIMATED and YEAR_ONLY confidence dates

---

---

# Fix 3 — Pipeline Token Usage Tracking

## Why This Matters

Corvus runs multiple LLM calls per check — Query Rewriter, Actor, Critic, Claim Classifier, Entity Extractor, Plausibility Enricher, and optionally the Compound Detector. Each call has a cost in tokens. Free tier limits are defined in tokens per minute (TPM) and tokens per day (TPD), not in API calls.

Without token tracking:
- No visibility into which pipeline step is the most expensive
- No way to know how close Corvus is to hitting free tier limits
- No ability to optimise prompts with real data
- No honest disclosure to the user about what each analysis consumed

With token tracking:
- Every check has a complete token receipt — input tokens, output tokens, total, per step
- Running totals stored in Room — daily/weekly/monthly usage visible in Settings
- Free tier headroom shown in the provider health panel
- Power users can see the cost of each analysis in the Methodology Card

---

## Token Counting Strategy

Different providers return token counts differently. Some return them in the API response body, some only in headers. Some free models do not return counts at all. Corvus uses a two-tier strategy:

**Tier 1 — Exact counts from API response** (preferred)
Used when the provider returns `usage.prompt_tokens` and `usage.completion_tokens` in the response JSON. Groq, Gemini, and OpenRouter all support this.

**Tier 2 — Estimated counts** (fallback)
When exact counts are unavailable, estimate using the standard approximation:
`tokens ≈ characters / 4`

This is accurate to within ~10% for English text and ~15% for BM/mixed text. The UI clearly labels estimated counts to distinguish them from exact counts.

---

## Data Models

```kotlin
// domain/model/TokenUsage.kt

data class TokenUsage(
    val promptTokens     : Int,
    val completionTokens : Int,
    val totalTokens      : Int,
    val isEstimated      : Boolean,      // true = character-divided estimate
    val provider         : LlmProvider,
    val model            : String,
    val pipelineStep     : TokenPipelineStep
)

enum class TokenPipelineStep(val displayLabel: String) {
    CLAIM_CLASSIFIER      ("Claim Classifier"),
    QUERY_REWRITER        ("Query Rewriter"),
    COMPOUND_DETECTOR     ("Compound Detector"),
    VIRAL_DETECTOR        ("Viral Detector"),
    ENTITY_EXTRACTOR      ("Entity Extractor"),
    ACTOR_ANALYSIS        ("Actor Analysis"),
    CRITIC_REVIEW         ("Critic Review"),
    PLAUSIBILITY_ENRICHER ("Plausibility Enricher"),
    KG_ENRICHER           ("Entity Context"),
    TEMPORAL_ANALYSER     ("Temporal Analyser")
}

// Aggregate for one complete fact-check
data class CheckTokenReport(
    val checkId          : String,
    val claimPreview     : String,       // First 60 chars of claim
    val steps            : List<TokenUsage>,
    val totalPromptTokens: Int,
    val totalOutputTokens: Int,
    val totalTokens      : Int,
    val hasEstimates     : Boolean,      // true if any step used estimation
    val checkedAt        : Long = System.currentTimeMillis()
) {
    companion object {
        fun from(checkId: String, claim: String, steps: List<TokenUsage>) = CheckTokenReport(
            checkId           = checkId,
            claimPreview      = claim.take(60).let { if (claim.length > 60) "$it…" else it },
            steps             = steps,
            totalPromptTokens = steps.sumOf { it.promptTokens },
            totalOutputTokens = steps.sumOf { it.completionTokens },
            totalTokens       = steps.sumOf { it.totalTokens },
            hasEstimates      = steps.any { it.isEstimated }
        )
    }
}

// Daily/monthly rollup for Settings dashboard
data class TokenUsageSummary(
    val periodLabel      : String,       // "Today", "This Week", "This Month"
    val totalTokens      : Int,
    val totalChecks      : Int,
    val avgTokensPerCheck: Int,
    val mostExpensiveStep: TokenPipelineStep?,
    val byProvider       : Map<LlmProvider, Int>   // tokens per provider
)
```

---

## Phase 3.1 — Token Counter Utility

```kotlin
// domain/util/TokenCounter.kt

object TokenCounter {

    // Estimation fallback — ~4 chars per token (standard approximation)
    // Slightly more conservative for BM/Malay mixed text — use 3.5
    private const val CHARS_PER_TOKEN_EN  = 4.0
    private const val CHARS_PER_TOKEN_MY  = 3.5

    fun estimate(text: String, language: ClaimLanguage = ClaimLanguage.ENGLISH): Int {
        val charsPerToken = when (language) {
            ClaimLanguage.BAHASA_MALAYSIA -> CHARS_PER_TOKEN_MY
            ClaimLanguage.MIXED           -> (CHARS_PER_TOKEN_EN + CHARS_PER_TOKEN_MY) / 2
            else                          -> CHARS_PER_TOKEN_EN
        }
        return (text.length / charsPerToken).toInt().coerceAtLeast(1)
    }

    // Build TokenUsage from exact API response values
    fun fromApiResponse(
        promptTokens     : Int,
        completionTokens : Int,
        provider         : LlmProvider,
        model            : String,
        step             : TokenPipelineStep
    ) = TokenUsage(
        promptTokens     = promptTokens,
        completionTokens = completionTokens,
        totalTokens      = promptTokens + completionTokens,
        isEstimated      = false,
        provider         = provider,
        model            = model,
        pipelineStep     = step
    )

    // Build TokenUsage from prompt + response text when API count unavailable
    fun fromText(
        promptText       : String,
        responseText     : String,
        provider         : LlmProvider,
        model            : String,
        step             : TokenPipelineStep,
        language         : ClaimLanguage = ClaimLanguage.ENGLISH
    ) = TokenUsage(
        promptTokens     = estimate(promptText, language),
        completionTokens = estimate(responseText, language),
        totalTokens      = estimate(promptText, language) + estimate(responseText, language),
        isEstimated      = true,
        provider         = provider,
        model            = model,
        pipelineStep     = step
    )
}
```

---

## Phase 3.2 — LLM Client Response Model Update

Each LLM client needs to return token usage alongside the response text. Update the base response model:

```kotlin
// data/remote/llm/LlmResponse.kt

data class LlmResponse(
    val content          : String,
    val promptTokens     : Int?,         // null if provider doesn't return usage
    val completionTokens : Int?,
    val model            : String,
    val provider         : LlmProvider
) {
    // Build a TokenUsage from this response
    fun toTokenUsage(
        step        : TokenPipelineStep,
        promptText  : String,            // Needed for estimation fallback
        language    : ClaimLanguage = ClaimLanguage.ENGLISH
    ): TokenUsage {
        return if (promptTokens != null && completionTokens != null) {
            TokenCounter.fromApiResponse(
                promptTokens     = promptTokens,
                completionTokens = completionTokens,
                provider         = provider,
                model            = model,
                step             = step
            )
        } else {
            // Fallback to estimation
            TokenCounter.fromText(
                promptText   = promptText,
                responseText = content,
                provider     = provider,
                model        = model,
                step         = step,
                language     = language
            )
        }
    }
}
```

### Groq Client — Token Extraction

Groq returns usage in the standard OpenAI format:

```kotlin
// In GroqClient.complete() — parse usage from response

suspend fun complete(
    prompt    : String,
    model     : String,
    maxTokens : Int
): LlmResponse {
    val response = httpClient.post("https://api.groq.com/openai/v1/chat/completions") {
        setBody(GroqRequest(
            model     = model,
            messages  = listOf(GroqMessage("user", prompt)),
            maxTokens = maxTokens
        ))
    }.body<GroqResponse>()

    return LlmResponse(
        content          = response.choices.first().message.content,
        promptTokens     = response.usage?.promptTokens,
        completionTokens = response.usage?.completionTokens,
        model            = model,
        provider         = LlmProvider.GROQ
    )
}

@Serializable
data class GroqResponse(
    val choices : List<GroqChoice>,
    val usage   : GroqUsage? = null
)

@Serializable
data class GroqUsage(
    @SerialName("prompt_tokens")     val promptTokens    : Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens")      val totalTokens     : Int
)
```

### Gemini Client — Token Extraction

```kotlin
@Serializable
data class GeminiResponse(
    val candidates    : List<GeminiCandidate>,
    val usageMetadata : GeminiUsageMetadata? = null
)

@Serializable
data class GeminiUsageMetadata(
    @SerialName("promptTokenCount")     val promptTokenCount    : Int = 0,
    @SerialName("candidatesTokenCount") val candidatesTokenCount: Int = 0,
    @SerialName("totalTokenCount")      val totalTokenCount     : Int = 0
)

// In complete():
return LlmResponse(
    content          = response.candidates.first().content.parts.first().text,
    promptTokens     = response.usageMetadata?.promptTokenCount,
    completionTokens = response.usageMetadata?.candidatesTokenCount,
    model            = "gemini-2.0-flash",
    provider         = LlmProvider.GEMINI
)
```

### Cerebras & OpenRouter

Both use OpenAI-compatible format — same `GroqUsage` model applies. Reuse the same parsing.

---

## Phase 3.3 — Token Collector

A single accumulator passed through the pipeline, collecting `TokenUsage` from each step:

```kotlin
// domain/usecase/TokenCollector.kt

class TokenCollector {
    private val _usages = mutableListOf<TokenUsage>()
    val usages: List<TokenUsage> get() = _usages.toList()

    fun record(usage: TokenUsage) {
        _usages.add(usage)
    }

    fun buildReport(checkId: String, claim: String): CheckTokenReport =
        CheckTokenReport.from(checkId, claim, _usages)

    val totalTokens  : Int     get() = _usages.sumOf { it.totalTokens }
    val totalPrompt  : Int     get() = _usages.sumOf { it.promptTokens }
    val totalOutput  : Int     get() = _usages.sumOf { it.completionTokens }
    val hasEstimates : Boolean get() = _usages.any { it.isEstimated }
    val stepCount    : Int     get() = _usages.size
}
```

### Pipeline Threading

```kotlin
// In CorvusFactCheckUseCase.check()

suspend fun check(raw: String): CorvusCheckResult = coroutineScope {
    val tokenCollector = TokenCollector()           // Fresh per check
    val checkId        = UUID.randomUUID().toString()

    val classified     = claimClassifier.classify(raw, tokenCollector)
    val rewrittenQuery = queryRewriter.rewrite(classified, tokenCollector)
    val sourceSet      = multiQueryRetriever.retrieve(rewrittenQuery, classified)

    val mainDeferred = async {
        actorCriticPipeline.analyze(
            claim          = rewrittenQuery.coreQuestion,
            classified     = classified,
            sources        = sourceSet.sources,
            tokenCollector = tokenCollector
        )
    }
    val kgDeferred = async {
        runCatching { kgEnricher.enrich(raw, classified, tokenCollector) }.getOrNull()
    }

    val mainResult    = mainDeferred.await()
    val entityContext = kgDeferred.await()

    val tokenReport   = tokenCollector.buildReport(checkId, raw)
    tokenUsageRepository.saveReport(tokenReport)

    mainResult.copy(entityContext = entityContext, tokenReport = tokenReport)
}
```

Each pipeline stage records its own usage:

```kotlin
// Example — QueryRewriterUseCase
val response = groqClient.complete(prompt, model, maxTokens = 200)
tokenCollector.record(
    response.toTokenUsage(
        step     = TokenPipelineStep.QUERY_REWRITER,
        promptText = prompt,
        language   = classified.language
    )
)
```

Actor and Critic each record independently using `ACTOR_ANALYSIS` and `CRITIC_REVIEW` step labels.

---

## Phase 3.4 — Room Persistence

```kotlin
@Entity(tableName = "token_reports")
data class TokenReportEntity(
    @PrimaryKey
    val checkId           : String,
    val claimPreview      : String,
    val totalPromptTokens : Int,
    val totalOutputTokens : Int,
    val totalTokens       : Int,
    val stepsJson         : String,     // JSON List<TokenUsage>
    val hasEstimates      : Boolean,
    val checkedAt         : Long
)

@Dao
interface TokenReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: TokenReportEntity)

    @Query("SELECT * FROM token_reports ORDER BY checkedAt DESC")
    fun getAllReports(): Flow<List<TokenReportEntity>>

    @Query("SELECT SUM(totalTokens) FROM token_reports WHERE checkedAt >= :fromMs")
    suspend fun totalTokensSince(fromMs: Long): Int?

    @Query("SELECT COUNT(*) FROM token_reports WHERE checkedAt >= :fromMs")
    suspend fun checkCountSince(fromMs: Long): Int?

    @Query("SELECT stepsJson FROM token_reports WHERE checkedAt >= :fromMs")
    suspend fun allStepsJsonSince(fromMs: Long): List<String>

    @Query("DELETE FROM token_reports WHERE checkedAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)

    @Query("SELECT * FROM token_reports ORDER BY checkedAt DESC LIMIT :limit")
    suspend fun getRecentReports(limit: Int): List<TokenReportEntity>
}
```

### Token Usage Repository

```kotlin
class TokenUsageRepository @Inject constructor(
    private val dao  : TokenReportDao,
    private val json : Json
) {
    suspend fun saveReport(report: CheckTokenReport) {
        dao.insert(TokenReportEntity(
            checkId           = report.checkId,
            claimPreview      = report.claimPreview,
            totalPromptTokens = report.totalPromptTokens,
            totalOutputTokens = report.totalOutputTokens,
            totalTokens       = report.totalTokens,
            stepsJson         = json.encodeToString(report.steps),
            hasEstimates      = report.hasEstimates,
            checkedAt         = report.checkedAt
        ))
    }

    suspend fun getSummary(period: TokenPeriod): TokenUsageSummary {
        val fromMs      = period.startMs()
        val totalTokens = dao.totalTokensSince(fromMs) ?: 0
        val totalChecks = dao.checkCountSince(fromMs) ?: 0

        val allSteps = dao.allStepsJsonSince(fromMs)
            .flatMap { runCatching { json.decodeFromString<List<TokenUsage>>(it) }.getOrDefault(emptyList()) }

        val byProvider = allSteps
            .groupBy { it.provider }
            .mapValues { (_, u) -> u.sumOf { it.totalTokens } }

        val mostExpensiveStep = allSteps
            .groupBy { it.pipelineStep }
            .mapValues { (_, u) -> u.sumOf { it.totalTokens } }
            .maxByOrNull { it.value }?.key

        return TokenUsageSummary(
            periodLabel       = period.label,
            totalTokens       = totalTokens,
            totalChecks       = totalChecks,
            avgTokensPerCheck = if (totalChecks > 0) totalTokens / totalChecks else 0,
            mostExpensiveStep = mostExpensiveStep,
            byProvider        = byProvider
        )
    }

    fun getRecentReports(limit: Int = 20): Flow<List<CheckTokenReport>> =
        dao.getAllReports().map { entities ->
            entities.take(limit).map { e ->
                CheckTokenReport(
                    checkId           = e.checkId,
                    claimPreview      = e.claimPreview,
                    steps             = runCatching { json.decodeFromString<List<TokenUsage>>(e.stepsJson) }.getOrDefault(emptyList()),
                    totalPromptTokens = e.totalPromptTokens,
                    totalOutputTokens = e.totalOutputTokens,
                    totalTokens       = e.totalTokens,
                    hasEstimates      = e.hasEstimates,
                    checkedAt         = e.checkedAt
                )
            }
        }
}

enum class TokenPeriod(val label: String) {
    TODAY("Today"), THIS_WEEK("This Week"), THIS_MONTH("This Month"), ALL_TIME("All Time");

    fun startMs(): Long {
        val now = LocalDate.now()
        return when (this) {
            TODAY      -> now.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            THIS_WEEK  -> now.with(DayOfWeek.MONDAY).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            THIS_MONTH -> now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            ALL_TIME   -> 0L
        }
    }
}
```

---

## Phase 3.5 — Free Tier Headroom Calculator

```kotlin
// domain/util/FreeTierCalculator.kt

object FreeTierCalculator {

    // Conservative daily limits — safe estimates for free tiers
    private val DAILY_TOKEN_LIMITS = mapOf(
        LlmProvider.GROQ       to 500_000,
        LlmProvider.GEMINI     to 1_000_000,
        LlmProvider.CEREBRAS   to 300_000,
        LlmProvider.OPENROUTER to 200_000
    )

    private const val AVG_TOKENS_PER_CHECK = 2_500

    data class ProviderHeadroom(
        val provider            : LlmProvider,
        val dailyLimit          : Int,
        val tokensUsedToday     : Int,
        val tokensRemaining     : Int,
        val percentUsed         : Float,
        val estimatedChecksLeft : Int,
        val status              : HeadroomStatus
    )

    enum class HeadroomStatus { HEALTHY, MODERATE, LOW, CRITICAL }

    fun calculateHeadroom(provider: LlmProvider, tokensUsedToday: Int): ProviderHeadroom {
        val dailyLimit  = DAILY_TOKEN_LIMITS[provider] ?: 100_000
        val remaining   = (dailyLimit - tokensUsedToday).coerceAtLeast(0)
        val percentUsed = tokensUsedToday.toFloat() / dailyLimit

        return ProviderHeadroom(
            provider            = provider,
            dailyLimit          = dailyLimit,
            tokensUsedToday     = tokensUsedToday,
            tokensRemaining     = remaining,
            percentUsed         = percentUsed,
            estimatedChecksLeft = remaining / AVG_TOKENS_PER_CHECK,
            status              = when {
                percentUsed < 0.50f -> HeadroomStatus.HEALTHY
                percentUsed < 0.80f -> HeadroomStatus.MODERATE
                percentUsed < 0.95f -> HeadroomStatus.LOW
                else                -> HeadroomStatus.CRITICAL
            }
        )
    }
}
```

---

## Phase 3.6 — UI Components

### Token Receipt in Methodology Card

```kotlin
@Composable
fun TokenReceiptSection(report: CheckTokenReport) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        // Header — always visible, shows total
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("TOKEN USAGE", style = CorvusTheme.typography.labelSmall,
                    color = CorvusTheme.colors.textSecondary, fontFamily = IbmPlexMono)
                if (report.hasEstimates) EstimatedBadge()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${report.totalTokens.formatTokenCount()} tokens",
                    style = CorvusTheme.typography.labelSmall,
                    color = CorvusTheme.colors.accent, fontFamily = IbmPlexMono)
                Icon(if (expanded) CorvusIcons.ChevronUp else CorvusIcons.ChevronDown,
                    modifier = Modifier.size(14.dp), tint = CorvusTheme.colors.textTertiary)
            }
        }

        // Expanded: per-step breakdown
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {

                // Input / Output / Total summary row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TokenSummaryChip("↑ Input",  report.totalPromptTokens,  CorvusTheme.colors.textSecondary)
                    TokenSummaryChip("↓ Output", report.totalOutputTokens, CorvusTheme.colors.textSecondary)
                    TokenSummaryChip("Total",    report.totalTokens,        CorvusTheme.colors.accent)
                }

                Spacer(Modifier.height(8.dp))
                Text("BREAKDOWN BY STEP", style = CorvusTheme.typography.caption,
                    color = CorvusTheme.colors.textTertiary, fontFamily = IbmPlexMono)
                Spacer(Modifier.height(4.dp))

                report.steps.forEach { step -> TokenStepRow(step, report.totalTokens) }

                if (report.hasEstimates) {
                    Spacer(Modifier.height(6.dp))
                    Text("* Estimated — provider did not return exact counts",
                        style = CorvusTheme.typography.caption,
                        color = CorvusTheme.colors.textTertiary,
                        fontStyle = FontStyle.Italic, fontFamily = IbmPlexMono)
                }
            }
        }
    }
}

@Composable
fun TokenStepRow(usage: TokenUsage, totalTokens: Int) {
    val proportion = if (totalTokens > 0) usage.totalTokens.toFloat() / totalTokens else 0f

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(usage.pipelineStep.displayLabel,
            modifier = Modifier.width(140.dp),
            style = CorvusTheme.typography.caption, color = CorvusTheme.colors.textSecondary,
            fontFamily = IbmPlexMono, maxLines = 1, overflow = TextOverflow.Ellipsis)

        Box(modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
            .background(CorvusTheme.colors.border)) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(proportion)
                .background(if (usage.isEstimated)
                    CorvusTheme.colors.accent.copy(alpha = 0.5f)
                else CorvusTheme.colors.accent))
        }

        Text(
            text = "${usage.totalTokens.formatTokenCount()}${if (usage.isEstimated) "*" else ""}",
            modifier = Modifier.width(52.dp),
            style = CorvusTheme.typography.caption, color = CorvusTheme.colors.textPrimary,
            fontFamily = IbmPlexMono, textAlign = TextAlign.End
        )
    }
}

@Composable
fun EstimatedBadge() {
    Surface(color = CorvusTheme.colors.verdictMisleading.copy(alpha = 0.12f),
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, CorvusTheme.colors.verdictMisleading.copy(alpha = 0.3f))) {
        Text("EST", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            style = CorvusTheme.typography.caption,
            color = CorvusTheme.colors.verdictMisleading, fontFamily = IbmPlexMono)
    }
}

fun Int.formatTokenCount(): String = when {
    this >= 1_000 -> "${this / 1000}.${(this % 1000) / 100}k"
    else          -> this.toString()
}
```

### Token Usage Dashboard in Settings

```kotlin
@Composable
fun TokenUsageDashboard(
    todaySummary : TokenUsageSummary,
    weekSummary  : TokenUsageSummary,
    monthSummary : TokenUsageSummary,
    headrooms    : List<FreeTierCalculator.ProviderHeadroom>,
    recentReports: List<CheckTokenReport>
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Period summary cards — Today / This Week / This Month
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(todaySummary, weekSummary, monthSummary).forEach { summary ->
                    UsagePeriodCard(summary, modifier = Modifier.weight(1f))
                }
            }
        }

        // Provider headroom cards
        item {
            Text("FREE TIER HEADROOM", style = CorvusTheme.typography.label,
                color = CorvusTheme.colors.textSecondary, fontFamily = IbmPlexMono,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
        items(headrooms, key = { it.provider.name }) { headroom ->
            ProviderHeadroomCard(headroom, modifier = Modifier.padding(horizontal = 16.dp))
        }

        // Recent checks token rows
        item {
            Text("RECENT CHECKS", style = CorvusTheme.typography.label,
                color = CorvusTheme.colors.textSecondary, fontFamily = IbmPlexMono,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
        items(recentReports.take(5), key = { it.checkId }) { report ->
            RecentCheckTokenRow(report, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
fun UsagePeriodCard(summary: TokenUsageSummary, modifier: Modifier = Modifier) {
    Card(modifier = modifier,
        border = BorderStroke(1.dp, CorvusTheme.colors.border),
        shape = RoundedCornerShape(6.dp)) {
        Column(modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(summary.periodLabel.uppercase(), style = CorvusTheme.typography.caption,
                color = CorvusTheme.colors.textTertiary, fontFamily = IbmPlexMono)
            Text(summary.totalTokens.formatTokenCount(),
                style = CorvusTheme.typography.title,
                color = CorvusTheme.colors.accent, fontFamily = IbmPlexMono)
            Text("${summary.totalChecks} checks",
                style = CorvusTheme.typography.caption, color = CorvusTheme.colors.textSecondary)
            summary.mostExpensiveStep?.let { step ->
                Text("↑ ${step.displayLabel}", style = CorvusTheme.typography.caption,
                    color = CorvusTheme.colors.textTertiary, fontFamily = IbmPlexMono)
            }
        }
    }
}

@Composable
fun ProviderHeadroomCard(
    headroom : FreeTierCalculator.ProviderHeadroom,
    modifier : Modifier = Modifier
) {
    val barColor = when (headroom.status) {
        FreeTierCalculator.HeadroomStatus.HEALTHY   -> CorvusTheme.colors.verdictTrue
        FreeTierCalculator.HeadroomStatus.MODERATE  -> CorvusTheme.colors.verdictTrue.copy(alpha = 0.7f)
        FreeTierCalculator.HeadroomStatus.LOW       -> CorvusTheme.colors.verdictMisleading
        FreeTierCalculator.HeadroomStatus.CRITICAL  -> CorvusTheme.colors.verdictFalse
    }
    Card(modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, CorvusTheme.colors.border),
        shape = RoundedCornerShape(6.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(headroom.provider.displayName(),
                    style = CorvusTheme.typography.labelSmall,
                    color = CorvusTheme.colors.textPrimary, fontFamily = IbmPlexMono)
                Text("~${headroom.estimatedChecksLeft} checks left today",
                    style = CorvusTheme.typography.caption, color = barColor)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress   = { headroom.percentUsed },
                modifier   = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color      = barColor,
                trackColor = CorvusTheme.colors.border
            )
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${headroom.tokensUsedToday.formatTokenCount()} used",
                    style = CorvusTheme.typography.caption,
                    color = CorvusTheme.colors.textTertiary, fontFamily = IbmPlexMono)
                Text("${headroom.tokensRemaining.formatTokenCount()} remaining",
                    style = CorvusTheme.typography.caption,
                    color = CorvusTheme.colors.textSecondary, fontFamily = IbmPlexMono)
            }
        }
    }
}

@Composable
fun RecentCheckTokenRow(report: CheckTokenReport, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(report.claimPreview, style = CorvusTheme.typography.bodySmall,
                color = CorvusTheme.colors.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(report.checkedAt.toRelativeTime(),
                style = CorvusTheme.typography.caption,
                color = CorvusTheme.colors.textTertiary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (report.hasEstimates) EstimatedBadge()
            Text(report.totalTokens.formatTokenCount(),
                style = CorvusTheme.typography.labelSmall,
                color = CorvusTheme.colors.accent, fontFamily = IbmPlexMono)
        }
    }
}
```

---

## Phase 3.7 — WorkManager: Report Purge

```kotlin
class TokenReportPurgeWorker(
    context : Context,
    params  : WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val cutoff = System.currentTimeMillis() - 90.days.inWholeMilliseconds
        tokenReportDao.purgeOlderThan(cutoff)
        return Result.success()
    }
}

WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "token_report_purge",
    ExistingPeriodicWorkPolicy.KEEP,
    PeriodicWorkRequestBuilder<TokenReportPurgeWorker>(7, TimeUnit.DAYS).build()
)
```

---

## Feature 3 Tasks

- [ ] Define `TokenUsage` data class with `TokenPipelineStep` enum
- [ ] Define `CheckTokenReport` with `from()` factory
- [ ] Define `TokenUsageSummary` and `TokenPeriod` enum
- [ ] Implement `TokenCounter` — `estimate()`, `fromApiResponse()`, `fromText()`
- [ ] Define `LlmResponse` wrapper with nullable `promptTokens` + `completionTokens`
- [ ] Update `GroqClient.complete()` — parse `usage.prompt_tokens` + `completion_tokens`
- [ ] Update `GeminiClient.complete()` — parse `usageMetadata.promptTokenCount` + `candidatesTokenCount`
- [ ] Update `CerebrasClient.complete()` — OpenAI-format usage
- [ ] Update `OpenRouterClient.complete()` — OpenAI-format usage
- [ ] Implement `LlmResponse.toTokenUsage()` with estimation fallback
- [ ] Implement `TokenCollector` — `record()`, `buildReport()`, summary accessors
- [ ] Update `CorvusFactCheckUseCase.check()` — create fresh `TokenCollector` per check
- [ ] Thread `TokenCollector` through all LLM-calling pipeline stages
- [ ] Update `QueryRewriterUseCase` — record `QUERY_REWRITER` step
- [ ] Update `ClaimClassifierUseCase` — record `CLAIM_CLASSIFIER` step
- [ ] Update `ActorCriticPipeline` — record `ACTOR_ANALYSIS` and `CRITIC_REVIEW` separately
- [ ] Update `EntityExtractorUseCase` — record `ENTITY_EXTRACTOR` step
- [ ] Update `PlausibilityEnricherUseCase` — record `PLAUSIBILITY_ENRICHER` step
- [ ] Update `CompoundDetectorUseCase` — record `COMPOUND_DETECTOR` step
- [ ] Add `tokenReport: CheckTokenReport?` to `CorvusCheckResult.GeneralResult`
- [ ] Add Room migration for `token_reports` table
- [ ] Implement `TokenReportDao` with all queries
- [ ] Implement `TokenUsageRepository` — `saveReport()`, `getSummary()`, `getRecentReports()`
- [ ] Implement `FreeTierCalculator` with `DAILY_TOKEN_LIMITS` and `calculateHeadroom()`
- [ ] Implement `TokenReceiptSection` composable — collapsed summary + expanded breakdown
- [ ] Implement `TokenStepRow` with proportion bar
- [ ] Implement `TokenSummaryChip` composable
- [ ] Implement `EstimatedBadge` composable
- [ ] Implement `Int.formatTokenCount()` extension (K suffix)
- [ ] Implement `TokenUsageDashboard` in Settings
- [ ] Implement `UsagePeriodCard` (Today / Week / Month)
- [ ] Implement `ProviderHeadroomCard` with progress bar + checks-left
- [ ] Implement `RecentCheckTokenRow`
- [ ] Implement `TokenReportPurgeWorker` via WorkManager (weekly, 90-day retention)
- [ ] Wire all new classes into Hilt module
- [ ] Unit test: `TokenCounter.estimate()` — EN, BM, mixed language text
- [ ] Unit test: `LlmResponse.toTokenUsage()` — exact count path vs estimation fallback
- [ ] Unit test: `TokenCollector` — multiple steps, totals summed correctly
- [ ] Unit test: `CheckTokenReport.from()` — preview truncated at 60 chars with ellipsis
- [ ] Unit test: `TokenUsageRepository.getSummary()` — all 4 `TokenPeriod` variants
- [ ] Unit test: `FreeTierCalculator.calculateHeadroom()` — all 4 `HeadroomStatus` thresholds
- [ ] Unit test: `Int.formatTokenCount()` — 999, 1000, 1234, 12345
- [ ] Integration test: full check produces `CheckTokenReport` with all expected step entries
- [ ] Integration test: Actor + Critic appear as separate `TokenPipelineStep` entries
- [ ] Integration test: token report persisted to Room after check completes
- [ ] Integration test: Groq exact counts path — `isEstimated == false`
- [ ] Integration test: provider with no usage response — `isEstimated == true`
- [ ] UI test: `TokenReceiptSection` collapses and expands correctly
- [ ] UI test: `ProviderHeadroomCard` renders correct bar colour per `HeadroomStatus`
- [ ] UI test: `EstimatedBadge` visible when `report.hasEstimates == true`

**Estimated duration: 6 days**

---

---

## Updated Combined Roadmap (All Three Fixes)

| Fix | Feature | Duration | Build After |
|---|---|---|---|
| 2a | `PublicationDate` model + extractor | 2 days | None |
| 1a | `QueryRewriterUseCase` | 2 days | None — parallel with 2a |
| 3a | LLM client `LlmResponse` updates + `TokenCounter` | 3 days | None — parallel with 1a, 2a |
| 1b | `MultiQueryRetriever` + dedup + rank | 3 days | 1a |
| 2b | `TemporalClaimAnalyser` + `MismatchDetector` | 2 days | 2a |
| 3b | `TokenCollector` + pipeline threading | 2 days | 3a + all LLM clients updated |
| 2c | `TemporalPromptInjector` + prompt injection | 1 day | 2b |
| 3c | Room persistence + `TokenUsageRepository` | 2 days | 3b |
| 2d | `applyTemporalOverride()` + zombie UI components | 2 days | 2c |
| 3d | UI — `TokenReceiptSection` + Settings dashboard | 2 days | 3c |
| **Total** | | **~21 days** | 1a, 2a, 3a all start in parallel |

**Fix 3 (token tracking) is the most parallelisable work in this plan.** The LLM client updates (3a) touch individual files independently and can start the moment development begins, alongside the other two fix foundations. Once all clients return `LlmResponse`, threading `TokenCollector` (3b) through the pipeline takes 2 days. UI (3d) is the last piece, built after Room persistence (3c) is confirmed working.

---

## Definition of Done — Fix 3

- Every LLM call in the pipeline records a `TokenUsage` entry via `TokenCollector`
- Actor and Critic recorded as separate `TokenPipelineStep` entries
- Groq, Gemini, Cerebras, OpenRouter all return exact counts where API supports it
- Estimation fallback used and labelled `isEstimated: true` when provider returns no usage
- `CheckTokenReport` attached to every `CorvusCheckResult.GeneralResult`
- Token report persisted to Room immediately after check completes
- Methodology Card collapsed header shows total token count
- Expanded Methodology Card shows per-step proportion bars — estimated steps show dimmed bar
- Estimated counts marked with `*` and footnote in expanded view
- Settings Token Usage Dashboard shows Today / This Week / This Month summary cards
- All 4 provider headroom cards render with correct status colour and checks-remaining
- Recent checks list shows last 5 token reports
- `UsagePeriodCard` shows most expensive step label for that period
- Token reports older than 90 days purged by weekly WorkManager job
- `1234.formatTokenCount()` → `"1.2k"`, `999.formatTokenCount()` → `"999"`
