# Corvus — Feature Enhancement Plan II
## Claim Decomposition · Viral Claim Detection · Confidence Timeline · Source Bias Scoring

> **Scope:** Four targeted intelligence and UX upgrades that build on top of the existing pipeline and enhancement plan. All features are zero-cost, additive, and non-breaking to the existing architecture.

---

## Overview

| Feature | What It Solves | Effort |
|---|---|---|
| **Claim Decomposition** | Compound statements with multiple sub-claims get a single misleading verdict today — this breaks them apart | Medium |
| **Viral Claim Detection** | No signal today on whether a claim is a known circulating hoax before the full pipeline runs | Low |
| **Confidence Timeline** | Verdict is a snapshot — no context on how evidence has evolved over time | High |
| **Source Bias Scoring** | Sources are shown but not evaluated — users can't assess credibility themselves | Low |

---

---

# Feature 1 — Claim Decomposition

## Problem

A compound claim like:

> *"Anwar's government raised petrol prices by 40% while unemployment hit a 10-year high"*

contains two independent factual assertions. Today, Corvus checks this as a single claim and returns one blended verdict. The result is ambiguous at best and misleading at worst — one sub-claim could be TRUE and the other FALSE.

## Design

### Flow

```
Raw input claim
    ↓
[Compound Detector] — Is this a compound claim?
    → Single claim? → Existing pipeline (no change)
    → Compound claim? → Decompose
    ↓
[Decomposer] — Split into N atomic sub-claims
    ↓
[Parallel Pipeline] — Check each sub-claim independently
    ↓
[Aggregator] — Merge sub-verdicts into composite result
    ↓
CompositeResult → UI
```

### New Models

```kotlin
data class SubClaim(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val index: Int,
    val result: CorvusCheckResult? = null  // null until checked
)

data class CompositeResult(
    val originalClaim: String,
    val subClaims: List<SubClaim>,
    val compositeVerdict: Verdict,
    val compositeConfidence: Float,
    val compositeSummary: String
)

// Composite verdict derivation rules
// All TRUE                   → TRUE
// All FALSE                  → FALSE
// Mix of TRUE + FALSE        → PARTIALLY_TRUE
// Any MISLEADING present     → MISLEADING (takes priority)
// Any UNVERIFIABLE, rest OK  → PARTIALLY_TRUE
// All UNVERIFIABLE           → UNVERIFIABLE
```

### Compound Detector Prompt

Runs on Groq first — cheap, fast. Only triggers decomposition if needed.

```
Analyze this statement and determine if it contains multiple independent factual claims.

Statement: "{claim}"

A compound claim contains two or more assertions that can each be independently
verified as true or false (e.g. joined by "while", "and", "but", "as", "despite",
"even though", or structured as separate sentences).

Respond ONLY with valid JSON:
{
  "is_compound": true/false,
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
```

### Decomposer Implementation

```kotlin
class ClaimDecomposerUseCase @Inject constructor(
    private val groqClient: GroqClient
) {
    suspend fun decompose(raw: String): DecompositionResult {
        val response = groqClient.complete(
            prompt = buildDecomposerPrompt(raw),
            model = "llama-3.3-70b-versatile",
            maxTokens = 500
        )
        val parsed = parseDecomposerResponse(response)

        return if (parsed.isCompound && parsed.subClaims.size >= 2) {
            DecompositionResult.Compound(
                original = raw,
                subClaims = parsed.subClaims.mapIndexed { i, text ->
                    SubClaim(text = text, index = i)
                }
            )
        } else {
            DecompositionResult.Single(raw)
        }
    }
}

sealed class DecompositionResult {
    data class Single(val claim: String) : DecompositionResult()
    data class Compound(val original: String, val subClaims: List<SubClaim>) : DecompositionResult()
}
```

### Parallel Pipeline Execution

```kotlin
class CompositeFactCheckPipeline @Inject constructor(
    private val decomposer: ClaimDecomposerUseCase,
    private val factCheckUseCase: CorvusFactCheckUseCase
) {
    suspend fun check(raw: String): CorvusCheckResult {
        return when (val decomposed = decomposer.decompose(raw)) {
            is DecompositionResult.Single   -> factCheckUseCase.check(decomposed.claim)
            is DecompositionResult.Compound -> checkCompound(decomposed)
        }
    }

    private suspend fun checkCompound(
        decomposed: DecompositionResult.Compound
    ): CorvusCheckResult.CompositeCheckResult {
        // Check all sub-claims in parallel — bounded concurrency (max 3 at once)
        val results = decomposed.subClaims
            .chunked(3)
            .flatMap { chunk ->
                chunk.map { subClaim ->
                    async { factCheckUseCase.check(subClaim.text) }
                }.awaitAll()
            }

        val checked = decomposed.subClaims.zip(results).map { (sub, result) ->
            sub.copy(result = result)
        }

        return CorvusCheckResult.CompositeCheckResult(
            originalClaim = decomposed.original,
            subClaims = checked,
            compositeVerdict = deriveCompositeVerdict(checked),
            compositeConfidence = checked.mapNotNull { it.result?.confidence }.average().toFloat(),
            compositeSummary = buildCompositeSummary(checked)
        )
    }

    private fun deriveCompositeVerdict(subClaims: List<SubClaim>): Verdict {
        val verdicts = subClaims.mapNotNull {
            (it.result as? CorvusCheckResult.GeneralResult)?.verdict
        }
        return when {
            verdicts.all { it == Verdict.TRUE }          -> Verdict.TRUE
            verdicts.all { it == Verdict.FALSE }         -> Verdict.FALSE
            verdicts.any { it == Verdict.MISLEADING }    -> Verdict.MISLEADING
            verdicts.all { it == Verdict.UNVERIFIABLE }  -> Verdict.UNVERIFIABLE
            else                                         -> Verdict.PARTIALLY_TRUE
        }
    }
}
```

