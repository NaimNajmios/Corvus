# Corvus — Enhancement Implementation Plan
## Expanded Sources, Claim Classification & Quote Verification

> **Scope:** This plan covers enhancements beyond the core MVP and full app plans. It assumes the base pipeline (Google Fact Check → Tavily → Gemini/Groq) is already functional. All additions are zero-cost using free APIs only.

---

## What This Plan Adds

| Enhancement | Description |
|---|---|
| **Claim Classifier** | LLM-based pre-routing — detects claim type before pipeline runs |
| **Quote Verification Pipeline** | Dedicated sub-pipeline for quote attribution and context checks |
| **Expanded Source Layer** | Wikipedia, Wikidata, Hansard, DOSM, GDELT, RSS cache, PubMed |
| **Malaysian RSS Cache** | Local offline cache of MY news headlines — zero API cost |
| **Extended Verdict Types** | `QuoteVerdict` sealed class with 7 quote-specific outcomes |
| **Claim-Type-Aware UI** | Result screen adapts layout based on claim type |

---

## New Domain Models

### Claim Classifier Output

```kotlin
enum class ClaimType {
    QUOTE,          // "X said Y"
    STATISTICAL,    // "Malaysia's GDP grew by 4.2%"
    HISTORICAL,     // "The Baling Talks happened in 1955"
    SCIENTIFIC,     // "Vitamin C prevents COVID"
    CURRENT_EVENT,  // "Floods hit Kelantan last week"
    PERSON_FACT,    // "Anwar Ibrahim is the 10th PM"
    GENERAL         // Catch-all
}

data class ClassifiedClaim(
    val raw: String,
    val type: ClaimType,
    val speaker: String? = null,        // Populated if QUOTE
    val quotedText: String? = null,     // Extracted quote if QUOTE
    val claimedDate: String? = null,    // Date context if present
    val entities: List<String> = emptyList()
)
```

### Extended Verdict Models

```kotlin
// General fact-check verdict (existing — unchanged)
enum class Verdict {
    TRUE, FALSE, MISLEADING, PARTIALLY_TRUE, UNVERIFIABLE, NOT_A_CLAIM
}

// Quote-specific verdict (new)
enum class QuoteVerdict {
    VERIFIED,           // Confirmed verbatim with primary source
    PARAPHRASED,        // Sentiment correct, wording differs
    OUT_OF_CONTEXT,     // Real quote, misleading framing
    MISATTRIBUTED,      // Quote exists but wrong speaker
    FABRICATED,         // No evidence it was ever said
    SATIRE_ORIGIN,      // Originated from satire, reshared as real
    UNVERIFIABLE        // Insufficient sources
}

// Unified result that wraps both
sealed class CorvusCheckResult {
    data class GeneralResult(
        val verdict: Verdict,
        val confidence: Float,
        val explanation: String,
        val keyFacts: List<String>,
        val sources: List<CorvusSource>,
        val claimType: ClaimType
    ) : CorvusCheckResult()

    data class QuoteResult(
        val quoteVerdict: QuoteVerdict,
        val confidence: Float,
        val speaker: String,
        val originalQuote: String?,
        val submittedQuote: String,
        val originalSource: CorvusSource?,
        val originalDate: String?,
        val contextExplanation: String,
        val supportingSources: List<CorvusSource>,
        val isVerbatim: Boolean,
        val contextAccurate: Boolean
    ) : CorvusCheckResult()
}
```

### Expanded Source Model

```kotlin
data class CorvusSource(
    val title: String,
    val url: String,
    val publisher: String?,
    val snippet: String?,
    val publishedDate: String?,
    val isLocalSource: Boolean = false,
    val sourceType: SourceType = SourceType.WEB,
    val credibilityTier: CredibilityTier = CredibilityTier.GENERAL
)

enum class SourceType {
    FACT_CHECK_DB,      // Google Fact Check, Sebenarnya
    OFFICIAL_TRANSCRIPT,// Hansard, PMO, ministry sites
    GOVERNMENT_DATA,    // DOSM, data.gov.my, World Bank
    NEWS_ARCHIVE,       // Bernama, The Star, FMT (RSS cache)
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
```

