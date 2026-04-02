# Corvus — Feature Enhancement Plan IV
## Grounded Key Facts · Kernel of Truth · Missing Context Callout · Methodology Framing

> **Scope:** Four verdict-quality enhancements that collectively solve the trust and transparency gap in Corvus's output. None require new API integrations. All are prompt schema changes, parser updates, and Compose UI additions. Zero additional cost.

---

## Overview

| Feature | Core Problem Solved |
|---|---|
| **Grounded Key Facts** | LLM can hallucinate facts with no traceability — users can't know which source backs which claim |
| **Kernel of Truth Split** | MISLEADING/PARTIALLY_TRUE verdicts present a confusing mixed list — users can't separate what's real from what's distorted |
| **Missing Context Callout** | Critical context that explains *why* a claim is deceptive is buried inside the explanation paragraph |
| **Methodology Framing** | Explanation reads as omniscient oracle — users with no reason to trust Corvus have no visibility into *how* it knows what it claims |

These four features collectively move Corvus from *"here is the verdict"* to *"here is the verdict, here is why, here is how we know, and here is what part is real."* That is the difference between a tool users accept and a tool users trust.

---

---

# Feature 1 — Grounded Key Facts (Inline Source Citations)

## Problem

The current `key_facts` schema is:

```json
"key_facts": ["fact 1", "fact 2", "fact 3"]
```

This is an array of unattributed strings. If the LLM generates a fact not present in any retrieved source — a hallucination — the user has no mechanism to detect it. There is no link between the fact and the evidence it supposedly came from.

This is Corvus's most significant trust vulnerability.

---

## New Key Fact Schema

```json
"key_facts": [
  {
    "statement": "Malaysia's GDP grew by 3.4% in Q4 2023.",
    "source_index": 0,
    "is_direct_quote": false
  },
  {
    "statement": "The Department of Statistics Malaysia released this figure on 15 Feb 2024.",
    "source_index": 1,
    "is_direct_quote": false
  },
  {
    "statement": "The Finance Minister stated: 'Growth remains on target.'",
    "source_index": 2,
    "is_direct_quote": true
  }
]
```

| Field | Type | Description |
|---|---|---|
| `statement` | String | The fact as a complete sentence |
| `source_index` | Int? | Index into the `sources` array. `null` if LLM cannot attribute to a specific source |
| `is_direct_quote` | Boolean | Whether the statement is a verbatim quote from the source |

`source_index` being nullable is deliberate — forcing the LLM to always provide an index would cause hallucinated indices. A null index is honest; it signals the LLM is drawing on general knowledge rather than a retrieved source, which the UI should render differently.

---

## Data Model

```kotlin
data class GroundedFact(
    val statement: String,
    val sourceIndex: Int?,          // null = general knowledge, not from retrieved source
    val isDirectQuote: Boolean = false
)

// Replaces List<String> keyFacts in CorvusCheckResult.GeneralResult
data class CorvusCheckResult.GeneralResult(
    val verdict: Verdict,
    val confidence: Float,
    val explanation: String,
    val keyFacts: List<GroundedFact>,   // CHANGED — was List<String>
    val sources: List<CorvusSource>,
    val claimType: ClaimType,
    val harmAssessment: HarmAssessment,
    val plausibility: PlausibilityAssessment?,
    val missingContext: String?,
    val kernelOfTruth: KernelOfTruth?
)
```

---

## Updated LLM Prompt Schema

Replace the existing `key_facts` instruction with:

```
KEY FACTS — GROUNDED CITATIONS:
For each key fact in your analysis, you MUST identify which source it comes from.

Rules:
- source_index refers to the zero-based index of the source in the SOURCES list above.
- If a fact comes from your general knowledge and not from the provided sources,
  set source_index to null. Do not invent a source index.
- If the fact is a verbatim quote from the source, set is_direct_quote to true.
- Maximum 5 key facts.
- Each fact must be a complete, specific, attributable statement.
- Do not write vague facts like "The claim is disputed." Be specific.

"key_facts": [
  {
    "statement": "Complete factual statement here.",
    "source_index": 0,
    "is_direct_quote": false
  }
]
```

---

## Parser Update

```kotlin
fun parseKeyFacts(json: JsonObject): List<GroundedFact> {
    return json["key_facts"]
        ?.jsonArray
        ?.mapNotNull { element ->
            runCatching {
                val obj = element.jsonObject
                GroundedFact(
                    statement = obj["statement"]!!.jsonPrimitive.content,
                    sourceIndex = obj["source_index"]?.jsonPrimitive
                        ?.intOrNull,  // null-safe — returns null if missing or null in JSON
                    isDirectQuote = obj["is_direct_quote"]
                        ?.jsonPrimitive?.boolean ?: false
                )
            }.getOrNull()
        }
        ?: emptyList()

    // Validate indices — clamp to source list size, null if out of bounds
    .map { fact ->
        fact.copy(
            sourceIndex = fact.sourceIndex?.takeIf { it in sources.indices }
        )
    }
}
```

---

## UI — Grounded Key Facts List

### Fact Row with Citation Badge