### UI — Composite Result Card

```
┌─────────────────────────────────────────┐
│  PARTIALLY TRUE                    🟡   │
│  Compound claim · 2 sub-claims          │
│  Confidence: 74%                        │
├─────────────────────────────────────────┤
│  ① Petrol prices rose 40%               │
│     FALSE ❌  · 81% confidence          │
│     Actual increase: 22% (DOSM)         │
├─────────────────────────────────────────┤
│  ② Unemployment at 10-year high         │
│     TRUE ✅  · 67% confidence           │
│     Confirmed by Dept. of Statistics    │
├─────────────────────────────────────────┤
│  One claim is false, one is true.       │
│  The overall statement is misleading    │
│  due to the inflated petrol figure.     │
└─────────────────────────────────────────┘
```

```kotlin
@Composable
fun CompositeResultCard(result: CorvusCheckResult.CompositeCheckResult) {
    Column {
        // Overall verdict header
        CompositeVerdictHeader(
            verdict = result.compositeVerdict,
            confidence = result.compositeConfidence,
            subClaimCount = result.subClaims.size
        )
        HorizontalDivider()

        // Individual sub-claim rows
        result.subClaims.forEachIndexed { index, subClaim ->
            SubClaimRow(
                index = index + 1,
                subClaim = subClaim,
                modifier = Modifier.animateContentSize()
            )
            if (index < result.subClaims.lastIndex) HorizontalDivider()
        }

        HorizontalDivider()
        // Composite summary
        Text(
            text = result.compositeSummary,
            style = CorvusTheme.typography.body,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun SubClaimRow(index: Int, subClaim: SubClaim) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier
        .clickable { expanded = !expanded }
        .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$index", style = CorvusTheme.typography.label,
                color = CorvusTheme.colors.accent)
            Spacer(Modifier.width(8.dp))
            Text(subClaim.text, style = CorvusTheme.typography.body,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            subClaim.result?.let { VerdictChip(it.verdict) }
        }
        if (expanded) {
            subClaim.result?.let { SubClaimDetail(it) }
        }
    }
}
```

### Tasks
- [ ] Implement `ClaimDecomposerUseCase` with compound detector prompt
- [ ] Implement `parseDecomposerResponse()` with JSON fallback
- [ ] Implement `CompositeFactCheckPipeline` with bounded parallel execution
- [ ] Implement `deriveCompositeVerdict()` logic
- [ ] Add `CompositeCheckResult` to sealed class hierarchy
- [ ] Implement `CompositeResultCard` composable
- [ ] Implement `SubClaimRow` with expand/collapse
- [ ] Replace direct `CorvusFactCheckUseCase` call in ViewModel with `CompositeFactCheckPipeline`
- [ ] Unit test: 10 compound claims, verify correct split
- [ ] Unit test: all 6 composite verdict derivation cases
- [ ] UI test: expand/collapse sub-claim rows

**Estimated duration: 5 days**

---

---

# Feature 2 — Viral Claim Detection

## Problem

The current pipeline runs a full retrieval + LLM analysis for every claim regardless of whether it is a well-known hoax that has been debunked hundreds of times. Viral claim detection adds a **fast pre-check layer** that surfaces known misinformation immediately — before any expensive pipeline work.

## Design

### Flow

```
Raw claim
    ↓
[Viral Detector] — parallel lookup in 3 sources
    ├── Junkipedia API
    ├── Google Fact Check API (already in pipeline — reuse)
    └── Fuzzy match against local viral hoax cache (Room)
    ↓
    → Strong match (similarity > 0.85)? → Return viral result immediately
    → Weak match (0.6 – 0.85)?         → Flag as "possibly related to known hoax"
                                           + continue full pipeline
    → No match?                        → Continue full pipeline silently
```

### Junkipedia

Junkipedia is a free, open database of known viral misinformation narratives. It has a public search API requiring no authentication.