---

## Architecture Changes

### Updated Use Case Routing

```kotlin
// CorvusFactCheckUseCase.kt
class CorvusFactCheckUseCase @Inject constructor(
    private val classifier: ClaimClassifierUseCase,
    private val generalPipeline: GeneralFactCheckPipeline,
    private val quotePipeline: QuoteVerificationPipeline,
    private val statisticalPipeline: StatisticalFactCheckPipeline,
    private val scientificPipeline: ScientificFactCheckPipeline,
    private val eventPipeline: CurrentEventPipeline
) {
    suspend fun check(raw: String): CorvusCheckResult {
        val classified = classifier.classify(raw)

        return when (classified.type) {
            ClaimType.QUOTE        -> quotePipeline.verify(classified)
            ClaimType.STATISTICAL  -> statisticalPipeline.verify(classified)
            ClaimType.SCIENTIFIC   -> scientificPipeline.verify(classified)
            ClaimType.CURRENT_EVENT -> eventPipeline.verify(classified)
            else                   -> generalPipeline.verify(classified)
        }
    }
}
```

### Full Source Repository Map

```
data/remote/
├── factcheck/
│   ├── GoogleFactCheckClient.kt       // Existing
│   └── SebenarnyaClient.kt            // Existing
├── search/
│   ├── TavilyClient.kt                // Existing
│   └── GoogleCustomSearchClient.kt    // Existing
├── knowledge/
│   ├── WikipediaClient.kt             // NEW
│   ├── WikiquoteClient.kt             // NEW
│   └── WikidataSparqlClient.kt        // NEW
├── transcript/
│   ├── HansardClient.kt               // NEW
│   └── BernamaRssClient.kt            // NEW (part of RSS cache)
├── govdata/
│   ├── DosmClient.kt                  // NEW
│   └── DataGovMyClient.kt             // NEW
├── academic/
│   └── PubMedClient.kt                // NEW
├── news/
│   ├── GdeltClient.kt                 // NEW
│   └── NewsApiClient.kt               // NEW
└── llm/
    ├── GeminiClient.kt                // Existing
    └── GroqClient.kt                  // Existing

data/local/
├── dao/
│   ├── CorvusHistoryDao.kt            // Existing
│   └── RssCacheDao.kt                 // NEW — Malaysian RSS headline cache
└── entity/
    ├── CorvusHistoryEntity.kt         // Existing
    └── RssCacheEntity.kt              // NEW
```

---

## Phase 1 — Claim Classifier

**Goal:** Route every claim to the correct pipeline before any retrieval happens.

### Implementation

```kotlin
class ClaimClassifierUseCase @Inject constructor(
    private val groqClient: GroqClient  // Use Groq — fast, free, lightweight task
) {
    suspend fun classify(raw: String): ClassifiedClaim {
        val prompt = """
            Analyze the following claim and extract structured information.
            
            Claim: "$raw"
            
            Respond ONLY with valid JSON:
            {
              "type": "QUOTE|STATISTICAL|HISTORICAL|SCIENTIFIC|CURRENT_EVENT|PERSON_FACT|GENERAL",
              "speaker": "name if quote claim, else null",
              "quoted_text": "extracted verbatim quote if type is QUOTE, else null",
              "claimed_date": "date context if present, else null",
              "entities": ["list", "of", "key", "named", "entities"]
            }
        """.trimIndent()

        val response = groqClient.complete(prompt, model = "llama-3.3-70b-versatile")
        return parseClassifierResponse(raw, response)
    }
}
```

### Tasks
- [ ] Implement `ClaimClassifierUseCase`
- [ ] Implement `parseClassifierResponse()` with JSON fallback
- [ ] Unit test: 20 sample claims across all 7 types
- [ ] Verify QUOTE extraction correctly pulls speaker + quote text
- [ ] Wire classifier into `CorvusFactCheckUseCase`