```kotlin
@Composable
fun GroundedFactsList(
    facts: List<GroundedFact>,
    sources: List<CorvusSource>,
    onSourceClick: (Int) -> Unit     // Scrolls to source at this index
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "KEY FACTS",
            style = CorvusTheme.typography.label,
            color = CorvusTheme.colors.textSecondary
        )
        facts.forEach { fact ->
            GroundedFactRow(
                fact = fact,
                source = fact.sourceIndex?.let { sources.getOrNull(it) },
                onCitationClick = { fact.sourceIndex?.let { onSourceClick(it) } }
            )
        }
    }
}

@Composable
fun GroundedFactRow(
    fact: GroundedFact,
    source: CorvusSource?,
    onCitationClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Dashed left border — analytical instrument aesthetic
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(IntrinsicSize.Min)
                .background(
                    if (fact.sourceIndex != null) CorvusTheme.colors.accent.copy(alpha = 0.4f)
                    else CorvusTheme.colors.border,
                    DashPathEffect  // Custom drawn dashed border
                )
        )

        Column(modifier = Modifier.weight(1f)) {
            // Fact statement — quote style if direct quote
            Text(
                text = if (fact.isDirectQuote) "\"${fact.statement}\"" else fact.statement,
                style = if (fact.isDirectQuote)
                    CorvusTheme.typography.body.copy(fontStyle = FontStyle.Italic)
                else
                    CorvusTheme.typography.body,
                color = CorvusTheme.colors.textPrimary
            )

            // Citation line below statement
            Spacer(Modifier.height(2.dp))
            if (fact.sourceIndex != null && source != null) {
                CitationBadge(
                    index = fact.sourceIndex,
                    publisherName = source.publisher ?: "Source ${fact.sourceIndex + 1}",
                    onClick = onCitationClick
                )
            } else {
                // Null source — honest signal to user
                Text(
                    "General knowledge — not from retrieved sources",
                    style = CorvusTheme.typography.caption,
                    color = CorvusTheme.colors.textTertiary,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

@Composable
fun CitationBadge(
    index: Int,
    publisherName: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Numeric index badge
        Surface(
            color = CorvusTheme.colors.accent.copy(alpha = 0.15f),
            shape = RoundedCornerShape(2.dp),
            border = BorderStroke(1.dp, CorvusTheme.colors.accent.copy(alpha = 0.4f))
        ) {
            Text(
                "[${index + 1}]",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                style = CorvusTheme.typography.caption,
                color = CorvusTheme.colors.accent,
                fontFamily = IbmPlexMono
            )
        }

        Text(
            publisherName,
            style = CorvusTheme.typography.caption,
            color = CorvusTheme.colors.accent,
            textDecoration = TextDecoration.Underline
        )

        Icon(
            CorvusIcons.ArrowDown,
            modifier = Modifier.size(10.dp),
            tint = CorvusTheme.colors.accent.copy(alpha = 0.6f)
        )
    }
}
```

### Scroll-to-Source Coordination

Wire citation tap to scroll the `LazyColumn` to the referenced source card:

```kotlin
// In ResultScreen
val listState = rememberLazyListState()
val coroutineScope = rememberCoroutineScope()

// Map source index to LazyColumn item index
// Layout: [VerdictCard, FactsCard, SourcesHeader, Source0, Source1, Source2...]
val SOURCE_HEADER_OFFSET = 3  // adjust to actual item count before sources

fun scrollToSource(sourceIndex: Int) {
    coroutineScope.launch {
        listState.animateScrollToItem(
            index = SOURCE_HEADER_OFFSET + sourceIndex,
            scrollOffset = -24  // slight padding at top
        )
    }
}

LazyColumn(state = listState) {
    item { VerdictCard(result) }
    item { MissingContextCallout(result.missingContext) }
    item { KernelOfTruthCard(result.kernelOfTruth) }
    item {
        GroundedFactsList(
            facts = result.keyFacts,
            sources = result.sources,
            onSourceClick = { index -> scrollToSource(index) }
        )
    }
    item { SourcesSectionHeader(result.sources) }
    itemsIndexed(result.sources) { index, source ->
        SourceCard(source = source, numberLabel = index + 1)
    }
}
```

### Source Card — Numbered Label

Update `SourceCard` to display its index number, matching the citation badge:

```kotlin
@Composable
fun SourceCard(source: CorvusSource, numberLabel: Int) {
    Card {
        Row(modifier = Modifier.padding(12.dp)) {
            // Source number — matches citation badge
            Surface(
                color = CorvusTheme.colors.border,
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.padding(top = 2.dp, end = 8.dp)
            ) {
                Text(
                    "[$numberLabel]",
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    style = CorvusTheme.typography.caption,
                    color = CorvusTheme.colors.textSecondary,
                    fontFamily = IbmPlexMono
                )
            }
            // Rest of source card content...
            SourceCardContent(source)
        }
    }
}
```

---

## Tasks — Grounded Key Facts

- [ ] Update `GroundedFact` data class (replaces `String` in `keyFacts`)
- [ ] Update `CorvusCheckResult.GeneralResult` — change `keyFacts` type
- [ ] Update `QuoteResult` and `CompositeCheckResult` to use `GroundedFact`
- [ ] Update all LLM prompt templates with grounded key facts instruction
- [ ] Implement `parseKeyFacts()` with null-safe index validation
- [ ] Implement `GroundedFactsList` composable
- [ ] Implement `GroundedFactRow` with dashed left border
- [ ] Implement `CitationBadge` with tap handler
- [ ] Implement scroll-to-source coordination in `ResultScreen`
- [ ] Update `SourceCard` to accept and display `numberLabel`
- [ ] Update `CorvusHistoryEntity` — serialize `GroundedFact` list as JSON string
- [ ] Unit test: `parseKeyFacts()` — valid index, null index, out-of-bounds index
- [ ] Unit test: citation badge renders correctly for null vs valid source
- [ ] Integration test: tapping citation badge scrolls to correct source card