```kotlin
class JunkipediaClient @Inject constructor(
    private val httpClient: HttpClient
) {
    suspend fun search(claim: String): List<JunkipediaResult> {
        return httpClient.get("https://www.junkipedia.org/api/v1/search") {
            parameter("q", claim)
            parameter("limit", 5)
        }
    }
}

data class JunkipediaResult(
    val id: String,
    val title: String,
    val summary: String,
    val url: String,
    val firstSeen: String?,
    val spreadCount: Int?,
    val debunkUrls: List<String>
)
```

### Viral Hoax Local Cache

Store confirmed viral claims locally for offline detection and to avoid redundant API calls.

```kotlin
@Entity(tableName = "viral_hoax_cache")
data class ViralHoaxEntity(
    @PrimaryKey val id: String,
    val normalizedText: String,     // Lowercased, punctuation-stripped for matching
    val title: String,
    val summary: String,
    val sourceUrl: String,
    val firstSeen: String?,
    val debunkUrls: String,         // JSON array
    val cachedAt: Long = System.currentTimeMillis()
)

@Dao
interface ViralHoaxDao {
    @Query("""
        SELECT * FROM viral_hoax_cache
        WHERE normalizedText LIKE '%' || :fragment || '%'
        LIMIT 5
    """)
    suspend fun searchByFragment(fragment: String): List<ViralHoaxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ViralHoaxEntity>)

    @Query("DELETE FROM viral_hoax_cache WHERE cachedAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)
}
```

### Similarity Scoring

Since claims are paraphrased differently each time they circulate, exact matching is insufficient. Use a simple token overlap similarity score before escalating to LLM embedding comparison.

```kotlin
object ClaimSimilarity {

    // Jaccard similarity on word tokens
    fun score(a: String, b: String): Float {
        val tokensA = normalize(a).split(" ").toSet()
        val tokensB = normalize(b).split(" ").toSet()
        val intersection = tokensA.intersect(tokensB).size.toFloat()
        val union = tokensA.union(tokensB).size.toFloat()
        return if (union == 0f) 0f else intersection / union
    }

    // Normalize: lowercase, strip punctuation, remove stopwords
    private fun normalize(input: String): String {
        val stopwords = setOf("the", "a", "an", "is", "was", "are", "were",
                              "in", "on", "at", "to", "of", "and", "or", "by")
        return input.lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")
            .split(" ")
            .filter { it.isNotBlank() && it !in stopwords }
            .joinToString(" ")
    }
}
```

### Viral Detector Use Case

```kotlin
class ViralClaimDetectorUseCase @Inject constructor(
    private val junkipediaClient: JunkipediaClient,
    private val viralHoaxDao: ViralHoaxDao
) {
    companion object {
        const val STRONG_MATCH_THRESHOLD = 0.85f
        const val WEAK_MATCH_THRESHOLD   = 0.60f
    }

    suspend fun detect(claim: String): ViralDetectionResult {

        // 1. Check local cache first (instant, offline)
        val localMatches = viralHoaxDao.searchByFragment(
            claim.lowercase().take(40)
        )
        val bestLocal = localMatches.maxByOrNull {
            ClaimSimilarity.score(claim, it.normalizedText)
        }
        if (bestLocal != null) {
            val score = ClaimSimilarity.score(claim, bestLocal.normalizedText)
            if (score >= STRONG_MATCH_THRESHOLD) {
                return ViralDetectionResult.KnownHoax(
                    matchedClaim = bestLocal.title,
                    summary = bestLocal.summary,
                    debunkUrls = Json.decodeFromString(bestLocal.debunkUrls),
                    similarityScore = score,
                    firstSeen = bestLocal.firstSeen
                )
            }
        }

        // 2. Check Junkipedia
        return try {
            val results = junkipediaClient.search(claim)
            val best = results.maxByOrNull {
                ClaimSimilarity.score(claim, it.title + " " + it.summary)
            }
            if (best != null) {
                val score = ClaimSimilarity.score(claim, best.title + " " + best.summary)
                when {
                    score >= STRONG_MATCH_THRESHOLD -> {
                        cacheResult(best)
                        ViralDetectionResult.KnownHoax(
                            matchedClaim = best.title,
                            summary = best.summary,
                            debunkUrls = best.debunkUrls,
                            similarityScore = score,
                            firstSeen = best.firstSeen
                        )
                    }
                    score >= WEAK_MATCH_THRESHOLD -> ViralDetectionResult.PossiblyRelated(
                        matchedClaim = best.title,
                        similarityScore = score,
                        debunkUrl = best.debunkUrls.firstOrNull()
                    )
                    else -> ViralDetectionResult.NotFound
                }
            } else {
                ViralDetectionResult.NotFound
            }
        } catch (e: Exception) {
            ViralDetectionResult.NotFound  // Fail silently, continue pipeline
        }
    }
}

sealed class ViralDetectionResult {
    data class KnownHoax(
        val matchedClaim  : String,
        val summary       : String,
        val debunkUrls    : List<String>,
        val similarityScore: Float,
        val firstSeen     : String?
    ) : ViralDetectionResult()

    data class PossiblyRelated(
        val matchedClaim  : String,
        val similarityScore: Float,
        val debunkUrl     : String?
    ) : ViralDetectionResult()

    object NotFound : ViralDetectionResult()
}
```