---

## Phase 2 — Quote Verification Pipeline

**Goal:** Full 4-layer quote attribution and context verification.

### Pipeline Flow

```
ClassifiedClaim (type = QUOTE)
    ↓
[Layer 1] Wikiquote API lookup for speaker
    → Exact match found? → VERIFIED (high confidence)
    → Similar found? → PARAPHRASED
    → Not found? → Continue
    ↓
[Layer 2] Google Fact Check API scoped to quote claim
    + Sebenarnya.my lookup
    → Debunk article found? → FABRICATED or MISATTRIBUTED
    → Verification article found? → VERIFIED
    → No result? → Continue
    ↓
[Layer 3] Tavily targeted search
    Query 1: '"{quoted_text_fragment}" "{speaker}"'
    Query 2: '"{speaker}" quote fact check'
    → Found in credible primary source (Bernama, The Star, official site)? → VERIFIED
    → Found only on social media / blogs? → Flag as unverified, continue
    → Found debunking? → FABRICATED
    ↓
[Layer 4a] Hansard search (if Malaysian politician)
    → parlimen.gov.my search for speaker + quote keywords
    → Quote found in parliamentary record? → VERIFIED (PRIMARY tier)
    ↓
[Layer 4b] LLM context analysis (Gemini / Groq)
    Given all retrieved sources:
    - Does the quote exist?
    - Is the attribution correct?
    - Is it verbatim or paraphrased?
    - Is the context in which it's shared accurate?
    → Return QuoteVerdict + explanation
```

### Wikiquote Client

```kotlin
class WikiquoteClient @Inject constructor(private val httpClient: HttpClient) {

    // Get all quotes for a person
    suspend fun getQuotes(personName: String): WikiquoteResult {
        val encoded = personName.replace(" ", "_")
        val response = httpClient.get(
            "https://en.wikiquote.org/api/rest_v1/page/summary/$encoded"
        )
        return parseWikiquoteResponse(response)
    }

    // Fuzzy match submitted quote against known quotes
    fun findMatch(submitted: String, known: List<String>): QuoteMatchResult {
        val normalized = submitted.lowercase().trim()
        val exact = known.firstOrNull { it.lowercase().trim() == normalized }
        val partial = known.firstOrNull {
            it.lowercase().contains(normalized.take(40))
        }
        return when {
            exact != null  -> QuoteMatchResult.EXACT(exact)
            partial != null -> QuoteMatchResult.PARTIAL(partial)
            else           -> QuoteMatchResult.NOT_FOUND
        }
    }
}
```

### Hansard Client (Malaysian Parliament)

```kotlin
class HansardClient @Inject constructor(
    private val httpClient: HttpClient,
    private val tavilyClient: TavilyClient
) {
    // parlimen.gov.my does not have a public JSON API
    // Use Tavily scoped to the domain as a proxy
    suspend fun searchHansard(speaker: String, keywords: String): List<CorvusSource> {
        val query = "$speaker $keywords site:parlimen.gov.my"
        return tavilyClient.search(query, maxResults = 3)
            .map { it.copy(sourceType = SourceType.OFFICIAL_TRANSCRIPT,
                           credibilityTier = CredibilityTier.PRIMARY) }
    }
}
```

### Quote Verification LLM Prompt

```
You are verifying whether a quote attributed to a public figure is authentic.

SUBMITTED QUOTE: "{submitted_quote}"
ATTRIBUTED TO: "{speaker}"
CLAIMED CONTEXT: "{claimed_context}"

RETRIEVED SOURCES:
{sources}

Evaluate:
1. Does evidence confirm this quote exists?
2. Is the attribution to {speaker} correct?
3. Is the quote verbatim or significantly paraphrased?
4. Is the context in which this quote is being shared accurate?
5. Any evidence this originated from satire?

Respond ONLY with valid JSON:
{
  "quote_verdict": "VERIFIED|PARAPHRASED|OUT_OF_CONTEXT|MISATTRIBUTED|FABRICATED|SATIRE_ORIGIN|UNVERIFIABLE",
  "confidence": 0.0,
  "is_verbatim": true/false,
  "attribution_correct": true/false,
  "context_accurate": true/false,
  "original_source_index": 0,
  "original_date": "date or null",
  "explanation": "3-4 sentence explanation"
}
```