**Estimated duration: 4 days**

---

---

# Feature 2 — The Kernel of Truth Split

## Problem

When a verdict is `MISLEADING` or `PARTIALLY_TRUE`, the current `key_facts` array presents a flat, undifferentiated list. The user cannot easily distinguish which parts of the claim are grounded in reality and which parts are the distortion. This is precisely where viral misinformation is hardest to understand — because it is not entirely false.

The Kernel of Truth split forces the LLM to make this separation explicit and renders it as two visually distinct blocks.

---

## New Data Model

```kotlin
data class KernelOfTruth(
    val trueParts: List<GroundedFact>,      // What is factually accurate in the claim
    val falseParts: List<GroundedFact>,     // What is distorted, fabricated, or misleading
    val twistExplanation: String            // How the true parts were weaponised to create the misleading claim
)
```

`KernelOfTruth` is only populated when `verdict == MISLEADING || verdict == PARTIALLY_TRUE`. It replaces — not supplements — the standard `key_facts` list for these verdict types.

---

## Updated LLM Prompt Schema

Add this conditional block to all fact-check prompts:

```
KERNEL OF TRUTH (complete ONLY if verdict is MISLEADING or PARTIALLY_TRUE):

Viral misinformation rarely invents facts from nothing. It takes real facts
and distorts them. If your verdict is MISLEADING or PARTIALLY_TRUE, you must
explicitly categorize the facts into two groups:

true_parts:  The elements of the claim that are factually accurate.
             These are the "kernel of truth" that makes the claim believable.

false_parts: The elements that are fabricated, exaggerated, taken out of context,
             or misleading. These are what makes the claim harmful.

twist_explanation: In 1-2 sentences, explain the mechanism of distortion —
                   how were the true elements manipulated to create the misleading claim?

Apply the same grounded citation rules as key_facts:
each fact must include source_index (or null) and is_direct_quote.

"kernel_of_truth": {
  "true_parts": [
    {
      "statement": "The video does show a protest in Kuala Lumpur.",
      "source_index": 1,
      "is_direct_quote": false
    }
  ],
  "false_parts": [
    {
      "statement": "The protest occurred in 2018, not in response to current events.",
      "source_index": 0,
      "is_direct_quote": false
    }
  ],
  "twist_explanation": "A genuine protest video was re-shared with a false caption implying it was recent, exploiting the real footage's credibility to spread a fabricated narrative."
}

If verdict is TRUE, FALSE, or UNVERIFIABLE, set kernel_of_truth to null.
```

---

## Parser

```kotlin
fun parseKernelOfTruth(json: JsonObject, verdict: Verdict): KernelOfTruth? {
    if (verdict != Verdict.MISLEADING && verdict != Verdict.PARTIALLY_TRUE) return null

    val kernelJson = json["kernel_of_truth"]?.jsonObject ?: return null

    return runCatching {
        KernelOfTruth(
            trueParts  = parseGroundedFactArray(kernelJson["true_parts"]?.jsonArray),
            falseParts = parseGroundedFactArray(kernelJson["false_parts"]?.jsonArray),
            twistExplanation = kernelJson["twist_explanation"]
                ?.jsonPrimitive?.content ?: ""
        )
    }.getOrNull()
}

private fun parseGroundedFactArray(array: JsonArray?): List<GroundedFact> {
    return array?.mapNotNull { element ->
        runCatching {
            val obj = element.jsonObject
            GroundedFact(
                statement    = obj["statement"]!!.jsonPrimitive.content,
                sourceIndex  = obj["source_index"]?.jsonPrimitive?.intOrNull,
                isDirectQuote = obj["is_direct_quote"]?.jsonPrimitive?.boolean ?: false
            )
        }.getOrNull()
    } ?: emptyList()
}
```

---

## UI — Kernel of Truth Card

Rendered **below** the explanation paragraph, **above** the sources list.
Only visible when `verdict == MISLEADING || verdict == PARTIALLY_TRUE`.

```
┌─────────────────────────────────────────────────────┐
│  KERNEL OF TRUTH                                    │
│  How a real fact was twisted into misinformation    │
├─────────────────────────────────────────────────────┤
│  ✓  WHAT IS TRUE                                    │
│  ─────────────────────────────                      │
│  The video does show a real protest in              │
│  Kuala Lumpur.                              [2] FMT │
│                                                     │
│  The protest did involve hundreds of               │
│  participants.                              [1] Awani│
├─────────────────────────────────────────────────────┤
│  ✗  WHAT IS FALSE OR MISLEADING                     │
│  ─────────────────────────────                      │
│  The protest occurred in 2018, not in               │
│  response to current events.                [0] Star │
│                                                     │
│  The caption claiming it happened "yesterday"       │
│  is fabricated.                          [null] ···  │
├─────────────────────────────────────────────────────┤
│  HOW IT WAS TWISTED                                 │
│  A genuine protest video was re-shared with a       │
│  false caption to imply it was recent.              │
└─────────────────────────────────────────────────────┘
```