### Integration into Main Pipeline

```kotlin
// In CompositeFactCheckPipeline / CorvusFactCheckUseCase
suspend fun check(raw: String): CorvusCheckResult {

    // Viral detection runs first, in parallel with decomposition
    val viralDeferred = async { viralDetector.detect(raw) }
    val decomposedDeferred = async { decomposer.decompose(raw) }

    val viral = viralDeferred.await()

    // Strong match — return immediately, skip full pipeline
    if (viral is ViralDetectionResult.KnownHoax) {
        return CorvusCheckResult.ViralHoaxResult(viral)
    }

    val decomposed = decomposedDeferred.await()
    val result = checkDecomposed(decomposed)

    // Weak match — attach viral flag to result
    return if (viral is ViralDetectionResult.PossiblyRelated) {
        result.withViralFlag(viral)
    } else {
        result
    }
}
```

### UI — Viral Warning Banner

```
┌─────────────────────────────────────────────┐
│  ⚠️  KNOWN VIRAL CLAIM                       │
│  This matches a hoax circulating since 2022  │
│  Similarity: 91%                             │
│  [See debunking article →]                   │
└─────────────────────────────────────────────┘
```

For `PossiblyRelated`, show a softer inline warning beneath the verdict:
*"A similar claim has been flagged before. Treat with caution."*

```kotlin
@Composable
fun ViralWarningBanner(result: ViralDetectionResult.KnownHoax) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = CorvusTheme.colors.verdictFalse.copy(alpha = 0.12f)
        ),
        border = BorderStroke(1.dp, CorvusTheme.colors.verdictFalse),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Icon(CorvusIcons.Warning, tint = CorvusTheme.colors.verdictFalse)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("KNOWN VIRAL CLAIM",
                    style = CorvusTheme.typography.label,
                    color = CorvusTheme.colors.verdictFalse)
                Text("Matches hoax circulating since ${result.firstSeen ?: "unknown date"}",
                    style = CorvusTheme.typography.body)
                Text("Similarity: ${(result.similarityScore * 100).roundToInt()}%",
                    style = CorvusTheme.typography.caption,
                    color = CorvusTheme.colors.textSecondary)
                result.debunkUrls.firstOrNull()?.let { url ->
                    TextButton(onClick = { openUrl(url) }) {
                        Text("See debunking article →")
                    }
                }
            }
        }
    }
}
```

### Tasks
- [ ] Implement `JunkipediaClient`
- [ ] Implement `ViralHoaxEntity` + `ViralHoaxDao`
- [ ] Implement `ClaimSimilarity` utility object
- [ ] Implement `ViralClaimDetectorUseCase`
- [ ] Wire viral detector into pipeline as parallel pre-check
- [ ] Implement `ViralWarningBanner` composable
- [ ] Implement `PossiblyRelated` inline flag in result cards
- [ ] Schedule `ViralHoaxCacheSyncWorker` via WorkManager (weekly refresh)
- [ ] Unit test: 5 known viral claims (strong match expected)
- [ ] Unit test: 5 paraphrased versions of viral claims (weak match expected)
- [ ] Unit test: 5 unrelated claims (no match expected)

**Estimated duration: 4 days**

---

---

# Feature 3 — Confidence Timeline

## Problem

Fact-check verdicts are not static. A claim might have been:
- **Unverifiable in 2021** → confirmed TRUE in 2023 when official data was released
- **TRUE in 2020** → reclassified as MISLEADING in 2022 with new context
- **FALSE** → still FALSE in 2024 despite continued circulation

A single current verdict gives no sense of this history. The confidence timeline shows **how the evidence and consensus around a claim has evolved**, grounding the verdict in its temporal context.

## Design

### What the Timeline Shows

Each timeline entry represents one of:
- A **previous Corvus check** of the same or similar claim (from local history)
- A **published fact-check article** from the Google Fact Check API with a date
- A **major news event** that changed the context of the claim

### Data Model

```kotlin
data class TimelineEntry(
    val date: String,                   // ISO 8601
    val verdict: Verdict?,              // null if it's a context event, not a verdict
    val confidence: Float?,
    val summary: String,                // What was known/decided at this point
    val source: CorvusSource?,
    val entryType: TimelineEntryType
)

enum class TimelineEntryType {
    CORVUS_CHECK,       // From local Room history
    FACT_CHECK_ARTICLE, // From Google Fact Check API with date
    CONTEXT_EVENT,      // Related news event that changed context
    CLAIM_ORIGIN        // Estimated first appearance of the claim
}

data class ConfidenceTimeline(
    val entries: List<TimelineEntry>,   // Sorted oldest → newest
    val trend: VerdictTrend,
    val hasSignificantChange: Boolean
)

enum class VerdictTrend {
    STABLE,             // Verdict consistent across all entries
    IMPROVING,          // Moving toward TRUE / more certain
    DETERIORATING,      // Moving toward FALSE / more uncertain
    CONTESTED,          // Mixed verdicts over time
    INSUFFICIENT_DATA   // Fewer than 2 data points
}
```