### Tasks
- [ ] Implement `WikiquoteClient` with fuzzy matching
- [ ] Implement `HansardClient` (Tavily-proxied)
- [ ] Implement `QuoteVerificationPipeline`
- [ ] Implement quote-specific LLM prompt and response parser
- [ ] Implement `QuoteVerdict` → UI mapping
- [ ] Unit test: 10 real Malaysian political quotes (verified + fabricated)
- [ ] Unit test: 5 misattributed quotes (common viral ones)

---

## Phase 3 — Knowledge Base Sources

**Goal:** Add Wikipedia, Wikidata for entity/historical/person fact claims.

### Wikipedia Client

```kotlin
class WikipediaClient @Inject constructor(private val httpClient: HttpClient) {

    suspend fun getSummary(topic: String): WikipediaSummary? {
        val encoded = topic.replace(" ", "_")
        return try {
            httpClient.get(
                "https://en.wikipedia.org/api/rest_v1/page/summary/$encoded"
            )
        } catch (e: Exception) { null }
    }

    // Search when exact title unknown
    suspend fun search(query: String): List<WikipediaSearchResult> {
        return httpClient.get(
            "https://en.wikipedia.org/w/api.php"
        ) {
            parameter("action", "search")
            parameter("list", "search")
            parameter("srsearch", query)
            parameter("format", "json")
            parameter("srlimit", 3)
        }
    }
}
```

### Wikidata SPARQL Client

Best for structured facts: roles, dates, relationships.

```kotlin
class WikidataSparqlClient @Inject constructor(private val httpClient: HttpClient) {

    // Example: verify "Anwar Ibrahim is PM of Malaysia"
    suspend fun queryPersonRole(personName: String, country: String): WikidataResult {
        val sparql = """
            SELECT ?person ?personLabel ?role ?roleLabel WHERE {
              ?person wdt:P31 wd:Q5.
              ?person rdfs:label "$personName"@en.
              ?person wdt:P39 ?role.
              SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
            }
            LIMIT 5
        """.trimIndent()

        return httpClient.get("https://query.wikidata.org/sparql") {
            parameter("query", sparql)
            parameter("format", "json")
        }
    }
}
```

### Tasks
- [ ] Implement `WikipediaClient` (summary + search endpoints)
- [ ] Implement `WikidataSparqlClient` with common query templates
- [ ] Wire both into `GeneralFactCheckPipeline` for HISTORICAL and PERSON_FACT claims
- [ ] Add `SourceType.KNOWLEDGE_BASE` rendering in UI (distinct icon)

---

## Phase 4 — Malaysian RSS Cache

**Goal:** Local cache of Malaysian news headlines, searchable offline, zero API cost.

### Why RSS Cache
- Bernama, FMT, Astro Awani, The Star, NST all expose public RSS feeds
- Poll every 2–4 hours via `WorkManager`
- Store in Room: headline + snippet + URL + publisher + timestamp
- Search locally before hitting any remote API

### RSS Feeds

```kotlin
val MALAYSIAN_RSS_FEEDS = listOf(
    RssFeed("Bernama",      "https://www.bernama.com/en/rss/news.xml",      CredibilityTier.VERIFIED),
    RssFeed("FMT",          "https://www.freemalaysiatoday.com/feed/",      CredibilityTier.GENERAL),
    RssFeed("Astro Awani",  "https://www.astroawani.com/rss/latest.xml",    CredibilityTier.GENERAL),
    RssFeed("The Star",     "https://www.thestar.com.my/rss/news/",         CredibilityTier.GENERAL),
    RssFeed("NST",          "https://www.nst.com.my/rss/news",              CredibilityTier.GENERAL),
    RssFeed("Malaysiakini", "https://malaysiakini.com/rss",                 CredibilityTier.GENERAL)
)
```