```kotlin
@Composable
fun KernelOfTruthCard(
    kernel: KernelOfTruth?,
    sources: List<CorvusSource>,
    onSourceClick: (Int) -> Unit
) {
    if (kernel == null) return

    Card(
        border = BorderStroke(1.dp, CorvusTheme.colors.verdictMisleading.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column {
            // Card header
            Column(
                modifier = Modifier
                    .background(CorvusTheme.colors.verdictMisleading.copy(alpha = 0.06f))
                    .padding(16.dp)
            ) {
                Text(
                    "KERNEL OF TRUTH",
                    style = CorvusTheme.typography.label,
                    color = CorvusTheme.colors.verdictMisleading
                )
                Text(
                    "How a real fact was twisted into misinformation",
                    style = CorvusTheme.typography.caption,
                    color = CorvusTheme.colors.textSecondary
                )
            }

            HorizontalDivider(color = CorvusTheme.colors.border)

            // True parts block
            KernelSection(
                label = "WHAT IS TRUE",
                icon = CorvusIcons.Check,
                iconTint = CorvusTheme.colors.verdictTrue,
                facts = kernel.trueParts,
                sources = sources,
                onSourceClick = onSourceClick,
                leftBarColor = CorvusTheme.colors.verdictTrue
            )

            HorizontalDivider(color = CorvusTheme.colors.border)

            // False parts block
            KernelSection(
                label = "WHAT IS FALSE OR MISLEADING",
                icon = CorvusIcons.X,
                iconTint = CorvusTheme.colors.verdictFalse,
                facts = kernel.falseParts,
                sources = sources,
                onSourceClick = onSourceClick,
                leftBarColor = CorvusTheme.colors.verdictFalse
            )

            HorizontalDivider(color = CorvusTheme.colors.border)

            // Twist explanation
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "HOW IT WAS TWISTED",
                    style = CorvusTheme.typography.label,
                    color = CorvusTheme.colors.textSecondary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    kernel.twistExplanation,
                    style = CorvusTheme.typography.body,
                    color = CorvusTheme.colors.textPrimary,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

@Composable
private fun KernelSection(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    facts: List<GroundedFact>,
    sources: List<CorvusSource>,
    onSourceClick: (Int) -> Unit,
    leftBarColor: Color
) {
    Column(modifier = Modifier.padding(16.dp)) {
        // Section label with icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, tint = iconTint, modifier = Modifier.size(14.dp))
            Text(
                label,
                style = CorvusTheme.typography.label,
                color = iconTint
            )
        }

        Spacer(Modifier.height(10.dp))

        // Facts — reuse GroundedFactRow with colored left bar
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            facts.forEach { fact ->
                GroundedFactRow(
                    fact = fact,
                    source = fact.sourceIndex?.let { sources.getOrNull(it) },
                    onCitationClick = { fact.sourceIndex?.let { onSourceClick(it) } },
                    leftBarColor = leftBarColor
                )
            }
            if (facts.isEmpty()) {
                Text(
                    "No specific items identified.",
                    style = CorvusTheme.typography.caption,
                    color = CorvusTheme.colors.textTertiary,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}
```

---

## Tasks — Kernel of Truth

- [ ] Add `KernelOfTruth` data class
- [ ] Add `kernelOfTruth: KernelOfTruth?` to `CorvusCheckResult.GeneralResult`
- [ ] Add kernel of truth block to all LLM prompt templates (conditional on MISLEADING/PARTIALLY_TRUE)
- [ ] Implement `parseKernelOfTruth()` and `parseGroundedFactArray()`
- [ ] Implement `KernelOfTruthCard` composable
- [ ] Implement `KernelSection` composable (reuses `GroundedFactRow`)
- [ ] Update `ResultScreen` `LazyColumn` to include `KernelOfTruthCard`
- [ ] Update `GroundedFactRow` to accept optional `leftBarColor` parameter
- [ ] Update `CorvusHistoryEntity` — serialize `kernelOfTruth` as JSON string
- [ ] Unit test: `parseKernelOfTruth()` — null for TRUE/FALSE, parsed for MISLEADING
- [ ] Unit test: `trueParts` and `falseParts` each parse correctly with source indices
- [ ] Integration test: viral protest video claim → MISLEADING + kernel populated
- [ ] Integration test: TRUE claim → `kernelOfTruth` is null

**Estimated duration: 4 days**

---

---

# Feature 3 — Elevate "Missing Context" to a Standalone UI Component

## Problem

The `context_missing` field exists in the current JSON schema but is optional and has no dedicated rendering — it either gets folded into the explanation text or is silently ignored. For `MISLEADING` and `OUT_OF_CONTEXT` verdicts, this is the single most important piece of information Corvus can surface. A user who reads only the verdict badge and the first sentence of the explanation should still understand *why* the claim is deceptive. Currently, they do not.

---

## Data Model

`missingContext` is already added to `CorvusCheckResult.GeneralResult` in the combined schema. It is elevated from an optional LLM output to a **first-class result field** with dedicated UI treatment.

```kotlin
// Already in CorvusCheckResult.GeneralResult:
val missingContext: String?    // Non-null for MISLEADING, OUT_OF_CONTEXT, PARTIALLY_TRUE

// Extend to capture context type for UI styling
data class MissingContextInfo(
    val content: String,
    val contextType: ContextType
)

enum class ContextType {
    TEMPORAL,       // Time/date context is missing ("This happened in 2018, not now")
    GEOGRAPHIC,     // Location context is missing or wrong
    ATTRIBUTION,    // Speaker/source context is missing
    STATISTICAL,    // Numbers lack denominator, baseline, or comparison
    SELECTIVE,      // Cherry-picked data — broader dataset tells different story
    GENERAL         // Catch-all
}
```