### Timeline Builder

```kotlin
class ConfidenceTimelineBuilder @Inject constructor(
    private val googleFactCheckClient: GoogleFactCheckClient,
    private val historyDao: CorvusHistoryDao,
    private val groqClient: GroqClient
) {
    suspend fun build(
        claim: String,
        currentResult: CorvusCheckResult
    ): ConfidenceTimeline {
        val entries = mutableListOf<TimelineEntry>()

        // Source 1: Published fact-check articles with dates
        val factChecks = googleFactCheckClient.search(claim)
        entries += factChecks
            .filter { it.reviewDate != null }
            .map { it.toTimelineEntry() }

        // Source 2: Previous local Corvus checks for similar claims
        val localHistory = historyDao.searchSimilar(claim, limit = 5)
        entries += localHistory.map { it.toTimelineEntry() }

        // Source 3: LLM-identified context events (only if 2+ fact-checks found)
        if (entries.size >= 2) {
            entries += buildContextEvents(claim, entries)
        }

        val sorted = entries.sortedBy { it.date }
        return ConfidenceTimeline(
            entries = sorted,
            trend = deriveTrend(sorted),
            hasSignificantChange = detectSignificantChange(sorted)
        )
    }

    // Ask LLM: "What major events changed the context of this claim?"
    private suspend fun buildContextEvents(
        claim: String,
        existingEntries: List<TimelineEntry>
    ): List<TimelineEntry> {
        val prompt = """
            Given this claim: "$claim"
            And these existing fact-check dates: ${existingEntries.map { it.date }}
            
            Identify up to 2 major real-world events that changed the context or 
            truth status of this claim. Only include events you are highly confident about.
            
            Respond ONLY with valid JSON:
            [
              {
                "date": "YYYY-MM",
                "event": "Brief description of what changed",
                "impact": "How this affected the claim's truth status"
              }
            ]
            
            If no significant events exist, return an empty array: []
        """.trimIndent()

        return try {
            val response = groqClient.complete(prompt, maxTokens = 400)
            parseContextEvents(response)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun deriveTrend(entries: List<TimelineEntry>): VerdictTrend {
        val verdicts = entries.mapNotNull { it.verdict }
        if (verdicts.size < 2) return VerdictTrend.INSUFFICIENT_DATA

        val first = verdicts.first()
        val last  = verdicts.last()
        val allSame = verdicts.all { it == first }

        return when {
            allSame                                               -> VerdictTrend.STABLE
            first == Verdict.UNVERIFIABLE && last == Verdict.TRUE -> VerdictTrend.IMPROVING
            first == Verdict.TRUE && last == Verdict.FALSE        -> VerdictTrend.DETERIORATING
            verdicts.toSet().size > 2                             -> VerdictTrend.CONTESTED
            else                                                  -> VerdictTrend.STABLE
        }
    }

    private fun detectSignificantChange(entries: List<TimelineEntry>): Boolean {
        val verdicts = entries.mapNotNull { it.verdict }
        return verdicts.toSet().size > 1
    }
}
```

### Local History Similarity Query

```kotlin
// In CorvusHistoryDao
@Query("""
    SELECT * FROM corvus_history
    WHERE claim LIKE '%' || :fragment || '%'
    ORDER BY checkedAt DESC
    LIMIT :limit
""")
suspend fun searchSimilar(fragment: String, limit: Int): List<CorvusHistoryEntity>
```

### UI — Timeline Component

Only shown when 2+ timeline entries exist. Collapsible by default.

```
VERDICT HISTORY

  2021 · Mar     UNVERIFIABLE ◌
  │              No official data available at the time
  │
  2022 · Aug     ── Policy announcement changed context ──
  │
  2023 · Jan     TRUE ✅  (Bernama · Google Fact Check)
  │              DOSM confirmed figures in Q4 2022 report
  │
  2024 · Now     TRUE ✅  (Corvus · Current check)
                 Consistent with all recent sources

  Trend: STABLE (confirmed true over time)
```