### Room Entity

```kotlin
@Entity(tableName = "rss_cache")
data class RssCacheEntity(
    @PrimaryKey val url: String,
    val title: String,
    val snippet: String,
    val publisher: String,
    val credibilityTier: String,
    val publishedAt: Long,
    val cachedAt: Long = System.currentTimeMillis()
)

@Dao
interface RssCacheDao {
    @Query("""
        SELECT * FROM rss_cache
        WHERE title LIKE '%' || :query || '%'
        OR snippet LIKE '%' || :query || '%'
        ORDER BY publishedAt DESC
        LIMIT 10
    """)
    suspend fun search(query: String): List<RssCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RssCacheEntity>)

    @Query("DELETE FROM rss_cache WHERE cachedAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)
}
```

### WorkManager Sync

```kotlin
class RssSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            MALAYSIAN_RSS_FEEDS.forEach { feed ->
                val items = rssParser.parse(feed.url)
                rssCacheDao.insertAll(items.map { it.toEntity(feed) })
            }
            rssCacheDao.purgeOlderThan(System.currentTimeMillis() - 7.days.inMilliseconds)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

// Schedule in Application class
WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "rss_sync",
    ExistingPeriodicWorkPolicy.KEEP,
    PeriodicWorkRequestBuilder<RssSyncWorker>(3, TimeUnit.HOURS).build()
)
```

### Tasks
- [ ] Add RSS XML parser (use `Rome` library or write lightweight parser)
- [ ] Implement `RssCacheDao` and `RssCacheEntity`
- [ ] Implement `RssSyncWorker` via WorkManager
- [ ] Implement `RssCacheRepository` with `search()` method
- [ ] Wire RSS cache as Layer 0 in `CurrentEventPipeline` (search before any remote API)
- [ ] Add WorkManager dependency

---

## Phase 5 — Statistical & Government Data Sources

**Goal:** Ground statistical claims in authoritative Malaysian and global data.

### DOSM Client

```kotlin
class DosmClient @Inject constructor(private val httpClient: HttpClient) {
    // DOSM OpenDOSM API — free, no key required
    private val BASE = "https://api.data.gov.my/data-catalogue"

    suspend fun search(query: String): List<DosmDataset> {
        return httpClient.get(BASE) {
            parameter("meta", true)
            parameter("filter__description__icontains", query)
        }
    }
}
```

### World Bank Client

```kotlin
class WorldBankClient @Inject constructor(private val httpClient: HttpClient) {
    // Indicator examples: NY.GDP.MKTP.KD.ZG (GDP growth), SP.POP.TOTL (population)
    suspend fun getIndicator(countryCode: String, indicatorCode: String): WorldBankResult {
        return httpClient.get(
            "https://api.worldbank.org/v2/country/$countryCode/indicator/$indicatorCode"
        ) {
            parameter("format", "json")
            parameter("mrv", 5)  // most recent 5 values
        }
    }
}
```

### Statistical Pipeline

```kotlin
class StatisticalFactCheckPipeline @Inject constructor(
    private val dosmClient: DosmClient,
    private val worldBankClient: WorldBankClient,
    private val tavilyClient: TavilyClient,
    private val llmRouter: LlmRouter
) {
    suspend fun verify(claim: ClassifiedClaim): CorvusCheckResult.GeneralResult {
        val sources = mutableListOf<CorvusSource>()

        // Try DOSM first for Malaysian statistics
        sources += dosmClient.search(claim.raw).take(3).map { it.toSource() }

        // World Bank for economic indicators
        sources += worldBankClient.searchRelated(claim.entities).map { it.toSource() }

        // Tavily fallback for anything not found above
        if (sources.size < 2) {
            sources += tavilyClient.search(claim.raw, maxResults = 3)
        }

        return llmRouter.analyze(claim.raw, sources, ClaimType.STATISTICAL)
    }
}
```