---

## Updated LLM Prompt Schema

Replace the existing `context_missing` optional field with:

```
MISSING CONTEXT (complete if verdict is MISLEADING, PARTIALLY_TRUE, or OUT_OF_CONTEXT):

If this claim is deceptive because of missing context rather than outright falsehood,
identify and articulate the critical context a reader needs to evaluate it fairly.

Be specific and concrete. Do not write "more context is needed" — state exactly
what the missing context IS.

Also identify the type of missing context:
  TEMPORAL     — The claim omits when something happened or cherry-picks a time period
  GEOGRAPHIC   — The claim omits where something happened or misapplies a location
  ATTRIBUTION  — The claim omits who said it, in what role, or under what conditions
  STATISTICAL  — The claim presents a number without denominator, baseline, or comparison
  SELECTIVE    — The claim uses real data but ignores contradicting data in the same dataset
  GENERAL      — Does not fit the above categories

"missing_context": {
  "content": "Specific, concrete statement of what context is absent",
  "context_type": "TEMPORAL|GEOGRAPHIC|ATTRIBUTION|STATISTICAL|SELECTIVE|GENERAL"
}

For TRUE or FALSE verdicts where context is not the issue, set missing_context to null.
```

---

## Parser Update

```kotlin
fun parseMissingContext(json: JsonObject, verdict: Verdict): MissingContextInfo? {
    val relevantVerdicts = setOf(
        Verdict.MISLEADING, Verdict.PARTIALLY_TRUE, Verdict.OUT_OF_CONTEXT
    )
    if (verdict !in relevantVerdicts) return null

    val mcJson = json["missing_context"]?.jsonObject ?: return null
    val content = mcJson["content"]?.jsonPrimitive?.content
        ?.takeIf { it.isNotBlank() } ?: return null

    val contextType = runCatching {
        ContextType.valueOf(mcJson["context_type"]?.jsonPrimitive?.content ?: "GENERAL")
    }.getOrDefault(ContextType.GENERAL)

    return MissingContextInfo(content = content, contextType = contextType)
}
```

---

## UI — Critical Context Callout

Positioned immediately below the verdict badge and confidence bar, **before** the explanation paragraph. It is the first thing a user reads after the verdict.

```
┌─────────────────────────────────────────────────────┐
│  MISLEADING                          ⚠️  · 84%      │  ← Verdict card
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐  ← Missing context callout
│  ◈  CRITICAL CONTEXT                    TEMPORAL    │
│  ─────────────────────────────────────────────────  │
│  This event occurred in March 2018 during the       │
│  previous administration. The claim implies it is   │
│  a recent development under the current government. │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐  ← Explanation paragraph
│  The claim is misleading because...                 │
└─────────────────────────────────────────────────────┘
```

```kotlin
@Composable
fun MissingContextCallout(missingContext: MissingContextInfo?) {
    if (missingContext == null) return

    val accentColor = CorvusTheme.colors.verdictMisleading

    Surface(
        color = accentColor.copy(alpha = 0.07f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                listOf(accentColor, accentColor.copy(alpha = 0.3f))
            )
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        CorvusIcons.Diamond,    // ◈ — distinct from warning icons
                        modifier = Modifier.size(14.dp),
                        tint = accentColor
                    )
                    Text(
                        "CRITICAL CONTEXT",
                        style = CorvusTheme.typography.label,
                        color = accentColor,
                        letterSpacing = 1.sp,
                        fontFamily = IbmPlexMono
                    )
                }

                // Context type tag
                ContextTypeTag(missingContext.contextType)
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = accentColor.copy(alpha = 0.2f))
            Spacer(Modifier.height(10.dp))

            // Context content
            Text(
                missingContext.content,
                style = CorvusTheme.typography.body,
                color = CorvusTheme.colors.textPrimary,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun ContextTypeTag(type: ContextType) {
    val label = when (type) {
        ContextType.TEMPORAL     -> "TEMPORAL"
        ContextType.GEOGRAPHIC   -> "GEOGRAPHIC"
        ContextType.ATTRIBUTION  -> "ATTRIBUTION"
        ContextType.STATISTICAL  -> "STATISTICAL"
        ContextType.SELECTIVE    -> "SELECTIVE DATA"
        ContextType.GENERAL      -> "CONTEXT"
    }

    Surface(
        color = CorvusTheme.colors.surface,
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, CorvusTheme.colors.border)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = CorvusTheme.typography.caption,
            color = CorvusTheme.colors.textSecondary,
            fontFamily = IbmPlexMono,
            letterSpacing = 0.5.sp
        )
    }
}
```

### Entry Animation

The callout should reveal with a subtle left-to-right wipe, distinct from the fade-in of other cards — communicating urgency without being alarmist:

```kotlin
// In ResultScreen — animate callout separately from verdict card
val calloutVisible by remember { mutableStateOf(false) }

LaunchedEffect(result) {
    delay(280)  // After verdict card reveals
    calloutVisible = true
}

AnimatedVisibility(
    visible = calloutVisible,
    enter = slideInHorizontally(
        initialOffsetX = { -40 },
        animationSpec = tween(350, easing = FastOutSlowInEasing)
    ) + fadeIn(tween(350))
) {
    MissingContextCallout(result.missingContext)
}
```

---

## Tasks — Missing Context Callout