```kotlin
@Composable
fun ConfidenceTimelineCard(timeline: ConfidenceTimeline) {
    var expanded by remember { mutableStateOf(false) }

    // Only render if meaningful data exists
    if (timeline.entries.size < 2) return

    Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column {
            // Header row — always visible
            Row(
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("VERDICT HISTORY",
                        style = CorvusTheme.typography.label,
                        color = CorvusTheme.colors.textSecondary)
                    Text(
                        when (timeline.trend) {
                            VerdictTrend.STABLE       -> "Verdict consistent over time"
                            VerdictTrend.IMPROVING    -> "Evidence strengthening"
                            VerdictTrend.DETERIORATING -> "Verdict has changed"
                            VerdictTrend.CONTESTED    -> "Contested over time"
                            else                      -> "${timeline.entries.size} data points"
                        },
                        style = CorvusTheme.typography.body
                    )
                }
                TrendChip(timeline.trend)
            }

            // Expandable timeline
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    timeline.entries.forEachIndexed { index, entry ->
                        TimelineEntryRow(
                            entry = entry,
                            isLast = index == timeline.entries.lastIndex
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineEntryRow(entry: TimelineEntry, isLast: Boolean) {
    Row {
        // Vertical line + dot
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = entry.verdict?.toColor() ?: CorvusTheme.colors.border,
                        shape = CircleShape
                    )
            )
            if (!isLast) {
                Box(modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(CorvusTheme.colors.border))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.date,
                    style = CorvusTheme.typography.caption,
                    color = CorvusTheme.colors.textSecondary)
                entry.verdict?.let { VerdictChip(it, compact = true) }
            }
            Text(entry.summary,
                style = CorvusTheme.typography.body,
                color = CorvusTheme.colors.textPrimary)
            entry.source?.let {
                Text(it.publisher ?: it.url,
                    style = CorvusTheme.typography.caption,
                    color = CorvusTheme.colors.accent)
            }
        }
    }
}

@Composable
fun TrendChip(trend: VerdictTrend) {
    val (label, color) = when (trend) {
        VerdictTrend.STABLE        -> "Stable"      to CorvusTheme.colors.verdictTrue
        VerdictTrend.IMPROVING     -> "Improving"   to CorvusTheme.colors.verdictTrue
        VerdictTrend.DETERIORATING -> "Changed"     to CorvusTheme.colors.verdictFalse
        VerdictTrend.CONTESTED     -> "Contested"   to CorvusTheme.colors.verdictMisleading
        else                       -> "Limited data" to CorvusTheme.colors.textTertiary
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(label.uppercase(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = CorvusTheme.typography.caption,
            color = color)
    }
}
```

### Tasks
- [ ] Implement `TimelineEntry` and `ConfidenceTimeline` models
- [ ] Implement `ConfidenceTimelineBuilder`
- [ ] Add `searchSimilar()` to `CorvusHistoryDao`
- [ ] Add `reviewDate` parsing to `GoogleFactCheckClient` response
- [ ] Implement context event LLM prompt + parser
- [ ] Implement `deriveTrend()` and `detectSignificantChange()`
- [ ] Implement `ConfidenceTimelineCard` composable
- [ ] Implement `TimelineEntryRow` with vertical connector line
- [ ] Implement `TrendChip` composable
- [ ] Wire `ConfidenceTimelineBuilder` into `CorvusViewModel` (runs after main result)
- [ ] Unit test: `deriveTrend()` for all 5 trend cases
- [ ] Unit test: timeline builds correctly from mixed local + remote sources

**Estimated duration: 6 days**

---

---

# Feature 4 — Source Bias & Credibility Scoring

## Problem

Corvus currently shows sources but gives users no signal about their credibility or potential bias. A result citing *The New York Times* and a result citing an anonymous blog both look the same in the UI. Users cannot make informed judgments about the evidence quality.

## Design

### Scoring Dimensions

Each source is scored on two independent axes:

```
Credibility (0–100)     How factually reliable is this outlet?
  0  = Known misinformation source
  50 = Mixed / unverified
  100 = Consistently factual, verified

Bias (-2 to +2)         Political or institutional lean
  -2 = Far left
  -1 = Left-leaning
   0 = Center / neutral
  +1 = Right-leaning
  +2 = Far right
   G = Government-affiliated (separate axis)
   S = Satire (flagged separately)
```

### Data Sources for Ratings

```kotlin
// Source 1: Hardcoded MY_OUTLET_RATINGS — Malaysian outlets
// Media Bias Fact Check does not cover Malaysian media well
// Build and maintain this manually — small, high-value dataset

val MY_OUTLET_RATINGS = mapOf(
    "bernama.com"              to OutletRating(credibility = 75, bias = 0,  isGovAffiliated = true),
    "malaysiakini.com"         to OutletRating(credibility = 80, bias = -1, isGovAffiliated = false),
    "freemalaysiatoday.com"    to OutletRating(credibility = 72, bias = -1, isGovAffiliated = false),
    "thestar.com.my"           to OutletRating(credibility = 78, bias = 0,  isGovAffiliated = false),
    "nst.com.my"               to OutletRating(credibility = 70, bias = 1,  isGovAffiliated = false),
    "astroawani.com"           to OutletRating(credibility = 73, bias = 0,  isGovAffiliated = false),
    "utusan.com.my"            to OutletRating(credibility = 60, bias = 2,  isGovAffiliated = false),
    "mkini.net"                to OutletRating(credibility = 80, bias = -1, isGovAffiliated = false),
    "sebenarnya.my"            to OutletRating(credibility = 85, bias = 0,  isGovAffiliated = true),
    "sinarharian.com.my"       to OutletRating(credibility = 65, bias = 1,  isGovAffiliated = false)
)

// Source 2: MBFC dataset for international outlets (CSV, bundled as asset)
// Download from: https://mediabiasfactcheck.com/methodology/
// ~3,000 outlets rated, bundle as assets/mbfc_ratings.csv

// Source 3: Domain-based heuristics for unknown outlets
fun estimateCredibility(domain: String): OutletRating {
    return when {
        domain.endsWith(".gov.my")  -> OutletRating(credibility = 88, bias = 0, isGovAffiliated = true)
        domain.endsWith(".edu.my")  -> OutletRating(credibility = 85, bias = 0, isGovAffiliated = false)
        domain.endsWith(".gov")     -> OutletRating(credibility = 82, bias = 0, isGovAffiliated = true)
        domain.endsWith(".edu")     -> OutletRating(credibility = 80, bias = 0, isGovAffiliated = false)
        domain.contains("blog")     -> OutletRating(credibility = 30, bias = 0, isGovAffiliated = false)
        domain.contains("wordpress") -> OutletRating(credibility = 25, bias = 0, isGovAffiliated = false)
        else                        -> OutletRating(credibility = 50, bias = 0, isGovAffiliated = false)
    }
}
```