### Tasks
- [ ] Implement `DosmClient` (OpenDOSM API)
- [ ] Implement `WorldBankClient`
- [ ] Implement `DataGovMyClient` (data.gov.my datasets)
- [ ] Implement `StatisticalFactCheckPipeline`
- [ ] Add `CredibilityTier.PRIMARY` badge for government data sources in UI

---

## Phase 6 — Scientific Claims (PubMed)

**Goal:** Ground health and science claims in peer-reviewed literature.

### PubMed Client

```kotlin
class PubMedClient @Inject constructor(private val httpClient: HttpClient) {

    // Step 1: Search for relevant paper IDs
    suspend fun search(query: String): List<String> {
        val response = httpClient.get(
            "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi"
        ) {
            parameter("db", "pubmed")
            parameter("term", query)
            parameter("retmax", 5)
            parameter("retmode", "json")
        }
        return parsePmids(response)
    }

    // Step 2: Fetch abstract for each ID
    suspend fun fetchAbstract(pmid: String): PubMedAbstract? {
        return httpClient.get(
            "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi"
        ) {
            parameter("db", "pubmed")
            parameter("id", pmid)
            parameter("rettype", "abstract")
            parameter("retmode", "text")
        }
    }
}
```

### Tasks
- [ ] Implement `PubMedClient` (search + abstract fetch)
- [ ] Implement `ScientificFactCheckPipeline`
- [ ] Wire PubMed into pipeline for SCIENTIFIC claim type only
- [ ] Add `SourceType.ACADEMIC` rendering (distinct from web sources)

---

## Phase 7 — GDELT for Current Events

**Goal:** Real-time global news event tracking for current event claims.

```kotlin
class GdeltClient @Inject constructor(private val httpClient: HttpClient) {

    // GDELT DOC 2.0 API — free, no key
    suspend fun search(query: String, fromDate: String? = null): List<GdeltArticle> {
        return httpClient.get("https://api.gdeltproject.org/api/v2/doc/doc") {
            parameter("query", query)
            parameter("mode", "artlist")
            parameter("maxrecords", 10)
            parameter("format", "json")
            fromDate?.let { parameter("startdatetime", it) }
        }
    }
}
```

### Tasks
- [ ] Implement `GdeltClient`
- [ ] Wire GDELT into `CurrentEventPipeline` alongside Tavily
- [ ] Parse GDELT tone scores as an additional credibility signal

---

## Phase 8 — UI Enhancements for New Claim Types

**Goal:** Result screen adapts to claim type — quote results look different from statistical results.

### Quote Result Card

```
┌─────────────────────────────────────┐
│  🔍 QUOTE VERIFICATION              │
│                                     │
│  Speaker: Tun Dr. Mahathir Mohamad  │
│  Verdict: OUT OF CONTEXT   ⚠️        │
│  Confidence: 72%                    │
│                                     │
│  Submitted:                         │
│  "The economy will collapse by 2025"│
│                                     │
│  Original (2018):                   │
│  "If nothing changes, by 2025..."   │
│                                     │
│  Context: Quote is real but missing │
│  the conditional phrasing. Speaker  │
│  was warning, not predicting.       │
│                                     │
│  Source: Bernama, March 2018 →      │
└─────────────────────────────────────┘
```

### Statistical Result Card

```
┌─────────────────────────────────────┐
│  📊 STATISTICAL CLAIM               │
│                                     │
│  Verdict: MISLEADING        ⚠️       │
│  Confidence: 81%                    │
│                                     │
│  Claim uses 2019 data, not current. │
│  Latest DOSM figure (2023): 4.1%   │
│  Claimed figure (2019): 6.2%        │
│                                     │
│  Source: DOSM [Primary] →           │
│  Source: World Bank →               │
└─────────────────────────────────────┘
```

### Composable Updates

```kotlin
@Composable
fun CorvusResultContent(result: CorvusCheckResult) {
    when (result) {
        is CorvusCheckResult.QuoteResult      -> QuoteResultCard(result)
        is CorvusCheckResult.GeneralResult    -> when (result.claimType) {
            ClaimType.STATISTICAL             -> StatisticalResultCard(result)
            ClaimType.SCIENTIFIC              -> ScientificResultCard(result)
            else                              -> GeneralResultCard(result)
        }
    }
}
```