- [ ] Add `ContextType` enum
- [ ] Add `MissingContextInfo` data class
- [ ] Update `CorvusCheckResult.GeneralResult` — replace `String?` with `MissingContextInfo?`
- [ ] Update all LLM prompt templates with structured `missing_context` block
- [ ] Implement `parseMissingContext()` with verdict guard
- [ ] Implement `MissingContextCallout` composable
- [ ] Implement `ContextTypeTag` composable
- [ ] Wire callout entry animation in `ResultScreen`
- [ ] Position callout in `LazyColumn` between verdict card and explanation
- [ ] Update `CorvusHistoryEntity` — serialize `MissingContextInfo` as JSON
- [ ] Unit test: `parseMissingContext()` — null for TRUE/FALSE, parsed for MISLEADING
- [ ] Unit test: all 6 `ContextType` enum values parse correctly
- [ ] Integration test: old-video-new-caption claim → MISLEADING + TEMPORAL context
- [ ] Integration test: TRUE claim → `missingContext` is null, callout not rendered

**Estimated duration: 3 days**

---

---

# Feature 4 — "How We Know This" (Methodology Framing)

## Problem

Corvus's explanation currently reads like a declaration:

> *"The claim is false. Malaysia's GDP grew by 3.4%, not 5.2%."*

This positions Corvus as an omniscient authority. Users who are already skeptical of the claim may also be skeptical of Corvus — particularly if they distrust technology, AI, or the sources Corvus used. The explanation gives them nothing to grab onto.

Evidentiary framing solves this by making the reasoning transparent and auditable. Instead of stating facts, it attributes them — telling users *how* Corvus knows what it claims to know, not just what it claims.

> *"According to data published by DOSM in Q4 2023, Malaysia's GDP grew at 3.4%. This figure was corroborated by multiple verified news outlets including Bernama and The Star. The claim's figure of 5.2% does not appear in any official or verified source."*

This is not just a tone change. It is a fundamental shift in the relationship between Corvus and the user — from oracle to evidence-based assistant.

---

## System Prompt Update

This is a **system prompt change**, not a JSON schema change. It applies to the `explanation` field across all pipelines.

### Current Implicit Instruction

```
explanation: "2-3 sentence neutral explanation"
```

### New Explicit Instruction

```
EXPLANATION — EVIDENTIARY FRAMING:

Write the explanation in an evidentiary, attribution-forward tone.
Do NOT state facts as absolute truths. DO attribute every claim to its evidence source.

Rules:
1. Open with what the evidence shows, not with a verdict declaration.
2. Attribute every specific fact or figure to a source:
   - Use "According to [source/publisher]..." for single-source facts
   - Use "Multiple verified outlets reported..." for facts confirmed across sources
   - Use "Official [document type] from [authority] states..." for primary sources
   - Use "No verified source confirms..." when absence of evidence is the finding
3. When sources conflict, acknowledge it: "While [Source A] states X, [Source B] reports Y."
4. Avoid first-person ("I found...") and omniscient declarations ("The truth is...").
5. End with what remains uncertain or unresolved, if anything.
6. Length: 3–5 sentences. Specific. Evidence-attributed. Neutral in tone.

Malaysian-context attribution phrases to use where applicable:
- "According to data from the Department of Statistics Malaysia (DOSM)..."
- "Official Hansard transcripts from Dewan Rakyat show..."
- "Bernama, Malaysia's national news agency, reported..."
- "As confirmed by the Prime Minister's Office official statement..."
- "Multiple Suhakam reports indicate..."
```

### Examples to Embed in Prompt

Include these contrast examples directly in the system prompt to calibrate the model:

```
BAD (oracle tone):
"The claim is false. The protest happened in 2018, not last week.
 The video has been circulating since the last election."

GOOD (evidentiary tone):
"Reverse-image search results and archived news coverage from Bernama and
 The Star place this footage in March 2018, predating the current administration.
 No verified news outlet has reported a protest matching this description in the
 claimed time period. The original caption on verified social media posts from
 2018 references a different political context entirely."

---

BAD (oracle tone):
"Malaysia's GDP did not grow by 5.2%. It grew by 3.4%."

GOOD (evidentiary tone):
"According to the Department of Statistics Malaysia's Q4 2023 report,
 GDP growth for that period was recorded at 3.4%, a figure corroborated
 by Bernama and the World Bank's Malaysia economic monitor.
 The figure of 5.2% cited in the claim does not appear in any official
 or verified economic publication for this period."
```

---

## Source Attribution Formatter

A utility that pre-formats retrieved sources into attribution-friendly strings for injection into the prompt context. This helps the LLM write better attribution without having to construct publisher names itself.

```kotlin
object SourceAttributionFormatter {

    fun formatForPrompt(sources: List<CorvusSource>): String {
        return sources.mapIndexed { index, source ->
            val attribution = buildAttribution(source)
            "[$index] $attribution — ${source.title}"
        }.joinToString("\n")
    }

    private fun buildAttribution(source: CorvusSource): String {
        return when {
            source.credibilityTier == CredibilityTier.PRIMARY &&
            source.sourceType == SourceType.OFFICIAL_TRANSCRIPT ->
                "Official transcript from ${source.publisher}"

            source.credibilityTier == CredibilityTier.PRIMARY &&
            source.sourceType == SourceType.GOVERNMENT_DATA ->
                "${source.publisher} (official government data)"

            source.publisher == "Bernama" ->
                "Bernama, Malaysia's national news agency"

            source.sourceType == SourceType.FACT_CHECK_DB ->
                "${source.publisher} (verified fact-checker)"

            source.sourceType == SourceType.ACADEMIC ->
                "${source.publisher} (peer-reviewed)"

            else ->
                source.publisher ?: extractDomain(source.url)
        }
    }

    private fun extractDomain(url: String?): String =
        Uri.parse(url).host?.removePrefix("www.") ?: "unknown source"
}
```

