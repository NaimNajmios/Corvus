# Corvus — Feature Enhancement Plan VI
## Strict RAG Citation Enforcement · Source Transparency · Dynamic LLM Fallbacks

> **Scope:** Three reliability and trust upgrades that address the core weaknesses of an LLM-powered fact-checking pipeline: hallucination in summaries, opaque source quality, and fragile single-provider LLM routing. All three are zero-cost, zero new APIs.

---

## Overview

| Feature | Core Problem Solved |
|---|---|
| **RAG Citation Enforcement** | LLM can hallucinate a "fact" that doesn't exist in any retrieved source — user has no way to detect this |
| **Source Transparency** | Sources are shown but users have no signal about their quality, bias, or credibility methodology |
| **Dynamic LLM Fallbacks** | A single provider rate-limit causes the entire check to fail — no resilience, no graceful degradation |

These three features together form the **reliability backbone** of Corvus. Citation enforcement addresses truthfulness, source transparency addresses trust, and LLM fallbacks address availability.

---

---

# Feature 1 — Strict RAG Citation Enforcement

## Problem

The current pipeline retrieves sources via Tavily/PubMed, stuffs their content into the LLM prompt, and returns a verdict with an explanation. The explanation reads as evidentiary (per Enhancement Plan IV's methodology framing) — but there is no mechanism to verify that the LLM's claims are actually traceable to the source documents it was given.

The LLM can:
- Paraphrase a source accurately ✅
- Conflate two sources into a single inaccurate statement ⚠️
- Generate a plausible-sounding fact not present in any source ❌ (hallucination)

All three outputs look identical in the current UI. The user cannot tell them apart.

### The Solution

A **post-processing verification pass** that takes each `GroundedFact` and attempts to find textual evidence for it in the source document it was attributed to. Facts that cannot be grounded in source text are flagged as `LOW_CONFIDENCE`. The LLM cannot self-verify — a deterministic string-matching algorithm does this job.

---

## Verification Architecture

```
LLM returns response JSON
    ↓
[Standard Parser] — extracts verdict, facts, explanation
    ↓
[RAG Verifier] — post-processing pass (NEW)
    │
    ├── For each GroundedFact with source_index:
    │       Extract claim keywords
    │       Search source document text for keyword matches
    │       Calculate coverage score
    │       Assign CitationConfidence level
    │
    ├── For explanation paragraph:
    │       Extract all factual assertions
    │       Attempt to match each to retrieved sources
    │       Calculate overall ExplanationConfidence
    │
    └── Return VerifiedResult with confidence annotations
    ↓
UI renders confidence signals alongside facts and explanation
```

---

## Data Models

```kotlin
// Confidence level for an individual grounded fact
enum class CitationConfidence {
    VERIFIED,       // Strong keyword overlap found in cited source (score >= 0.7)
    PARTIAL,        // Partial overlap found (score 0.4–0.69)
    LOW_CONFIDENCE, // Minimal or no overlap found (score < 0.4)
    UNATTRIBUTED    // source_index was null — general knowledge claim
}

// Per-fact verification result
data class FactVerification(
    val factIndex        : Int,
    val confidence       : CitationConfidence,
    val coverageScore    : Float,           // 0.0–1.0
    val matchedFragment  : String?,         // Exact fragment from source that supports this fact
    val matchedSourceIndex: Int?            // Which source the fragment was found in
)

// Overall explanation verification
data class ExplanationVerification(
    val overallConfidence : ExplanationConfidence,
    val groundedSentences : Int,            // How many sentences trace to sources
    val totalSentences    : Int,
    val groundingRatio    : Float           // groundedSentences / totalSentences
)

enum class ExplanationConfidence {
    WELL_GROUNDED,    // >= 0.75 grounding ratio
    MOSTLY_GROUNDED,  // 0.5–0.74
    PARTIALLY_GROUNDED, // 0.25–0.49
    POORLY_GROUNDED   // < 0.25 — flag entire explanation as low confidence
}

// Extended GroundedFact with verification annotation
data class GroundedFact(
    val statement       : String,
    val sourceIndex     : Int?,
    val isDirectQuote   : Boolean = false,
    val verification    : FactVerification? = null  // NULL until RAG verifier runs
)

// Extended CorvusCheckResult with explanation verification
data class CorvusCheckResult.GeneralResult(
    // ... all existing fields ...
    val explanationVerification: ExplanationVerification? = null  // NEW
)
```

---

## Phase 1.1 — Source Content Store

The verifier needs access to raw source text. Tavily returns `raw_content` in its response — this needs to be stored alongside each source, not discarded after the LLM call.

```kotlin
// Extended CorvusSource — add raw content field
data class CorvusSource(
    val title           : String,
    val url             : String,
    val publisher       : String?,
    val snippet         : String?,
    val publishedDate   : String?,
    val isLocalSource   : Boolean = false,
    val sourceType      : SourceType = SourceType.WEB,
    val credibilityTier : CredibilityTier = CredibilityTier.GENERAL,
    val outletRating    : OutletRating? = null,
    val rawContent      : String? = null   // NEW — full article text from Tavily
)

// In TavilyClient — include raw content in response
suspend fun search(query: String, maxResults: Int = 5): List<CorvusSource> {
    val response = httpClient.post("https://api.tavily.com/search") {
        setBody(TavilySearchRequest(
            query            = query,
            maxResults       = maxResults,
            includeRawContent = true,   // ← Enable this — returns full article text
            searchDepth      = "advanced"
        ))
    }
    return response.results.map { it.toCorvusSource(includeRaw = true) }
}

// In PubMedClient — abstract text is already available
// Map abstract to rawContent field when building CorvusSource
fun PubMedAbstract.toCorvusSource(): CorvusSource {
    return CorvusSource(
        title      = this.title,
        url        = "https://pubmed.ncbi.nlm.nih.gov/${this.pmid}/",
        publisher  = "PubMed / NCBI",
        snippet    = this.abstractText.take(200),
        rawContent = this.abstractText,   // Full abstract for verification
        sourceType = SourceType.ACADEMIC,
        credibilityTier = CredibilityTier.PRIMARY
    )
}
```

---

## Phase 1.2 — Keyword Extractor

Extracts the core verifiable keywords from a fact statement — the terms that must appear in the source if the fact is real.

```kotlin
object FactKeywordExtractor {

    // Stop words — words that appear everywhere, useless for matching
    private val STOP_WORDS = setOf(
        "the", "a", "an", "is", "was", "are", "were", "be", "been",
        "have", "has", "had", "will", "would", "could", "should",
        "in", "on", "at", "to", "of", "and", "or", "but", "with",
        "by", "from", "for", "as", "that", "this", "it", "not",
        // BM stop words
        "yang", "dan", "di", "ke", "dari", "pada", "oleh", "untuk",
        "dengan", "dalam", "adalah", "akan", "telah", "tidak"
    )

    fun extract(statement: String): List<String> {
        return statement
            .lowercase()
            .replace(Regex("[^a-z0-9\\s%.]"), " ")
            .split(Regex("\\s+"))
            .filter { word ->
                word.length >= 3 &&
                word !in STOP_WORDS
            }
            .distinct()
    }

    // Extract numeric values specifically — these are the most verifiable
    fun extractNumerics(statement: String): List<String> {
        return Regex("""(\d+\.?\d*\s*%?|\b\d{4}\b)""")
            .findAll(statement)
            .map { it.value.trim() }
            .toList()
    }
}
```

---

## Phase 1.3 — RAG Verifier Core

```kotlin
// domain/usecase/RagVerifierUseCase.kt

class RagVerifierUseCase @Inject constructor() {

    companion object {
        const val VERIFIED_THRESHOLD       = 0.70f
        const val PARTIAL_THRESHOLD        = 0.40f
        const val WELL_GROUNDED_RATIO      = 0.75f
        const val MOSTLY_GROUNDED_RATIO    = 0.50f
        const val PARTIALLY_GROUNDED_RATIO = 0.25f

        // Max chars of source content to search — balance coverage vs performance
        const val MAX_SOURCE_CONTENT_CHARS = 5000
    }

    // ── Fact verification ────────────────────────────────────────────────

    fun verifyFacts(
        facts   : List<GroundedFact>,
        sources : List<CorvusSource>
    ): List<GroundedFact> {
        return facts.mapIndexed { index, fact ->
            val verification = verifyFact(index, fact, sources)
            fact.copy(verification = verification)
        }
    }

    private fun verifyFact(
        index   : Int,
        fact    : GroundedFact,
        sources : List<CorvusSource>
    ): FactVerification {

        // UNATTRIBUTED — no source index, cannot verify
        if (fact.sourceIndex == null) {
            return FactVerification(
                factIndex         = index,
                confidence        = CitationConfidence.UNATTRIBUTED,
                coverageScore     = 0f,
                matchedFragment   = null,
                matchedSourceIndex = null
            )
        }

        // Get the cited source
        val citedSource = sources.getOrNull(fact.sourceIndex)
            ?: return FactVerification(
                factIndex         = index,
                confidence        = CitationConfidence.LOW_CONFIDENCE,
                coverageScore     = 0f,
                matchedFragment   = null,
                matchedSourceIndex = fact.sourceIndex
            )

        // Search for evidence in cited source first, then all sources
        val (score, fragment) = searchForEvidence(fact.statement, citedSource)

        // If cited source score is low, try all other sources
        val (finalScore, finalFragment, finalSourceIndex) = if (score >= PARTIAL_THRESHOLD) {
            Triple(score, fragment, fact.sourceIndex)
        } else {
            // Try other sources — maybe LLM cited the wrong index
            val bestAlternative = sources
                .mapIndexed { i, s -> Triple(i, searchForEvidence(fact.statement, s), s) }
                .filter { (i, _, _) -> i != fact.sourceIndex }
                .maxByOrNull { (_, result, _) -> result.first }

            val altScore = bestAlternative?.second?.first ?: 0f
            if (altScore > score) {
                Triple(
                    altScore,
                    bestAlternative?.second?.second,
                    bestAlternative?.first
                )
            } else {
                Triple(score, fragment, fact.sourceIndex)
            }
        }

        val confidence = when {
            finalScore >= VERIFIED_THRESHOLD -> CitationConfidence.VERIFIED
            finalScore >= PARTIAL_THRESHOLD  -> CitationConfidence.PARTIAL
            else                             -> CitationConfidence.LOW_CONFIDENCE
        }

        return FactVerification(
            factIndex          = index,
            confidence         = confidence,
            coverageScore      = finalScore,
            matchedFragment    = finalFragment,
            matchedSourceIndex = finalSourceIndex
        )
    }

    // ── Evidence search ──────────────────────────────────────────────────

    private fun searchForEvidence(
        statement : String,
        source    : CorvusSource
    ): Pair<Float, String?> {

        // Build searchable text from source — prefer rawContent, fall back to snippet
        val sourceText = (source.rawContent ?: source.snippet ?: "")
            .take(MAX_SOURCE_CONTENT_CHARS)
            .lowercase()

        if (sourceText.isBlank()) return 0f to null

        val keywords  = FactKeywordExtractor.extract(statement)
        val numerics  = FactKeywordExtractor.extractNumerics(statement)

        if (keywords.isEmpty()) return 0f to null

        // 1. Numeric matching — high weight (numbers are most verifiable)
        val numericScore = if (numerics.isNotEmpty()) {
            val matched = numerics.count { num -> sourceText.contains(num) }
            (matched.toFloat() / numerics.size) * 0.5f  // Numerics weight 50%
        } else 0f

        // 2. Keyword matching
        val keywordScore = run {
            val matched = keywords.count { kw -> sourceText.contains(kw) }
            (matched.toFloat() / keywords.size) * 0.5f  // Keywords weight 50%
        }

        val totalScore = numericScore + keywordScore

        // 3. Extract the best matching fragment for UI display
        val fragment = if (totalScore >= PARTIAL_THRESHOLD) {
            extractBestFragment(keywords, sourceText)
        } else null

        return totalScore to fragment
    }

    // Find the sentence in the source that contains the most keywords
    private fun extractBestFragment(
        keywords   : List<String>,
        sourceText : String
    ): String? {
        val sentences = sourceText.split(Regex("[.!?]"))

        return sentences
            .map { sentence ->
                val matchCount = keywords.count { kw -> sentence.contains(kw) }
                sentence.trim() to matchCount
            }
            .filter { (_, count) -> count >= 2 }
            .maxByOrNull { (_, count) -> count }
            ?.first
            ?.take(200)   // Cap fragment length for UI
            ?.let { "\"...${it.trim()}...\"" }
    }

    // ── Explanation verification ─────────────────────────────────────────

    fun verifyExplanation(
        explanation : String,
        sources     : List<CorvusSource>
    ): ExplanationVerification {

        // Split explanation into individual sentences
        val sentences = explanation
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length > 20 }   // Skip very short sentences

        if (sentences.isEmpty()) {
            return ExplanationVerification(
                overallConfidence = ExplanationConfidence.POORLY_GROUNDED,
                groundedSentences = 0,
                totalSentences    = 0,
                groundingRatio    = 0f
            )
        }

        // Check each sentence against all sources
        val groundedCount = sentences.count { sentence ->
            val bestScore = sources.maxOfOrNull { source ->
                searchForEvidence(sentence, source).first
            } ?: 0f
            bestScore >= PARTIAL_THRESHOLD
        }

        val ratio = groundedCount.toFloat() / sentences.size

        val confidence = when {
            ratio >= WELL_GROUNDED_RATIO      -> ExplanationConfidence.WELL_GROUNDED
            ratio >= MOSTLY_GROUNDED_RATIO    -> ExplanationConfidence.MOSTLY_GROUNDED
            ratio >= PARTIALLY_GROUNDED_RATIO -> ExplanationConfidence.PARTIALLY_GROUNDED
            else                              -> ExplanationConfidence.POORLY_GROUNDED
        }

        return ExplanationVerification(
            overallConfidence = confidence,
            groundedSentences = groundedCount,
            totalSentences    = sentences.size,
            groundingRatio    = ratio
        )
    }
}
```

---

## Phase 1.4 — Pipeline Integration

```kotlin
// In CorvusFactCheckUseCase — run verifier after LLM response parsed

private suspend fun runMainPipeline(raw: String): CorvusCheckResult {
    val classified = claimClassifier.classify(raw)
    val sources    = retrievalLayer.retrieve(raw, classified)
    val llmResult  = llmRouter.analyze(raw, sources, classified)

    // ── RAG Verification pass ────────────────────────────────────────
    val verifiedFacts = ragVerifier.verifyFacts(llmResult.keyFacts, sources)
    val explanationVerification = ragVerifier.verifyExplanation(
        llmResult.explanation,
        sources
    )

    // Escalate verdict confidence if explanation is poorly grounded
    val adjustedConfidence = when (explanationVerification.overallConfidence) {
        ExplanationConfidence.POORLY_GROUNDED     -> llmResult.confidence * 0.6f
        ExplanationConfidence.PARTIALLY_GROUNDED  -> llmResult.confidence * 0.8f
        else                                      -> llmResult.confidence
    }
    // ────────────────────────────────────────────────────────────────

    return llmResult.copy(
        keyFacts                = verifiedFacts,
        confidence              = adjustedConfidence,
        explanationVerification = explanationVerification
    )
}
```

---

## Phase 1.5 — UI

### Grounded Fact Row — Citation Confidence Badge

```kotlin
// Updated GroundedFactRow to show verification state

@Composable
fun GroundedFactRow(
    fact         : GroundedFact,
    source       : CorvusSource?,
    onCitationClick: () -> Unit
) {
    Column {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.Top
        ) {
            // Left bar — colour changes with citation confidence
            val barColor = fact.verification?.confidence?.barColor()
                ?: CorvusTheme.colors.border

            Box(
                modifier = Modifier
                    .width(2.dp)
                    .heightIn(min = 20.dp)
                    .background(barColor)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = if (fact.isDirectQuote) "\"${fact.statement}\"" else fact.statement,
                    style = if (fact.isDirectQuote)
                        CorvusTheme.typography.body.copy(fontStyle = FontStyle.Italic)
                    else
                        CorvusTheme.typography.body
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // Citation badge
                    if (fact.sourceIndex != null && source != null) {
                        CitationBadge(
                            index         = fact.sourceIndex,
                            publisherName = source.publisher ?: "Source ${fact.sourceIndex + 1}",
                            onClick       = onCitationClick
                        )
                    } else {
                        Text(
                            "General knowledge",
                            style     = CorvusTheme.typography.caption,
                            color     = CorvusTheme.colors.textTertiary,
                            fontStyle = FontStyle.Italic
                        )
                    }

                    // Citation confidence badge — NEW
                    fact.verification?.let { v ->
                        CitationConfidenceBadge(v.confidence)
                    }
                }

                // Matched fragment — shown for VERIFIED and PARTIAL
                fact.verification?.matchedFragment?.let { fragment ->
                    Spacer(Modifier.height(6.dp))
                    MatchedFragmentCard(fragment)
                }
            }
        }
    }
}

@Composable
fun CitationConfidenceBadge(confidence: CitationConfidence) {
    val (label, color) = when (confidence) {
        CitationConfidence.VERIFIED      -> "Verified in source"   to CorvusTheme.colors.verdictTrue
        CitationConfidence.PARTIAL       -> "Partially matched"    to CorvusTheme.colors.verdictMisleading
        CitationConfidence.LOW_CONFIDENCE -> "Low confidence"      to CorvusTheme.colors.verdictFalse
        CitationConfidence.UNATTRIBUTED  -> "General knowledge"    to CorvusTheme.colors.textTertiary
    }

    Surface(
        color  = color.copy(alpha = 0.12f),
        shape  = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text      = label.uppercase(),
            modifier  = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            style     = CorvusTheme.typography.labelSmall,
            color     = color,
            fontFamily = IbmPlexMono,
            letterSpacing = 0.3.sp
        )
    }
}

fun CitationConfidence.barColor(): Color = when (this) {
    CitationConfidence.VERIFIED       -> CorvusTheme.colors.verdictTrue
    CitationConfidence.PARTIAL        -> CorvusTheme.colors.verdictMisleading
    CitationConfidence.LOW_CONFIDENCE -> CorvusTheme.colors.verdictFalse
    CitationConfidence.UNATTRIBUTED   -> CorvusTheme.colors.border
}
```

### Matched Fragment Card

Shows the exact text from the source that backs the fact — the smoking-gun evidence:

```kotlin
@Composable
fun MatchedFragmentCard(fragment: String) {
    Surface(
        color  = CorvusTheme.colors.surface,
        shape  = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, CorvusTheme.colors.border)
    ) {
        Row(
            modifier              = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Vertical quote mark accent
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(CorvusTheme.colors.accent.copy(alpha = 0.5f))
            )
            Text(
                text      = fragment,
                style     = CorvusTheme.typography.caption.copy(fontStyle = FontStyle.Italic),
                color     = CorvusTheme.colors.textSecondary
            )
        }
    }
}
```

### Explanation Confidence Banner

Shown at the top of the explanation card when `overallConfidence` is `PARTIALLY_GROUNDED` or `POORLY_GROUNDED`:

```kotlin
@Composable
fun ExplanationConfidenceBanner(verification: ExplanationVerification) {
    if (verification.overallConfidence == ExplanationConfidence.WELL_GROUNDED ||
        verification.overallConfidence == ExplanationConfidence.MOSTLY_GROUNDED) return

    val isPoor = verification.overallConfidence == ExplanationConfidence.POORLY_GROUNDED
    val color  = if (isPoor) CorvusTheme.colors.verdictFalse
                 else CorvusTheme.colors.verdictMisleading

    Surface(
        color  = color.copy(alpha = 0.08f),
        shape  = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier              = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(
                CorvusIcons.AlertTriangle,
                modifier = Modifier.size(14.dp),
                tint     = color
            )
            Column {
                Text(
                    text      = if (isPoor) "LOW SOURCE GROUNDING" else "PARTIAL SOURCE GROUNDING",
                    style     = CorvusTheme.typography.labelSmall,
                    color     = color,
                    fontFamily = IbmPlexMono
                )
                Text(
                    text  = "${verification.groundedSentences} of ${verification.totalSentences} " +
                            "sentences traced to retrieved sources. " +
                            "Treat this explanation with caution.",
                    style = CorvusTheme.typography.caption,
                    color = CorvusTheme.colors.textSecondary
                )
            }
        }
    }
}
```

### Methodology Card — RAG Stats Addition

```kotlin
// Add to MethodologyCard expanded section

MethodologyStatRow(
    label = "Citation grounding",
    value = "${(verification.groundingRatio * 100).roundToInt()}% of facts traced to sources"
)
MethodologyStatRow(
    label = "Low-confidence facts",
    value = "${facts.count { it.verification?.confidence == CitationConfidence.LOW_CONFIDENCE }}"
)
```

---

## Feature 1 Tasks

- [ ] Add `rawContent` field to `CorvusSource`
- [ ] Enable `include_raw_content: true` in `TavilyClient`
- [ ] Map PubMed abstract text to `rawContent` in `PubMedClient`
- [ ] Add `CitationConfidence` enum
- [ ] Add `FactVerification` data class
- [ ] Add `ExplanationVerification` data class and `ExplanationConfidence` enum
- [ ] Add `verification` field to `GroundedFact`
- [ ] Add `explanationVerification` field to `CorvusCheckResult.GeneralResult`
- [ ] Implement `FactKeywordExtractor` object
- [ ] Implement `RagVerifierUseCase` with keyword + numeric matching
- [ ] Implement `extractBestFragment()` — sentence-level matching
- [ ] Wire `RagVerifierUseCase` into `CorvusFactCheckUseCase` post-LLM
- [ ] Implement confidence-based `adjustedConfidence` scaling
- [ ] Implement `CitationConfidenceBadge` composable
- [ ] Implement `MatchedFragmentCard` composable
- [ ] Update `GroundedFactRow` — left bar colour + confidence badge + fragment
- [ ] Implement `ExplanationConfidenceBanner` composable
- [ ] Add RAG stats to `MethodologyCard`
- [ ] Unit test: `FactKeywordExtractor` — stop word removal, numeric extraction
- [ ] Unit test: `searchForEvidence` — strong match, partial match, no match
- [ ] Unit test: `verifyExplanation` — all 4 confidence levels
- [ ] Unit test: confidence scaling in pipeline
- [ ] Integration test: bleach/covid claim → LOW_CONFIDENCE on hallucinated facts
- [ ] Integration test: DOSM GDP claim → VERIFIED facts with matched fragments

**Estimated duration: 7 days**

---

---

# Feature 2 — Source Transparency on SourceCard

## Problem

Sources are currently displayed as a list of titles and publisher names. The `OutletRatingRepository` and MBFC ratings exist in the data layer but are invisible to the user. A source from a highly biased outlet looks identical to one from a primary government database. Users cannot assess the quality of the evidence they are looking at.

Source transparency is one of the highest-leverage trust signals Corvus can surface — it does not change the verdict, but it teaches users to think critically about evidence quality.

---

## Rating Display Strategy

Three tiers of display based on what is known about the outlet:

```
Tier 1 — MBFC / MY hardcoded rating exists
    → Full badge: credibility score + bias indicator + MBFC category label

Tier 2 — Domain heuristic only (gov, edu, blog etc.)
    → Partial badge: domain-inferred credibility + affiliation tag

Tier 3 — Completely unknown domain
    → Minimal badge: "Unknown source" + credibility score 50 (neutral default)
```

---

## Phase 2.1 — OutletRating Model Extension

```kotlin
// Extended OutletRating — add MBFC category and display metadata

data class OutletRating(
    val credibility      : Int,              // 0–100
    val bias             : Int,              // -2 to +2
    val isGovAffiliated  : Boolean,
    val isSatire         : Boolean = false,
    val mbfcCategory     : MbfcCategory? = null,
    val ratingSource     : RatingSource = RatingSource.HEURISTIC
)

// MBFC's own category labels — map exactly to their published categories
enum class MbfcCategory(val displayLabel: String, val credibilityHint: String) {
    VERY_HIGH_FACTUAL  ("Very High Factual",  "Consistently accurate reporting"),
    HIGH_FACTUAL       ("High Factual",        "Generally accurate reporting"),
    MOSTLY_FACTUAL     ("Mostly Factual",      "Mostly accurate, minor errors"),
    MIXED_FACTUAL      ("Mixed Factual",       "Inconsistent accuracy"),
    LOW_FACTUAL        ("Low Factual",         "Frequent factual errors"),
    VERY_LOW_FACTUAL   ("Very Low Factual",   "Unreliable, poor sourcing"),
    SATIRE             ("Satire",              "Intentionally satirical content"),
    CONSPIRACY         ("Conspiracy / Pseudoscience", "Promotes conspiracy theories"),
    PRO_SCIENCE        ("Pro-Science",         "Peer-reviewed science focus"),
    SCIENCE            ("Science",             "Scientific focus"),
    GOVERNMENT        ("Government",          "Official government source")
}

enum class RatingSource {
    MBFC_CSV,      // From bundled mbfc_ratings.csv
    MY_HARDCODED,  // From MY_OUTLET_RATINGS hardcoded map
    HEURISTIC,     // Domain-based inference
    UNKNOWN
}

// Computed properties for UI
fun OutletRating.credibilityLabel(): String = when {
    credibility >= 90 -> "Highly Factual"
    credibility >= 75 -> "Generally Factual"
    credibility >= 60 -> "Mostly Factual"
    credibility >= 45 -> "Mixed"
    credibility >= 30 -> "Low Factual"
    else              -> "Unreliable"
}

fun OutletRating.biasLabel(): String = when (bias) {
    -2 -> "Far Left"
    -1 -> "Left-Center"
     0 -> "Center"
     1 -> "Right-Center"
     2 -> "Far Right"
    else -> "Unknown"
}

fun OutletRating.biasIndicatorChar(): String = when (bias) {
    -2, -1 -> "←"
     1,  2 -> "→"
    else   -> "◉"
}
```

---

## Phase 2.2 — MBFC CSV Parser

```kotlin
// data/local/OutletRatingRepository.kt — full implementation

@Singleton
class OutletRatingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Loaded once at startup via lazy — thread-safe in Kotlin
    private val mbfcRatings: Map<String, OutletRating> by lazy {
        loadMbfcCsv()
    }

    fun getRating(url: String): OutletRating {
        val domain = extractDomain(url)
        return MY_OUTLET_RATINGS[domain]
            ?: mbfcRatings[domain]
            ?: inferFromDomain(domain)
    }

    fun getRatingOrNull(url: String): OutletRating? {
        val domain = extractDomain(url)
        return MY_OUTLET_RATINGS[domain] ?: mbfcRatings[domain]
        // Returns null for unknown domains — caller decides whether to show badge
    }

    // ── MBFC CSV loading ─────────────────────────────────────────────────
    // Expected CSV columns:
    // name, url, bias, factual_reporting, country, media_type, credibility
    // Example row:
    // Reuters,reuters.com,center,very high,international,news,95

    private fun loadMbfcCsv(): Map<String, OutletRating> {
        return try {
            context.assets.open("mbfc_ratings.csv")
                .bufferedReader()
                .readLines()
                .drop(1)                    // Skip header row
                .mapNotNull { parseMbfcRow(it) }
                .toMap()
        } catch (e: Exception) {
            emptyMap()                      // Fail gracefully — app works without CSV
        }
    }

    private fun parseMbfcRow(row: String): Pair<String, OutletRating>? {
        return try {
            val cols = row.split(",").map { it.trim().lowercase() }
            if (cols.size < 6) return null

            val domain      = cols[1].removePrefix("www.")
            val biasStr     = cols[2]
            val factualStr  = cols[3]
            val credibility = cols.getOrNull(6)?.toIntOrNull()
                ?: mapFactualToCredibility(factualStr)

            val bias = mapBiasStringToInt(biasStr)
            val mbfcCat = mapFactualToMbfcCategory(factualStr)

            domain to OutletRating(
                credibility    = credibility,
                bias           = bias,
                isGovAffiliated = biasStr.contains("government") ||
                                  domain.endsWith(".gov") ||
                                  domain.endsWith(".gov.my"),
                isSatire       = biasStr.contains("satire"),
                mbfcCategory   = mbfcCat,
                ratingSource   = RatingSource.MBFC_CSV
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun mapBiasStringToInt(bias: String): Int = when {
        bias.contains("extreme left") || bias.contains("far left") -> -2
        bias.contains("left")         -> -1
        bias.contains("center")       -> 0
        bias.contains("right") && !bias.contains("far") -> 1
        bias.contains("far right") || bias.contains("extreme right") -> 2
        else -> 0
    }

    private fun mapFactualToCredibility(factual: String): Int = when {
        factual.contains("very high") -> 90
        factual.contains("high")      -> 78
        factual.contains("mostly")    -> 65
        factual.contains("mixed")     -> 48
        factual.contains("low")       -> 28
        factual.contains("very low")  -> 12
        factual.contains("satire")    -> 50
        else                          -> 50
    }

    private fun mapFactualToMbfcCategory(factual: String): MbfcCategory? = when {
        factual.contains("very high") -> MbfcCategory.VERY_HIGH_FACTUAL
        factual.contains("high")      -> MbfcCategory.HIGH_FACTUAL
        factual.contains("mostly")    -> MbfcCategory.MOSTLY_FACTUAL
        factual.contains("mixed")     -> MbfcCategory.MIXED_FACTUAL
        factual.contains("low") && !factual.contains("very") -> MbfcCategory.LOW_FACTUAL
        factual.contains("very low")  -> MbfcCategory.VERY_LOW_FACTUAL
        factual.contains("satire")    -> MbfcCategory.SATIRE
        else                          -> null
    }

    // ── Domain heuristics ────────────────────────────────────────────────

    private fun inferFromDomain(domain: String): OutletRating = when {
        domain.endsWith(".gov.my")    ->
            OutletRating(90, 0, isGovAffiliated = true,
                mbfcCategory = MbfcCategory.GOVERNMENT,
                ratingSource = RatingSource.HEURISTIC)
        domain.endsWith(".gov")       ->
            OutletRating(85, 0, isGovAffiliated = true,
                mbfcCategory = MbfcCategory.GOVERNMENT,
                ratingSource = RatingSource.HEURISTIC)
        domain.endsWith(".edu.my") ||
        domain.endsWith(".edu")       ->
            OutletRating(82, 0, isGovAffiliated = false,
                ratingSource = RatingSource.HEURISTIC)
        domain.contains("blog") ||
        domain.contains("wordpress") ||
        domain.contains("blogger") ||
        domain.contains("tumblr")     ->
            OutletRating(25, 0, isGovAffiliated = false,
                ratingSource = RatingSource.HEURISTIC)
        domain.contains("facebook") ||
        domain.contains("twitter") ||
        domain.contains("tiktok")     ->
            OutletRating(20, 0, isGovAffiliated = false,
                ratingSource = RatingSource.HEURISTIC)
        else                          ->
            OutletRating(50, 0, isGovAffiliated = false,
                ratingSource = RatingSource.UNKNOWN)
    }

    private fun extractDomain(url: String): String =
        Uri.parse(url).host?.removePrefix("www.") ?: url.lowercase().take(50)
}
```

---

## Phase 2.3 — SourceCard UI Redesign

```kotlin
// ui/components/SourceCard.kt — full redesign

@Composable
fun SourceCard(
    source      : CorvusSource,
    numberLabel : Int,
    modifier    : Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val rating = source.outletRating

    Card(
        modifier = modifier.fillMaxWidth(),
        border   = BorderStroke(
            width = if (rating?.credibility ?: 50 < 40) 1.5.dp else 1.dp,
            color = when {
                rating?.isSatire == true           -> CorvusTheme.colors.verdictMisleading
                (rating?.credibility ?: 50) < 40   -> CorvusTheme.colors.verdictFalse.copy(alpha = 0.5f)
                (rating?.credibility ?: 50) >= 80  -> CorvusTheme.colors.verdictTrue.copy(alpha = 0.3f)
                else                               -> CorvusTheme.colors.border
            }
        ),
        shape  = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = CorvusTheme.colors.surface)
    ) {
        Column {
            // ── Main row ─────────────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.Top
            ) {
                // Source number badge
                SourceNumberBadge(numberLabel)

                Column(modifier = Modifier.weight(1f)) {

                    // Publisher + credibility label on same line
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text      = source.publisher ?: extractDomain(source.url),
                            style     = CorvusTheme.typography.labelSmall,
                            color     = CorvusTheme.colors.accent,
                            fontFamily = IbmPlexMono,
                            fontWeight = FontWeight.Medium
                        )

                        // Compact credibility label — always shown
                        rating?.let { r ->
                            CompactCredibilityLabel(r)
                        }
                    }

                    Spacer(Modifier.height(3.dp))

                    // Article title
                    Text(
                        text     = source.title,
                        style    = CorvusTheme.typography.bodySmall,
                        color    = CorvusTheme.colors.textPrimary,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Date + source type
                    Row(
                        modifier              = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        source.publishedDate?.let { date ->
                            Text(
                                date,
                                style = CorvusTheme.typography.caption,
                                color = CorvusTheme.colors.textTertiary,
                                fontFamily = IbmPlexMono
                            )
                        }
                        SourceTypeChip(source.sourceType)
                    }
                }
            }

            // ── Expanded: full ratings breakdown ─────────────────────────
            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(color = CorvusTheme.colors.border)
                    rating?.let { r ->
                        SourceRatingBreakdown(
                            rating    = r,
                            sourceUrl = source.url,
                            modifier  = Modifier.padding(12.dp)
                        )
                    } ?: run {
                        // No rating available
                        Text(
                            "No rating data available for this source.",
                            modifier  = Modifier.padding(12.dp),
                            style     = CorvusTheme.typography.caption,
                            color     = CorvusTheme.colors.textTertiary,
                            fontStyle = FontStyle.Italic
                        )
                    }

                    // Open in browser button
                    source.url?.let { url ->
                        HorizontalDivider(color = CorvusTheme.colors.border)
                        TextButton(
                            onClick        = { uriHandler.openUri(url) },
                            modifier       = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text(
                                "Open source →",
                                style     = CorvusTheme.typography.labelSmall,
                                color     = CorvusTheme.colors.accent,
                                fontFamily = IbmPlexMono
                            )
                        }
                    }
                }
            }
        }
    }
}

// Compact inline credibility label — shown in collapsed state
@Composable
fun CompactCredibilityLabel(rating: OutletRating) {
    val (label, color) = when {
        rating.isSatire                -> "Satire"        to CorvusTheme.colors.verdictMisleading
        rating.credibility >= 80       -> "High Factual"  to CorvusTheme.colors.verdictTrue
        rating.credibility >= 60       -> "Mostly Factual" to CorvusTheme.colors.verdictTrue.copy(alpha = 0.7f)
        rating.credibility >= 45       -> "Mixed"         to CorvusTheme.colors.verdictMisleading
        rating.credibility < 45        -> "Low Factual"   to CorvusTheme.colors.verdictFalse
        else                           -> "Unknown"       to CorvusTheme.colors.textTertiary
    }

    Text(
        text      = label,
        style     = CorvusTheme.typography.caption,
        color     = color,
        fontFamily = IbmPlexMono
    )
}

// Full breakdown panel — shown when card is expanded
@Composable
fun SourceRatingBreakdown(
    rating    : OutletRating,
    sourceUrl : String?,
    modifier  : Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // Credibility score bar
        CredibilityScoreRow(rating.credibility)

        // Bias indicator
        if (!rating.isGovAffiliated && !rating.isSatire) {
            BiasIndicatorRow(rating.bias)
        }

        // Special tags
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (rating.isGovAffiliated) {
                RatingTag("GOV AFFILIATED", CorvusTheme.colors.textSecondary)
            }
            if (rating.isSatire) {
                RatingTag("SATIRE", CorvusTheme.colors.verdictMisleading)
            }
            rating.mbfcCategory?.let { cat ->
                RatingTag(cat.displayLabel.uppercase(), CorvusTheme.colors.textTertiary)
            }
        }

        // MBFC hint
        rating.mbfcCategory?.let { cat ->
            Text(
                cat.credibilityHint,
                style     = CorvusTheme.typography.caption,
                color     = CorvusTheme.colors.textSecondary,
                fontStyle = FontStyle.Italic
            )
        }

        // Rating source disclosure
        Text(
            when (rating.ratingSource) {
                RatingSource.MBFC_CSV      -> "Rating: Media Bias/Fact Check"
                RatingSource.MY_HARDCODED  -> "Rating: Corvus Malaysian Media Index"
                RatingSource.HEURISTIC     -> "Rating: Inferred from domain type"
                RatingSource.UNKNOWN       -> "Rating: Unknown"
            },
            style     = CorvusTheme.typography.caption,
            color     = CorvusTheme.colors.textTertiary,
            fontFamily = IbmPlexMono
        )
    }
}

@Composable
fun CredibilityScoreRow(credibility: Int) {
    val color = when {
        credibility >= 75 -> CorvusTheme.colors.verdictTrue
        credibility >= 50 -> CorvusTheme.colors.verdictMisleading
        else              -> CorvusTheme.colors.verdictFalse
    }
    Column {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "CREDIBILITY",
                style     = CorvusTheme.typography.caption,
                color     = CorvusTheme.colors.textSecondary,
                fontFamily = IbmPlexMono
            )
            Text(
                "$credibility / 100",
                style     = CorvusTheme.typography.caption,
                color     = color,
                fontFamily = IbmPlexMono,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress          = { credibility / 100f },
            modifier          = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color             = color,
            trackColor        = CorvusTheme.colors.border
        )
    }
}

@Composable
fun BiasIndicatorRow(bias: Int) {
    val biasLabel = when (bias) {
        -2 -> "Far Left"
        -1 -> "Left-Center"
         0 -> "Center"
         1 -> "Right-Center"
         2 -> "Far Right"
        else -> "Unknown"
    }
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            "POLITICAL LEAN",
            style     = CorvusTheme.typography.caption,
            color     = CorvusTheme.colors.textSecondary,
            fontFamily = IbmPlexMono
        )
        Text(
            biasLabel,
            style     = CorvusTheme.typography.caption,
            color     = if (bias == 0) CorvusTheme.colors.textSecondary
                        else CorvusTheme.colors.textPrimary,
            fontFamily = IbmPlexMono
        )
    }
}

@Composable
fun RatingTag(label: String, color: Color) {
    Surface(
        color  = color.copy(alpha = 0.1f),
        shape  = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text      = label,
            modifier  = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            style     = CorvusTheme.typography.caption,
            color     = color,
            fontFamily = IbmPlexMono,
            letterSpacing = 0.3.sp
        )
    }
}
```

---

## Feature 2 Tasks

- [ ] Extend `OutletRating` with `MbfcCategory` enum and `RatingSource` enum
- [ ] Add `credibilityLabel()` and `biasLabel()` computed properties
- [ ] Implement full `OutletRatingRepository` with CSV parser
- [ ] Map all MBFC CSV bias strings to `Int` via `mapBiasStringToInt()`
- [ ] Map all MBFC factual reporting strings to `MbfcCategory`
- [ ] Validate `mbfc_ratings.csv` asset parses without errors
- [ ] Update `MY_OUTLET_RATINGS` — add `mbfcCategory` and `ratingSource` fields
- [ ] Implement `CompactCredibilityLabel` composable
- [ ] Implement `SourceRatingBreakdown` composable (expanded state)
- [ ] Implement `CredibilityScoreRow` composable
- [ ] Implement `BiasIndicatorRow` composable
- [ ] Implement `RatingTag` composable
- [ ] Redesign `SourceCard` with expand/collapse and ratings
- [ ] Update `SourcesSectionHeader` — show avg credibility summary
- [ ] Unit test: MBFC CSV parser — 20 known outlets verify correct rating
- [ ] Unit test: domain heuristics — .gov.my, .edu, blog, social domains
- [ ] Unit test: `credibilityLabel()` for all boundary values
- [ ] UI test: card expands to show rating breakdown on tap
- [ ] UI test: satire tag renders correctly for satire outlets

**Estimated duration: 5 days**

---

---

# Feature 3 — Dynamic LLM Fallbacks in LlmRouter

## Problem

The current `LlmRouter` has a list of providers but no battle-hardened retry and fallback logic. In practice:

- **Groq** hits rate limits at ~10 RPM on the free tier — fails silently
- **Gemini** has occasional 503 overload errors during peak hours
- **Cerebras** has the fastest inference but smallest context window
- **OpenRouter** free models have inconsistent availability

Without a robust fallback cascade, a single provider hiccup fails the entire check — the user sees an error screen for a problem that could have been silently recovered.

---

## Failure Mode Classification

Not all failures are equal. The router needs to handle them differently:

```kotlin
sealed class LlmFailure {
    // Retry same provider after delay — transient issues
    data class RateLimit(val retryAfterMs: Long) : LlmFailure()
    object Timeout                               : LlmFailure()
    object ServiceUnavailable                    : LlmFailure()

    // Move to next provider immediately — provider-specific issues
    object ContextLengthExceeded                 : LlmFailure()
    object ModelUnavailable                      : LlmFailure()

    // Non-recoverable — surface error to user
    data class AuthenticationFailed(val provider: LlmProvider) : LlmFailure()
    data class InvalidResponse(val raw: String)                 : LlmFailure()
}

fun classifyFailure(e: Exception, responseCode: Int?): LlmFailure = when {
    responseCode == 429                          -> LlmFailure.RateLimit(retryAfterMs = 2000L)
    responseCode == 503 || responseCode == 502   -> LlmFailure.ServiceUnavailable
    responseCode == 401 || responseCode == 403   -> LlmFailure.AuthenticationFailed(LlmProvider.UNKNOWN)
    e is TimeoutCancellationException            -> LlmFailure.Timeout
    responseCode == 400 && e.message?.contains("context") == true -> LlmFailure.ContextLengthExceeded
    else                                         -> LlmFailure.ServiceUnavailable
}
```

---

## Phase 3.1 — Provider Configuration

```kotlin
// domain/model/LlmProviderConfig.kt

data class LlmProviderConfig(
    val provider      : LlmProvider,
    val model         : String,
    val maxContextTokens: Int,
    val timeoutMs     : Long,
    val maxRetries    : Int,
    val retryDelayMs  : Long,
    val priority      : Int             // Lower = higher priority
)

val DEFAULT_PROVIDER_CONFIGS = listOf(
    LlmProviderConfig(
        provider          = LlmProvider.GEMINI,
        model             = "gemini-2.0-flash",
        maxContextTokens  = 1_000_000,
        timeoutMs         = 15_000L,
        maxRetries        = 2,
        retryDelayMs      = 1_000L,
        priority          = 1
    ),
    LlmProviderConfig(
        provider          = LlmProvider.GROQ,
        model             = "llama-3.3-70b-versatile",
        maxContextTokens  = 128_000,
        timeoutMs         = 8_000L,     // Fast — short timeout
        maxRetries        = 1,          // Low retry — moves fast to fallback
        retryDelayMs      = 500L,
        priority          = 2
    ),
    LlmProviderConfig(
        provider          = LlmProvider.CEREBRAS,
        model             = "llama-3.3-70b",
        maxContextTokens  = 8_192,      // Smaller context — watch for truncation
        timeoutMs         = 6_000L,
        maxRetries        = 1,
        retryDelayMs      = 500L,
        priority          = 3
    ),
    LlmProviderConfig(
        provider          = LlmProvider.OPENROUTER,
        model             = "meta-llama/llama-3.3-70b-instruct:free",
        maxContextTokens  = 131_072,
        timeoutMs         = 20_000L,    // Longer timeout — free tier is slower
        maxRetries        = 1,
        retryDelayMs      = 1_000L,
        priority          = 4
    )
)
```

---

## Phase 3.2 — Provider Health Tracker

Track provider health in-memory during the app session. Failed providers are temporarily demoted:

```kotlin
// domain/usecase/LlmProviderHealthTracker.kt

class LlmProviderHealthTracker @Inject constructor() {

    private data class ProviderHealth(
        val failCount       : Int = 0,
        val lastFailureMs   : Long = 0L,
        val consecutiveFails: Int = 0,
        val rateLimitUntilMs: Long = 0L
    )

    private val healthMap = ConcurrentHashMap<LlmProvider, ProviderHealth>()

    // How long to deprioritise a provider after failures
    companion object {
        const val COOL_DOWN_MS          = 60_000L   // 1 minute cool-down after 3 fails
        const val RATE_LIMIT_BUFFER_MS  = 5_000L    // Extra buffer after rate limit window
    }

    fun isAvailable(provider: LlmProvider): Boolean {
        val health = healthMap[provider] ?: return true
        val now = System.currentTimeMillis()

        // Rate limited — check if window has expired
        if (health.rateLimitUntilMs > now) return false

        // Cool-down after consecutive failures
        if (health.consecutiveFails >= 3 &&
            (now - health.lastFailureMs) < COOL_DOWN_MS) return false

        return true
    }

    fun recordSuccess(provider: LlmProvider) {
        healthMap[provider] = ProviderHealth()   // Reset on success
    }

    fun recordFailure(provider: LlmProvider, failure: LlmFailure) {
        val current = healthMap[provider] ?: ProviderHealth()
        val now = System.currentTimeMillis()

        healthMap[provider] = when (failure) {
            is LlmFailure.RateLimit -> current.copy(
                failCount        = current.failCount + 1,
                lastFailureMs    = now,
                consecutiveFails = current.consecutiveFails + 1,
                rateLimitUntilMs = now + failure.retryAfterMs + RATE_LIMIT_BUFFER_MS
            )
            else -> current.copy(
                failCount        = current.failCount + 1,
                lastFailureMs    = now,
                consecutiveFails = current.consecutiveFails + 1
            )
        }
    }

    fun getHealthReport(): Map<LlmProvider, String> {
        return LlmProvider.values().associateWith { provider ->
            when {
                !isAvailable(provider) -> "Unavailable"
                (healthMap[provider]?.consecutiveFails ?: 0) > 0 -> "Degraded"
                else -> "Healthy"
            }
        }
    }
}
```

---

## Phase 3.3 — LlmRouter with Full Fallback Cascade

```kotlin
// domain/usecase/LlmRouter.kt

class LlmRouter @Inject constructor(
    private val gemini      : GeminiProvider,
    private val groq        : GroqProvider,
    private val cerebras    : CerebrasProvider,
    private val openRouter  : OpenRouterProvider,
    private val healthTracker: LlmProviderHealthTracker,
    private val prefs       : UserPreferences
) {
    // Build ordered provider list — preferred first, then by priority
    private fun buildProviderOrder(): List<Pair<LlmProvider, LlmProviderConfig>> {
        val configs = DEFAULT_PROVIDER_CONFIGS.sortedWith(
            compareBy<LlmProviderConfig> { it.provider != prefs.preferredProvider }
                .thenBy { it.priority }
        )
        // Filter to available providers — health tracker gates this
        return configs
            .filter { healthTracker.isAvailable(it.provider) }
            .map { it.provider to it }
            .ifEmpty {
                // All providers unavailable — reset health and try primary
                listOf(DEFAULT_PROVIDER_CONFIGS.first().let { it.provider to it })
            }
    }

    suspend fun analyze(
        claim   : String,
        sources : List<CorvusSource>,
        type    : ClaimType
    ): LlmAnalysisResult {

        val providerOrder = buildProviderOrder()
        val errors = mutableListOf<ProviderAttempt>()

        for ((provider, config) in providerOrder) {
            val attempt = attemptWithRetry(
                provider = provider,
                config   = config,
                claim    = claim,
                sources  = sources,
                type     = type
            )

            when (attempt) {
                is ProviderAttempt.Success -> {
                    healthTracker.recordSuccess(provider)
                    return attempt.result.copy(providerUsed = provider)
                }
                is ProviderAttempt.Failure -> {
                    healthTracker.recordFailure(provider, attempt.failure)
                    errors.add(attempt)

                    // Non-recoverable failure — stop immediately
                    if (attempt.failure is LlmFailure.AuthenticationFailed) {
                        throw LlmAuthException(provider)
                    }

                    // Log failure, continue to next provider
                    continue
                }
            }
        }

        // All providers exhausted
        throw AllProvidersExhaustedException(
            "All LLM providers failed. Attempts: ${errors.joinToString { it.toString() }}"
        )
    }

    // ── Retry logic per provider ─────────────────────────────────────────

    private suspend fun attemptWithRetry(
        provider : LlmProvider,
        config   : LlmProviderConfig,
        claim    : String,
        sources  : List<CorvusSource>,
        type     : ClaimType
    ): ProviderAttempt {

        var lastFailure: LlmFailure? = null

        repeat(config.maxRetries + 1) { attempt ->
            // Delay before retry (not before first attempt)
            if (attempt > 0) {
                delay(config.retryDelayMs * attempt)   // Exponential backoff
            }

            try {
                val result = withTimeout(config.timeoutMs) {
                    getProvider(provider).analyze(
                        claim   = claim,
                        sources = truncateSourcesForContext(sources, config.maxContextTokens),
                        type    = type
                    )
                }
                return ProviderAttempt.Success(result)

            } catch (e: Exception) {
                val responseCode = (e as? ResponseException)?.response?.status?.value
                val failure      = classifyFailure(e, responseCode)
                lastFailure      = failure

                // Don't retry on non-transient failures
                if (failure is LlmFailure.ContextLengthExceeded ||
                    failure is LlmFailure.ModelUnavailable ||
                    failure is LlmFailure.AuthenticationFailed) {
                    return ProviderAttempt.Failure(failure, provider)
                }
            }
        }

        return ProviderAttempt.Failure(
            lastFailure ?: LlmFailure.ServiceUnavailable,
            provider
        )
    }

    // ── Context truncation ───────────────────────────────────────────────
    // Cerebras has an 8,192 token context — truncate sources to fit

    private fun truncateSourcesForContext(
        sources    : List<CorvusSource>,
        maxTokens  : Int
    ): List<CorvusSource> {
        if (maxTokens >= 32_000) return sources   // Generous context — no truncation

        // Rough estimate: 1 token ≈ 4 chars
        val maxChars       = maxTokens * 3         // Leave room for prompt + response
        var totalChars     = 0
        val truncated      = mutableListOf<CorvusSource>()

        for (source in sources.sortedByDescending {
            it.credibilityTier.ordinal            // Highest credibility first
        }) {
            val snippetLen = (source.snippet?.length ?: 0)
            if (totalChars + snippetLen > maxChars) {
                // Include source with truncated snippet if partially fits
                val remaining = maxChars - totalChars
                if (remaining > 100) {
                    truncated.add(source.copy(
                        snippet    = source.snippet?.take(remaining),
                        rawContent = null   // Drop raw content when space is tight
                    ))
                }
                break
            }
            truncated.add(source)
            totalChars += snippetLen
        }

        return truncated
    }

    private fun getProvider(provider: LlmProvider) = when (provider) {
        LlmProvider.GEMINI     -> gemini
        LlmProvider.GROQ       -> groq
        LlmProvider.CEREBRAS   -> cerebras
        LlmProvider.OPENROUTER -> openRouter
        else                   -> gemini
    }
}

// ── Supporting types ─────────────────────────────────────────────────────────

sealed class ProviderAttempt {
    data class Success(val result: LlmAnalysisResult) : ProviderAttempt()
    data class Failure(
        val failure  : LlmFailure,
        val provider : LlmProvider
    ) : ProviderAttempt()
}
```

---

## Phase 3.4 — Pipeline Step Visibility

The user should know which provider was used, but should never know *why* a fallback happened. Silent switching is the goal:

```kotlin
// Updated PipelineStep enum
enum class PipelineStep {
    IDLE,
    PRE_SCREENING,
    CHECKING_KNOWN_FACTS,
    RETRIEVING_SOURCES,
    ANALYZING,              // Generic — does not reveal which provider
    DONE
}

// In PipelineStepIndicator composable
@Composable
fun PipelineStepLabel(step: PipelineStep) {
    val label = when (step) {
        PipelineStep.IDLE                 -> ""
        PipelineStep.PRE_SCREENING        -> "Pre-screening claim..."
        PipelineStep.CHECKING_KNOWN_FACTS -> "Checking fact databases..."
        PipelineStep.RETRIEVING_SOURCES   -> "Retrieving sources..."
        PipelineStep.ANALYZING            -> "Analysing..."  // Never says "Groq" or "Gemini"
        PipelineStep.DONE                 -> "Done"
    }
    // ...
}

// The provider IS revealed post-result in MethodologyCard — just not during loading
// MethodologyCard: "Analysis provider: Groq (Llama 3.3 70B)"
```

---

## Phase 3.5 — Provider Health Display in Settings

A diagnostics panel in Settings showing live provider health — useful for debugging and power users:

```kotlin
@Composable
fun LlmProviderHealthPanel(
    healthReport: Map<LlmProvider, String>,
    onResetHealth: () -> Unit
) {
    Card(
        border = BorderStroke(1.dp, CorvusTheme.colors.border),
        shape  = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "LLM PROVIDER STATUS",
                    style     = CorvusTheme.typography.labelSmall,
                    fontFamily = IbmPlexMono,
                    color     = CorvusTheme.colors.textSecondary
                )
                TextButton(onClick = onResetHealth) {
                    Text("Reset", style = CorvusTheme.typography.caption,
                        color = CorvusTheme.colors.accent)
                }
            }

            Spacer(Modifier.height(10.dp))

            healthReport.entries
                .sortedBy { it.key.displayName() }
                .forEach { (provider, status) ->
                    ProviderStatusRow(provider, status)
                }
        }
    }
}

@Composable
fun ProviderStatusRow(provider: LlmProvider, status: String) {
    val (color, dot) = when (status) {
        "Healthy"     -> CorvusTheme.colors.verdictTrue    to "●"
        "Degraded"    -> CorvusTheme.colors.verdictMisleading to "◐"
        "Unavailable" -> CorvusTheme.colors.verdictFalse   to "○"
        else          -> CorvusTheme.colors.textTertiary   to "○"
    }

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            provider.displayName(),
            style     = CorvusTheme.typography.bodySmall,
            color     = CorvusTheme.colors.textPrimary,
            fontFamily = IbmPlexMono
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(status, style = CorvusTheme.typography.caption, color = color)
            Text(dot,    style = CorvusTheme.typography.caption, color = color)
        }
    }
}
```

---

## Feature 3 Tasks

- [ ] Add `LlmFailure` sealed class
- [ ] Implement `classifyFailure()` mapping HTTP status codes to `LlmFailure`
- [ ] Add `LlmProviderConfig` data class with per-provider settings
- [ ] Define `DEFAULT_PROVIDER_CONFIGS` list
- [ ] Implement `LlmProviderHealthTracker` with `ConcurrentHashMap`
- [ ] Implement `isAvailable()` — rate limit + cool-down logic
- [ ] Implement `recordSuccess()` and `recordFailure()`
- [ ] Implement `LlmRouter.buildProviderOrder()` — preference + health aware
- [ ] Implement `LlmRouter.attemptWithRetry()` — exponential backoff
- [ ] Implement `LlmRouter.truncateSourcesForContext()` — Cerebras guard
- [ ] Wire `LlmProviderHealthTracker` into Hilt as `@Singleton`
- [ ] Expose `getHealthReport()` to `SettingsViewModel`
- [ ] Implement `LlmProviderHealthPanel` composable in Settings
- [ ] Implement `ProviderStatusRow` composable
- [ ] Ensure `PipelineStep.ANALYZING` label never exposes provider name
- [ ] Verify `MethodologyCard` shows actual provider used post-result
- [ ] Unit test: `classifyFailure()` — 429, 503, 401, timeout
- [ ] Unit test: `isAvailable()` — rate limited, cool-down, healthy
- [ ] Unit test: `recordFailure()` consecutive failures → cool-down
- [ ] Unit test: `buildProviderOrder()` — unhealthy providers excluded
- [ ] Unit test: `truncateSourcesForContext()` — Cerebras 8k token limit
- [ ] Integration test: Groq 429 → silent fallback to Gemini
- [ ] Integration test: all providers fail → `AllProvidersExhaustedException`
- [ ] Integration test: provider recovers after cool-down

**Estimated duration: 6 days**

---

---

## Combined Roadmap

| Phase | Feature | Duration | Build After |
|---|---|---|---|
| 1 | RAG Citation Enforcement | 7 days | Requires `rawContent` in sources |
| 2 | Source Transparency | 5 days | Independent — can build in parallel with Phase 1 |
| 3 | Dynamic LLM Fallbacks | 6 days | Independent — can build in parallel with Phase 1 |
| **Total** | | **~18 days** | Phase 2 + 3 can run in parallel |

**Recommended build order:**
- Phase 3 (LLM Fallbacks) first — makes all subsequent development more stable
- Phase 2 (Source Transparency) in parallel with Phase 3 — no dependencies
- Phase 1 (RAG Enforcement) last — depends on `rawContent` from Phase 2's Tavily update

---

## New Dependencies

```kotlin
// No new external dependencies required
// All three features use existing Ktor, Room, Compose, and Kotlinx tooling
```

---

## New API Keys Required

None. All three features work within the existing provider API keys.

---

## Definition of Done

**RAG Citation Enforcement**
- Every `GroundedFact` with a `source_index` has a `FactVerification` after pipeline
- `CitationConfidence.VERIFIED` facts show matched fragment from source text
- `CitationConfidence.LOW_CONFIDENCE` facts render with amber/red left bar
- `ExplanationConfidence.POORLY_GROUNDED` triggers confidence penalty on overall verdict
- `POORLY_GROUNDED` explanation shows warning banner
- Integration test: GDP claim with DOSM source → VERIFIED with matched fragment
- Integration test: hallucinated fact → LOW_CONFIDENCE with no matched fragment

**Source Transparency**
- All 10 MY hardcoded outlets show correct credibility label in compact state
- MBFC CSV loads and parses — known outlets (Reuters, BBC) return correct rating
- Unknown domains return heuristic rating without crash
- `SourceCard` expands to show full breakdown on tap
- Credibility score bar renders with correct colour at all threshold levels
- Bias indicator row hidden for GOV_AFFILIATED and SATIRE sources
- Rating source disclosure renders for all three `RatingSource` values

**Dynamic LLM Fallbacks**
- Groq 429 triggers silent fallback to Gemini — no error shown to user
- `LlmProviderHealthTracker` rate-limits Groq for correct duration
- After cool-down period, Groq is available again
- Context truncation correctly limits sources for Cerebras 8k window
- Provider health panel in Settings shows correct status for all 4 providers
- Reset button in Settings clears all health state
- `PipelineStep.ANALYZING` label never exposes provider name during loading
- `MethodologyCard` correctly shows which provider was actually used