### Tasks
- [ ] Implement `QuoteResultCard` composable
- [ ] Implement `StatisticalResultCard` composable
- [ ] Implement `ScientificResultCard` composable
- [ ] Update `CorvusResultContent` with type-aware routing
- [ ] Add source credibility tier badges (`PRIMARY`, `VERIFIED`, `GENERAL`)
- [ ] Add `SourceType` icons (transcript icon, database icon, academic icon)

---

## Full Source Matrix

| Source | API | Key Required | Cost | Claim Types | Credibility |
|---|---|---|---|---|---|
| Google Fact Check | REST | ✅ Free | Free | All | Verified |
| Sebenarnya.my | WordPress REST | ❌ | Free | All (MY) | Verified |
| Wikiquote | REST | ❌ | Free | Quote | Verified |
| Wikipedia | REST | ❌ | Free | Historical, Person | General |
| Wikidata SPARQL | SPARQL | ❌ | Free | Person, Historical | General |
| Hansard (via Tavily) | Proxied | ✅ Tavily | Free tier | Quote (MY politics) | Primary |
| parlimen.gov.my | Tavily proxy | ✅ Tavily | Free tier | Quote, Political | Primary |
| Bernama RSS | RSS | ❌ | Free | Current Event | Verified |
| FMT RSS | RSS | ❌ | Free | Current Event | General |
| Astro Awani RSS | RSS | ❌ | Free | Current Event | General |
| The Star RSS | RSS | ❌ | Free | Current Event | General |
| NST RSS | RSS | ❌ | Free | Current Event | General |
| Tavily | REST | ✅ Free | 1000/mo | All | General |
| Google Custom Search | REST | ✅ Free | 100/day | All | General |
| GDELT | REST | ❌ | Free | Current Event | General |
| DOSM OpenDOSM | REST | ❌ | Free | Statistical (MY) | Primary |
| data.gov.my | REST | ❌ | Free | Statistical (MY) | Primary |
| World Bank | REST | ❌ | Free | Statistical | Primary |
| PubMed | REST | ❌ | Free | Scientific | Primary |
| Semantic Scholar | REST | ❌ | Free | Scientific | Verified |

---

## Implementation Roadmap

| Phase | Feature | Estimated Duration |
|---|---|---|
| 1 | Claim Classifier | 3 days |
| 2 | Quote Verification Pipeline | 5 days |
| 3 | Knowledge Base (Wikipedia + Wikidata) | 3 days |
| 4 | Malaysian RSS Cache + WorkManager | 4 days |
| 5 | Statistical + Government Data Sources | 4 days |
| 6 | Scientific Claims (PubMed) | 3 days |
| 7 | GDELT Current Events | 2 days |
| 8 | UI — Claim-Type-Aware Result Cards | 4 days |
| **Total** | | **~4 weeks** |

---

## New API Keys Required

| Key | Source | Free Limit |
|---|---|---|
| No new paid keys required | — | — |

All new sources in this plan are either keyless (Wikipedia, Wikidata, GDELT, DOSM, PubMed, RSS) or already covered by existing keys (Tavily for Hansard proxy, Google CS).

---

## Definition of Done

- Claim classifier correctly routes 90%+ of test claims to correct pipeline
- Quote pipeline returns `VERIFIED` for 5 known real Malaysian political quotes
- Quote pipeline returns `FABRICATED` or `MISATTRIBUTED` for 3 known viral fake quotes
- Wikipedia and Wikidata integrated and returning results for person/historical claims
- RSS cache populates on first launch and refreshes every 3 hours
- DOSM returns data for at least 3 Malaysian economic claim types
- PubMed returns relevant abstracts for health/science claims
- GDELT integrated into current event pipeline
- All 4 result card variants render correctly with real data
- No regressions in existing general pipeline