The formatted attribution block replaces the raw source list in the LLM prompt context:

```kotlin
// In FactCheckPromptBuilder
val sourceContext = SourceAttributionFormatter.formatForPrompt(sources)

val prompt = """
    ...
    SOURCES (use these attribution labels in your explanation):
    $sourceContext
    ...
    ${EVIDENTIARY_FRAMING_INSTRUCTION}
""".trimIndent()
```

---

## Methodology Disclosure Card (UI)

A lightweight collapsible card at the bottom of the result screen — below the sources — that discloses exactly how Corvus produced this result. This is the transparency receipt.

```
┌─────────────────────────────────────────────────────┐
│  HOW CORVUS CHECKED THIS               ▼            │
└─────────────────────────────────────────────────────┘
Expanded:
┌─────────────────────────────────────────────────────┐
│  HOW CORVUS CHECKED THIS               ▲            │
│                                                     │
│  Pipeline steps completed:                          │
│  ✓ Known fact-check databases    — No prior result  │
│  ✓ Web retrieval                 — 5 sources found  │
│  ✓ Malaysian source filter       — 3 local sources  │
│  ✓ LLM analysis                  — Gemini 2.0 Flash │
│                                                     │
│  Claim type detected: CURRENT_EVENT                 │
│  Sources retrieved: 5 · Avg. credibility: 76        │
│  Analysis provider: Gemini 2.0 Flash (AI Studio)    │
│  Checked: 20 Mar 2026, 14:32 WIB                    │
│                                                     │
│  Note: Corvus uses AI analysis and may make errors. │
│  Always verify critical claims with primary sources.│
└─────────────────────────────────────────────────────┘
```

```kotlin
data class MethodologyMetadata(
    val pipelineStepsCompleted: List<PipelineStepResult>,
    val claimTypeDetected: ClaimType,
    val sourcesRetrieved: Int,
    val avgSourceCredibility: Int,
    val llmProviderUsed: LlmProvider,
    val checkedAt: Long
)

data class PipelineStepResult(
    val step: PipelineStep,
    val outcome: String   // Human-readable: "No prior result", "5 sources found", etc.
)

@Composable
fun MethodologyCard(metadata: MethodologyMetadata) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        border = BorderStroke(1.dp, CorvusTheme.colors.border),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "HOW CORVUS CHECKED THIS",
                    style = CorvusTheme.typography.label,
                    color = CorvusTheme.colors.textSecondary,
                    fontFamily = IbmPlexMono
                )
                Icon(
                    if (expanded) CorvusIcons.ChevronUp else CorvusIcons.ChevronDown,
                    modifier = Modifier.size(16.dp),
                    tint = CorvusTheme.colors.textTertiary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(
                        start = 16.dp, end = 16.dp, bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Pipeline steps
                    Text("Pipeline steps completed:",
                        style = CorvusTheme.typography.caption,
                        color = CorvusTheme.colors.textSecondary)

                    metadata.pipelineStepsCompleted.forEach { step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("✓",
                                style = CorvusTheme.typography.caption,
                                color = CorvusTheme.colors.verdictTrue)
                            Text(
                                step.step.displayName().padEnd(28),
                                style = CorvusTheme.typography.caption,
                                color = CorvusTheme.colors.textPrimary,
                                fontFamily = IbmPlexMono
                            )
                            Text(
                                "— ${step.outcome}",
                                style = CorvusTheme.typography.caption,
                                color = CorvusTheme.colors.textSecondary
                            )
                        }
                    }

                    HorizontalDivider(color = CorvusTheme.colors.border)

                    // Summary stats
                    MethodologyStatRow("Claim type",
                        metadata.claimTypeDetected.name)
                    MethodologyStatRow("Sources retrieved",
                        "${metadata.sourcesRetrieved} · Avg. credibility: ${metadata.avgSourceCredibility}")
                    MethodologyStatRow("Analysis provider",
                        metadata.llmProviderUsed.displayName())
                    MethodologyStatRow("Checked",
                        metadata.checkedAt.toFormattedDateTime())

                    HorizontalDivider(color = CorvusTheme.colors.border)

                    // Disclaimer
                    Text(
                        "Corvus uses AI analysis and may make errors. " +
                        "Always verify critical claims with primary sources.",
                        style = CorvusTheme.typography.caption,
                        color = CorvusTheme.colors.textTertiary,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}

@Composable
fun MethodologyStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label,
            style = CorvusTheme.typography.caption,
            color = CorvusTheme.colors.textSecondary)
        Text(value,
            style = CorvusTheme.typography.caption,
            color = CorvusTheme.colors.textPrimary,
            fontFamily = IbmPlexMono)
    }
}
```

---

## Tasks — Methodology Framing