### Rating Repository

```kotlin
data class OutletRating(
    val credibility: Int,           // 0–100
    val bias: Int,                  // -2 to +2
    val isGovAffiliated: Boolean,
    val isSatire: Boolean = false,
    val mbfcCategory: String? = null  // "High", "Mixed", "Low", "Satire" etc.
)

class OutletRatingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Loaded once at startup
    private val mbfcRatings: Map<String, OutletRating> by lazy { loadMbfcCsv() }

    fun getRating(url: String): OutletRating {
        val domain = extractDomain(url)
        return MY_OUTLET_RATINGS[domain]
            ?: mbfcRatings[domain]
            ?: estimateCredibility(domain)
    }

    private fun loadMbfcCsv(): Map<String, OutletRating> {
        return context.assets.open("mbfc_ratings.csv")
            .bufferedReader()
            .readLines()
            .drop(1)  // Skip header
            .mapNotNull { parseMbfcRow(it) }
            .toMap()
    }

    private fun extractDomain(url: String): String {
        return Uri.parse(url).host
            ?.removePrefix("www.")
            ?: url
    }
}
```

### Enriching Sources with Ratings

```kotlin
// Called after retrieval, before LLM analysis
fun List<CorvusSource>.withBiasRatings(
    ratingRepo: OutletRatingRepository
): List<CorvusSource> {
    return map { source ->
        val rating = source.url?.let { ratingRepo.getRating(it) }
        source.copy(outletRating = rating)
    }
}
```

### UI — Source Credibility Indicators

Shown inline on each source card. Compact by default, expandable.

```
┌─────────────────────────────────────────────────────┐
│  Bernama · Mar 2024                        ░░░░░▓▓▓▓│ 75
│  "Economy grew by 3.8% in Q3 per BNM..."   GOV  ◉  │
│                                                      │
│  Free Malaysia Today · Feb 2024            ░░░░▓▓▓▓▓│ 80
│  "Opposition disputes GDP methodology..."   ←  ◉    │
│                                                      │
│  unknownblog.wordpress.com · 2024          ░░▓▓▓▓▓▓▓│ 25
│  "The real numbers they hide from you..."   ?   ◉   │
└─────────────────────────────────────────────────────┘

Legend:  GOV = Government affiliated
         ←   = Left-leaning   → = Right-leaning
         ◉   = Credibility score indicator
```

```kotlin
@Composable
fun SourceCard(source: CorvusSource) {
    val rating = source.outletRating

    Card(modifier = Modifier.padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(source.publisher ?: extractDomain(source.url),
                        style = CorvusTheme.typography.label,
                        color = CorvusTheme.colors.accent)
                    Text(source.title,
                        style = CorvusTheme.typography.body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    source.publishedDate?.let {
                        Text(it,
                            style = CorvusTheme.typography.caption,
                            color = CorvusTheme.colors.textSecondary)
                    }
                }

                // Credibility score column
                rating?.let { CredibilityIndicator(it) }
            }

            // Bias + affiliation tags
            rating?.let {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (it.isGovAffiliated) BiasTag("GOV", CorvusTheme.colors.textSecondary)
                    if (it.isSatire) BiasTag("SATIRE", CorvusTheme.colors.verdictFalse)
                    BiasTag(biasLabel(it.bias), biasColor(it.bias))
                }
            }
        }
    }
}

@Composable
fun CredibilityIndicator(rating: OutletRating) {
    val color = when {
        rating.credibility >= 75 -> CorvusTheme.colors.verdictTrue
        rating.credibility >= 50 -> CorvusTheme.colors.verdictMisleading
        else                     -> CorvusTheme.colors.verdictFalse
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${rating.credibility}",
            style = CorvusTheme.typography.title,
            color = color)
        LinearProgressIndicator(
            progress = { rating.credibility / 100f },
            color = color,
            trackColor = CorvusTheme.colors.border,
            modifier = Modifier.width(40.dp).height(3.dp)
        )
    }
}

fun biasLabel(bias: Int) = when (bias) {
    -2   -> "FAR LEFT"
    -1   -> "LEFT"
     0   -> "CENTER"
     1   -> "RIGHT"
     2   -> "FAR RIGHT"
    else -> "UNKNOWN"
}

fun biasColor(bias: Int) = when (bias) {
    0    -> CorvusTheme.colors.textTertiary
    else -> CorvusTheme.colors.textSecondary
}
```