- [ ] Update system prompt in all LLM pipeline clients with evidentiary framing instruction
- [ ] Embed BAD/GOOD contrast examples in prompt (calibration)
- [ ] Add Malaysian-context attribution phrases to prompt
- [ ] Implement `SourceAttributionFormatter` utility object
- [ ] Wire `SourceAttributionFormatter.formatForPrompt()` into all prompt builders
- [ ] Add `MethodologyMetadata` data class
- [ ] Update `CorvusFactCheckUseCase` to collect and return `MethodologyMetadata`
- [ ] Update `CorvusViewModel` to pass `MethodologyMetadata` to UI state
- [ ] Implement `MethodologyCard` composable
- [ ] Implement `MethodologyStatRow` composable
- [ ] Add `MethodologyCard` at bottom of result screen `LazyColumn`
- [ ] Unit test: `SourceAttributionFormatter` — PRIMARY, VERIFIED, and GENERAL tier sources
- [ ] Prompt regression test: verify 3 sample explanations are attribution-forward after change
- [ ] UI test: `MethodologyCard` expands and displays all pipeline steps

**Estimated duration: 3 days**

---

---

## Combined JSON Schema — All Four Features

Full response schema incorporating every feature from this plan plus Plans II and III:

```json
{
  "verdict": "TRUE|FALSE|MISLEADING|PARTIALLY_TRUE|UNVERIFIABLE",
  "confidence": 0.87,

  "explanation": "Evidentiary-toned, attribution-forward explanation. 3-5 sentences. Attributes every fact to a named source.",

  "key_facts": [
    {
      "statement": "Complete factual statement.",
      "source_index": 0,
      "is_direct_quote": false
    }
  ],

  "kernel_of_truth": {
    "true_parts": [
      { "statement": "...", "source_index": 1, "is_direct_quote": false }
    ],
    "false_parts": [
      { "statement": "...", "source_index": null, "is_direct_quote": false }
    ],
    "twist_explanation": "How the true elements were used to construct the misleading claim."
  },

  "missing_context": {
    "content": "Specific, concrete missing context statement.",
    "context_type": "TEMPORAL|GEOGRAPHIC|ATTRIBUTION|STATISTICAL|SELECTIVE|GENERAL"
  },

  "harm_assessment": {
    "level": "NONE|LOW|MODERATE|HIGH",
    "category": "NONE|HEALTH|SAFETY|RACIAL_ETHNIC|RELIGIOUS|POLITICAL|FINANCIAL",
    "reason": "One sentence. Empty if NONE."
  },

  "plausibility": {
    "score": "IMPLAUSIBLE|UNLIKELY|NEUTRAL|PLAUSIBLE|PROBABLE",
    "reasoning": "1-2 sentences. Only if verdict is UNVERIFIABLE.",
    "closest_evidence": "Best available evidence hint. Null if not applicable."
  }
}
```

**Conditional population rules:**

| Field | Populated when |
|---|---|
| `key_facts` | Always — minimum 1, maximum 5 |
| `kernel_of_truth` | `verdict == MISLEADING \|\| PARTIALLY_TRUE` only |
| `missing_context` | `verdict == MISLEADING \|\| PARTIALLY_TRUE \|\| OUT_OF_CONTEXT` only |
| `harm_assessment` | Always — `level: NONE` when no harm |
| `plausibility` | `verdict == UNVERIFIABLE` only |

---

## Combined Roadmap

| Phase | Feature | Duration | Build After |
|---|---|---|---|
| 1 | Grounded Key Facts | 4 days | Existing pipeline |
| 2 | Kernel of Truth Split | 4 days | Phase 1 (reuses `GroundedFact`) |
| 3 | Missing Context Callout | 3 days | Phase 1 (shares prompt schema work) |
| 4 | Methodology Framing | 3 days | Phases 1–3 (updates finalized prompt) |
| **Total** | | **~14 days** | |

Phases 1–3 share the same prompt schema extension and should be built together. Phase 4 (methodology framing) updates the finalized prompt after schema work is settled — avoids re-editing the prompt multiple times.

---

## New Dependencies

None. All composables use the existing Compose + Material3 toolkit. No new network clients.

---

## New API Keys Required

None.

---

## Definition of Done

**Grounded Key Facts**
- Every `GroundedFact` has a valid `source_index` or honest `null`
- Out-of-bounds indices are caught and nulled during parsing — no crashes
- Citation badge `[N]` taps scroll `LazyColumn` to the correct source card with smooth animation
- Null-source facts render "General knowledge" caption in tertiary color
- Direct quotes render in italic with quotation marks

**Kernel of Truth**
- `kernelOfTruth` is `null` for TRUE, FALSE, and UNVERIFIABLE verdicts
- `trueParts` and `falseParts` both render with correct left-bar color
- `twistExplanation` renders in italic below both sections
- Empty `trueParts` or `falseParts` renders graceful "No specific items identified" caption
- Integration test: protest video claim → MISLEADING + kernel with temporal split

**Missing Context Callout**
- Callout renders between verdict card and explanation — not inside either
- `ContextTypeTag` renders correct label for all 6 types
- Callout entry animation fires 280ms after verdict card, slides in from left
- TRUE and FALSE verdicts — callout component not rendered at all (no empty space)

**Methodology Framing**
- 3 sample explanations after prompt change are verifiably attribution-forward
- No explanations contain "The truth is..." or "I found..." constructions
- `SourceAttributionFormatter` correctly builds attribution for PRIMARY, VERIFIED, GENERAL tiers
- `MethodologyCard` expands to show all pipeline steps with outcomes
- Disclaimer text visible in expanded state