### Source Quality Gate

Use credibility scores to gate LLM context. Low-credibility sources are still shown to the user but are excluded from the evidence passed to the LLM to prevent them polluting the reasoning.

```kotlin
// Before LLM analysis, filter sources by credibility
val llmSources = sources
    .withBiasRatings(ratingRepo)
    .filter { it.outletRating?.credibility ?: 50 >= 40 }  // Exclude known misinformation sources
    .sortedByDescending { it.outletRating?.credibility ?: 50 }
    .take(7)

// All sources shown in UI regardless (with low-cred warning)
val uiSources = sources.withBiasRatings(ratingRepo)
```

### Overall Source Quality Summary

Show a quick aggregate above the source list.

```
SOURCES OVERVIEW
4 sources · Avg. credibility: 77 · 1 gov-affiliated
```

```kotlin
@Composable
fun SourcesOverviewHeader(sources: List<CorvusSource>) {
    val ratings = sources.mapNotNull { it.outletRating }
    val avgCredibility = ratings.map { it.credibility }.average().roundToInt()
    val govCount = ratings.count { it.isGovAffiliated }

    Text(
        "${sources.size} sources · Avg. credibility: $avgCredibility" +
        if (govCount > 0) " · $govCount gov-affiliated" else "",
        style = CorvusTheme.typography.caption,
        color = CorvusTheme.colors.textSecondary
    )
}
```

### Tasks
- [ ] Build and validate `MY_OUTLET_RATINGS` map (10–15 key Malaysian outlets)
- [ ] Download and bundle MBFC CSV as `assets/mbfc_ratings.csv`
- [ ] Implement `OutletRatingRepository` with CSV loader + domain heuristics
- [ ] Add `outletRating: OutletRating?` field to `CorvusSource`
- [ ] Implement `List<CorvusSource>.withBiasRatings()` extension
- [ ] Apply rating enrichment in all retrieval pipelines before LLM call
- [ ] Implement source quality gate (exclude credibility < 40 from LLM context)
- [ ] Implement `CredibilityIndicator` composable
- [ ] Implement `BiasTag` composable
- [ ] Update `SourceCard` with credibility + bias display
- [ ] Implement `SourcesOverviewHeader`
- [ ] Add "What does this mean?" tooltip on long-press of credibility score
- [ ] Unit test: domain extraction for 20 URL formats
- [ ] Unit test: rating lookup for MY outlets, MBFC outlets, unknown domains

**Estimated duration: 4 days**

---

---

## Combined Roadmap

| Phase | Feature | Duration | Dependencies |
|---|---|---|---|
| 1 | Claim Decomposition | 5 days | Existing pipeline |
| 2 | Viral Claim Detection | 4 days | Room DB (existing) |
| 3 | Source Bias Scoring | 4 days | Existing source models |
| 4 | Confidence Timeline | 6 days | History DB + Phase 3 source ratings |
| **Total** | | **~19 days** | |

Source bias (Phase 3) before confidence timeline (Phase 4) because the timeline uses source credibility tier in its display.

---

## New Dependencies

```kotlin
// build.gradle.kts (app)

// WorkManager — already needed for RSS sync
implementation("androidx.work:work-runtime-ktx:2.9.0")

// No new network dependencies — all new clients use existing Ktor setup
// No new UI dependencies — all composables use existing Compose + Material3
```

---

## New API Keys Required

| Key | Source | Notes |
|---|---|---|
| None | — | Junkipedia, Wikipedia, MBFC CSV, DOSM are all keyless |

---

## Definition of Done

**Claim Decomposition**
- Compound claims correctly split in 90%+ of test cases
- Parallel pipeline executes sub-claims concurrently
- Composite verdict derivation correct for all 6 rule cases
- UI shows expandable sub-claim rows with individual verdicts

**Viral Claim Detection**
- Known viral claims (Junkipedia match ≥ 0.85) short-circuit pipeline and show banner
- Paraphrased viral claims (0.60–0.85) show soft warning without blocking verdict
- Local cache populated after first Junkipedia hit, used on subsequent checks
- No false positives on 10 legitimate, non-viral test claims

**Confidence Timeline**
- Timeline renders for any claim with 2+ Google Fact Check dates in results
- Local history entries surface correctly for re-checked claims
- Trend chip shows correct label for all 5 trend types
- Timeline hidden (not rendered) when fewer than 2 data points

**Source Bias Scoring**
- All 10 hardcoded Malaysian outlets return correct ratings
- MBFC CSV loads successfully and covers major international outlets
- Unknown domains fall back to heuristic scoring without crash
- Low-credibility sources excluded from LLM context but visible in UI
- Credibility scores render correctly on all source cards
