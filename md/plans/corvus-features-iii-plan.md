# Corvus — Feature Enhancement Plan III
## Harm Potential Flagging · Plausibility Spectrum

> **Scope:** Two verdict-enrichment features that add critical depth to Corvus's output. Neither requires new API integrations — both are LLM prompt schema extensions with corresponding data model and UI changes. Zero additional cost.

---

## Why These Features Matter

Standard fact-checkers treat all FALSE claims equally and leave UNVERIFIABLE as a dead end. These two features fix both problems:

| Gap Today | Feature | What It Adds |
|---|---|---|
| FALSE is binary — no severity signal | **Harm Potential** | Distinguishes a trivially wrong claim from a dangerous one |
| UNVERIFIABLE is a dead end | **Plausibility Spectrum** | Tells users *how* unverifiable — from "no data" to "defies physics" |

Together they make Corvus's verdicts **actionable**, not just accurate.

---

---

# Feature 1 — Harm Potential Flag

## Concept

Not all FALSE claims carry the same weight. The error in *"Anwar wore a blue tie yesterday"* (it was red) and the claim *"Drinking bleach cures Covid"* are both FALSE — but one is a harmless factual slip and the other could kill someone who believes it.

Corvus should surface this distinction explicitly, both in the data model and in the UI, so users immediately understand not just *that* something is wrong but *how dangerously* wrong it is.

---

## Harm Categories

Harm potential is assessed across five vectors. The final `HarmLevel` is derived from the highest-severity vector triggered.

```
HEALTH          → Medical misinformation, dangerous treatments, vaccine denial
SAFETY          → Instructions or claims that could cause physical harm
RACIAL_ETHNIC   → Content inciting racial or ethnic tension or violence
RELIGIOUS       → Claims inciting religious conflict or persecution
POLITICAL       → Election interference, incitement to political violence
FINANCIAL       → Scam-enabling claims, fraudulent investment advice
NONE            → No meaningful real-world harm potential
```

---

## Data Model

```kotlin
enum class HarmLevel {
    NONE,       // Factually wrong but harmless (wrong tie colour, wrong sports score)
    LOW,        // Minor misinformation — small real-world impact
    MODERATE,   // Could cause meaningful harm if acted upon
    HIGH        // Dangerous — health risk, incitement, public safety threat
}

enum class HarmCategory {
    NONE,
    HEALTH,
    SAFETY,
    RACIAL_ETHNIC,
    RELIGIOUS,
    POLITICAL,
    FINANCIAL
}

data class HarmAssessment(
    val level: HarmLevel,
    val category: HarmCategory,
    val reason: String           // Short LLM-generated explanation of why harm level was assigned
)
```

### Extending CorvusCheckResult

```kotlin
// Add harm assessment to the existing GeneralResult
data class CorvusCheckResult.GeneralResult(
    val verdict: Verdict,
    val confidence: Float,
    val explanation: String,
    val keyFacts: List<String>,
    val sources: List<CorvusSource>,
    val claimType: ClaimType,
    val harmAssessment: HarmAssessment   // NEW — always populated, NONE by default
) : CorvusCheckResult()

// Also added to CompositeCheckResult at the sub-claim level
data class SubClaim(
    val id: String,
    val text: String,
    val index: Int,
    val result: CorvusCheckResult? = null,
    val harmAssessment: HarmAssessment? = null  // NEW
)
```

---

## LLM Prompt Schema Extension

Harm assessment is added to the existing fact-check prompt. It is **not a separate LLM call** — it is an additional field in the JSON schema returned by the same analysis call.

### Extended Prompt Addition

Append this block to all existing fact-check prompts (general, quote, statistical, scientific):

```
HARM ASSESSMENT:
After determining your verdict, assess the real-world harm potential of this claim
if it were believed and acted upon by a member of the public.

Consider:
- Could this claim cause physical harm (health, safety)?
- Could this claim incite racial, religious, or ethnic tension?
- Could this claim interfere with democratic processes or incite political violence?
- Could this claim cause significant financial harm?
- Is this a harmless factual error with no meaningful real-world consequence?

IMPORTANT: Assign HIGH only when there is a credible, direct path from believing
this claim to serious harm. Do not over-escalate.
```

### Extended JSON Schema

```json
{
  "verdict": "TRUE|FALSE|MISLEADING|PARTIALLY_TRUE|UNVERIFIABLE",
  "confidence": 0.0,
  "explanation": "...",
  "key_facts": [],
  "sources_used": [],
  "harm_assessment": {
    "level": "NONE|LOW|MODERATE|HIGH",
    "category": "NONE|HEALTH|SAFETY|RACIAL_ETHNIC|RELIGIOUS|POLITICAL|FINANCIAL",
    "reason": "One sentence explaining the harm assessment"
  }
}
```

### Harm Level Guidance Examples (in prompt)

```
Examples to calibrate your harm assessment:

HIGH:
- "Drinking [substance] cures [disease]"       → HEALTH
- "Vaccine X causes infertility"                → HEALTH
- "[Ethnic group] is responsible for [attack]" → RACIAL_ETHNIC
- "Election results were fraudulent, take action" → POLITICAL

MODERATE:
- "This supplement prevents cancer"             → HEALTH
- "[Politician] is secretly taking bribes"      → POLITICAL (unverified allegation)
- "This investment is guaranteed 50% returns"  → FINANCIAL

LOW:
- "Company X's revenue was RM2B" (actually RM1.8B) → FINANCIAL (minor, public info)
- "[Public figure] said something they didn't" → POLITICAL (harmless misquote)

NONE:
- "The match ended 2-1" (it was 3-1)            → NONE
- "The event was on Tuesday" (it was Wednesday) → NONE
```

---

## Harm Assessment Parser

```kotlin
fun parseHarmAssessment(json: JsonObject): HarmAssessment {
    val harmJson = json["harm_assessment"]?.jsonObject
        ?: return HarmAssessment(HarmLevel.NONE, HarmCategory.NONE, "")

    val level = runCatching {
        HarmLevel.valueOf(harmJson["level"]?.jsonPrimitive?.content ?: "NONE")
    }.getOrDefault(HarmLevel.NONE)

    val category = runCatching {
        HarmCategory.valueOf(harmJson["category"]?.jsonPrimitive?.content ?: "NONE")
    }.getOrDefault(HarmCategory.NONE)

    val reason = harmJson["reason"]?.jsonPrimitive?.content ?: ""

    return HarmAssessment(level, category, reason)
}
```

---

## Local Harm Keyword Pre-Screen

Before the LLM evaluates harm, run a lightweight local keyword pre-screen to:
1. Give the LLM a hint if obvious harm signals are present
2. Catch cases where the LLM underestimates harm due to subtle phrasing

```kotlin
object HarmPreScreener {

    private val HIGH_HARM_PATTERNS = mapOf(
        HarmCategory.HEALTH to listOf(
            "cures", "bleach", "mms", "miracle mineral", "prevents cancer",
            "vaccine causes", "do not vaccinate", "ivermectin cures",
            "drink", "inject", "overdose", "self-medicate"
        ),
        HarmCategory.RACIAL_ETHNIC to listOf(
            "race war", "ethnic cleansing", "all [race]", "blame the",
            "pendatang", "kafir harus", "kaum cina", "kaum melayu"
        ),
        HarmCategory.POLITICAL to listOf(
            "take to the streets", "revolt", "armed resistance",
            "overthrow", "pilihan raya dicuri", "undi dicuri"
        ),
        HarmCategory.FINANCIAL to listOf(
            "guaranteed returns", "double your money", "amanah hartanah",
            "pelaburan tanpa risiko", "forex guaranteed"
        )
    )

    fun preScreen(claim: String): HarmCategory? {
        val lower = claim.lowercase()
        return HIGH_HARM_PATTERNS.entries.firstOrNull { (_, keywords) ->
            keywords.any { keyword -> lower.contains(keyword) }
        }?.key
    }
}
```

If a category is pre-screened, inject it as a hint into the LLM prompt:

```kotlin
val hint = HarmPreScreener.preScreen(claim)
val harmHint = hint?.let {
    "\nHINT: This claim may contain ${it.name} harm signals. Evaluate carefully."
} ?: ""
```

---

## UI Implementation

### Verdict Card — Harm Level Styling

The verdict card adapts its visual treatment based on `HarmLevel`. The core verdict colors remain unchanged — harm level is communicated through **additional** visual signals layered on top.

```kotlin
@Composable
fun VerdictCard(result: CorvusCheckResult.GeneralResult) {
    val harm = result.harmAssessment

    // Border intensifies with harm level
    val borderColor = when {
        harm.level == HarmLevel.HIGH && result.verdict == Verdict.FALSE ->
            CorvusTheme.colors.verdictFalse
        harm.level == HarmLevel.HIGH ->
            CorvusTheme.colors.verdictFalse.copy(alpha = 0.7f)
        harm.level == HarmLevel.MODERATE ->
            CorvusTheme.colors.verdictMisleading
        else ->
            CorvusTheme.colors.border
    }

    val borderWidth = when (harm.level) {
        HarmLevel.HIGH     -> 2.dp
        HarmLevel.MODERATE -> 1.5.dp
        else               -> 1.dp
    }

    Card(
        border = BorderStroke(borderWidth, borderColor),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (harm.level) {
                HarmLevel.HIGH -> CorvusTheme.colors.verdictFalse.copy(alpha = 0.06f)
                else           -> CorvusTheme.colors.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Standard verdict header
            VerdictHeader(result.verdict, result.confidence)

            // HIGH harm — prominent warning block
            if (harm.level == HarmLevel.HIGH) {
                Spacer(Modifier.height(12.dp))
                HarmWarningBlock(harm)
            }

            // MODERATE harm — inline subdued tag
            if (harm.level == HarmLevel.MODERATE) {
                Spacer(Modifier.height(8.dp))
                HarmInlineTag(harm)
            }

            Spacer(Modifier.height(12.dp))
            Text(result.explanation, style = CorvusTheme.typography.body)
        }
    }
}
```

### HIGH Harm Warning Block

```kotlin
@Composable
fun HarmWarningBlock(harm: HarmAssessment) {
    val pulseAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        )
    )

    Surface(
        color = CorvusTheme.colors.verdictFalse.copy(alpha = 0.10f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, CorvusTheme.colors.verdictFalse.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Pulsing warning icon — draws attention without screaming
            Icon(
                imageVector = CorvusIcons.AlertTriangle,
                contentDescription = "High harm warning",
                tint = CorvusTheme.colors.verdictFalse.copy(alpha = pulseAlpha),
                modifier = Modifier.size(16.dp).padding(top = 2.dp)
            )
            Column {
                Text(
                    "${harm.category.displayName()} RISK".uppercase(),
                    style = CorvusTheme.typography.label,
                    color = CorvusTheme.colors.verdictFalse,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    harm.reason,
                    style = CorvusTheme.typography.caption,
                    color = CorvusTheme.colors.textSecondary
                )
            }
        }
    }
}

fun HarmCategory.displayName() = when (this) {
    HarmCategory.HEALTH        -> "Public Health"
    HarmCategory.SAFETY        -> "Public Safety"
    HarmCategory.RACIAL_ETHNIC -> "Racial Tension"
    HarmCategory.RELIGIOUS     -> "Religious Tension"
    HarmCategory.POLITICAL     -> "Political"
    HarmCategory.FINANCIAL     -> "Financial"
    HarmCategory.NONE          -> ""
}
```

### MODERATE Harm Inline Tag

```kotlin
@Composable
fun HarmInlineTag(harm: HarmAssessment) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            CorvusIcons.AlertCircle,
            tint = CorvusTheme.colors.verdictMisleading,
            modifier = Modifier.size(12.dp)
        )
        Text(
            "Moderate ${harm.category.displayName()} concern".uppercase(),
            style = CorvusTheme.typography.caption,
            color = CorvusTheme.colors.verdictMisleading,
            letterSpacing = 0.5.sp
        )
    }
}
```

### History List — Harm Indicator

In the history screen, show a small harm indicator dot on history items that were HIGH harm — a passive reminder that this was a serious claim.

```kotlin
@Composable
fun HistoryItemRow(item: CorvusHistoryEntity) {
    Row(modifier = Modifier.padding(16.dp)) {
        // Verdict color left stripe
        VerdictStripe(item.verdict)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.claim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.checkedAt.toRelativeTime(),
                style = CorvusTheme.typography.caption,
                color = CorvusTheme.colors.textSecondary)
        }

        // HIGH harm dot — subtle, not alarming
        if (item.harmLevel == HarmLevel.HIGH) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .align(Alignment.Top)
                    .background(CorvusTheme.colors.verdictFalse, CircleShape)
            )
        }
    }
}
```

### Share / Export — Harm Label

When a result is shared or exported, append a harm context line:

```
CORVUS FACT-CHECK
Verdict: FALSE
Harm Level: HIGH — Public Health Risk
This claim could be dangerous if believed and acted upon.
Checked: 20 Mar 2026 · corvus.app
```

---

## Tasks — Harm Potential

- [ ] Add `HarmLevel` and `HarmCategory` enums
- [ ] Add `HarmAssessment` data class
- [ ] Extend `CorvusCheckResult.GeneralResult` with `harmAssessment` field
- [ ] Extend `CorvusHistoryEntity` with `harmLevel` + `harmCategory` columns + Room migration
- [ ] Add harm assessment block to all LLM prompt templates (general, quote, statistical, scientific)
- [ ] Implement `parseHarmAssessment()` JSON parser with safe fallback
- [ ] Implement `HarmPreScreener` with Malaysian-context keyword list
- [ ] Wire pre-screener hint into prompt builder
- [ ] Implement `HarmWarningBlock` composable (HIGH)
- [ ] Implement `HarmInlineTag` composable (MODERATE)
- [ ] Update `VerdictCard` with harm-aware border + background
- [ ] Update `HistoryItemRow` with HIGH harm dot indicator
- [ ] Update share/export text template with harm label
- [ ] Unit test: `parseHarmAssessment()` — valid JSON, missing field, invalid enum
- [ ] Unit test: `HarmPreScreener` — 5 high-harm claims, 5 benign claims
- [ ] Integration test: end-to-end — bleach/Covid claim returns HIGH HEALTH
- [ ] Integration test: sports score claim returns NONE

**Estimated duration: 4 days**

---

---

# Feature 2 — Plausibility Spectrum for Unverifiable Claims

## Concept

`UNVERIFIABLE` today is a verdict that tells the user nothing beyond *"we couldn't confirm this."* But there is a world of difference between:

- *"Aliens built the Petronas Twin Towers"* — UNVERIFIABLE, but defies all established physics, engineering history, and documented fact. Deeply implausible.
- *"The Prime Minister had a private meeting with the Sultan last Tuesday"* — UNVERIFIABLE because private meetings are unrecorded, but entirely plausible given their roles and history.
- *"There is a classified government document proving X"* — UNVERIFIABLE with genuinely neutral plausibility.

Users deserve to know which kind of UNVERIFIABLE they're dealing with.

---

## Plausibility Model

```kotlin
enum class PlausibilityScore {
    IMPLAUSIBLE,    // Contradicts established facts, science, or documented history
    UNLIKELY,       // Possible in theory but evidence strongly suggests otherwise
    NEUTRAL,        // Genuinely unknown — no evidence either way
    PLAUSIBLE,      // Consistent with known facts, just unconfirmed
    PROBABLE        // Strong circumstantial support, just not directly verifiable
}

data class PlausibilityAssessment(
    val score: PlausibilityScore,
    val reasoning: String,          // Why this plausibility score was assigned
    val closestEvidence: String?    // What the best available evidence suggests, even if not conclusive
)
```

### Extending CorvusCheckResult for Unverifiable

```kotlin
data class CorvusCheckResult.GeneralResult(
    val verdict: Verdict,
    val confidence: Float,
    val explanation: String,
    val keyFacts: List<String>,
    val sources: List<CorvusSource>,
    val claimType: ClaimType,
    val harmAssessment: HarmAssessment,
    val plausibility: PlausibilityAssessment? = null  // Non-null ONLY when verdict == UNVERIFIABLE
)
```

---

## LLM Prompt Schema Extension

Plausibility is a **conditional field** — the LLM only populates it when it selects `UNVERIFIABLE` as the verdict. This keeps the prompt focused and avoids polluting verdicts where it doesn't apply.

### Prompt Addition

Append after the standard verdict instructions:

```
PLAUSIBILITY SPECTRUM (complete ONLY if verdict is UNVERIFIABLE):

If you select UNVERIFIABLE, you must assess plausibility — how likely is this claim
to be true, based on what you know about the world, physics, established history,
and available circumstantial evidence?

Plausibility levels:
  IMPLAUSIBLE — Contradicts well-established science, engineering, documented history,
                or basic logic. No credible mechanism exists for it to be true.
                Example: "Aliens built the Petronas Twin Towers" contradicts
                all documented construction history and established physics.

  UNLIKELY    — Theoretically possible but available evidence strongly points
                against it. Credible but unlikely.

  NEUTRAL     — Genuinely unknown. No evidence points either way.
                Example: Whether a specific private conversation occurred.

  PLAUSIBLE   — Consistent with known facts and context. Unconfirmed but
                makes sense given what we know.

  PROBABLE    — Strong circumstantial evidence supports it. Very likely true
                but cannot be directly verified with available sources.

If verdict is NOT UNVERIFIABLE, set plausibility to null in your response.
```

### Extended JSON Schema

```json
{
  "verdict": "UNVERIFIABLE",
  "confidence": 0.0,
  "explanation": "...",
  "key_facts": [],
  "sources_used": [],
  "harm_assessment": {
    "level": "NONE",
    "category": "NONE",
    "reason": ""
  },
  "plausibility": {
    "score": "IMPLAUSIBLE|UNLIKELY|NEUTRAL|PLAUSIBLE|PROBABLE",
    "reasoning": "One to two sentences explaining the plausibility score",
    "closest_evidence": "What the best available evidence suggests, even if inconclusive"
  }
}
```

For non-UNVERIFIABLE verdicts:

```json
{
  "verdict": "FALSE",
  "plausibility": null
}
```

---

## Plausibility Parser

```kotlin
fun parsePlausibility(json: JsonObject, verdict: Verdict): PlausibilityAssessment? {
    // Only parse if verdict is UNVERIFIABLE
    if (verdict != Verdict.UNVERIFIABLE) return null

    val plausJson = json["plausibility"]?.jsonObject ?: return null

    val score = runCatching {
        PlausibilityScore.valueOf(
            plausJson["score"]?.jsonPrimitive?.content ?: "NEUTRAL"
        )
    }.getOrDefault(PlausibilityScore.NEUTRAL)

    return PlausibilityAssessment(
        score = score,
        reasoning = plausJson["reasoning"]?.jsonPrimitive?.content ?: "",
        closestEvidence = plausJson["closest_evidence"]?.jsonPrimitive?.content
    )
}
```

---

## Plausibility Enrichment Pass

For `UNVERIFIABLE` claims, run one additional lightweight enrichment step — ask the LLM specifically about plausibility with more context from the retrieved sources. This separate pass produces a more considered plausibility score than the combined prompt alone.

```kotlin
class PlausibilityEnricherUseCase @Inject constructor(
    private val groqClient: GroqClient
) {
    suspend fun enrich(
        claim: String,
        sources: List<CorvusSource>,
        initialAssessment: PlausibilityAssessment?
    ): PlausibilityAssessment {

        // Skip enrichment if initial assessment is already strongly scored
        if (initialAssessment?.score == PlausibilityScore.IMPLAUSIBLE ||
            initialAssessment?.score == PlausibilityScore.PROBABLE) {
            return initialAssessment
        }

        val prompt = """
            A fact-check of the following claim returned UNVERIFIABLE.
            
            CLAIM: "$claim"
            
            RETRIEVED SOURCES (for context):
            ${sources.take(3).mapIndexed { i, s -> "[${i+1}] ${s.title}: ${s.snippet}" }.joinToString("\n")}
            
            Based on established knowledge, physics, documented history, and the
            sources above — how plausible is this claim, even if it cannot be
            directly verified?
            
            Respond ONLY with valid JSON:
            {
              "score": "IMPLAUSIBLE|UNLIKELY|NEUTRAL|PLAUSIBLE|PROBABLE",
              "reasoning": "1-2 sentences",
              "closest_evidence": "Best available evidence hint, even if inconclusive"
            }
        """.trimIndent()

        return try {
            val response = groqClient.complete(prompt, maxTokens = 300)
            parsePlausibilityDirect(response)
        } catch (e: Exception) {
            initialAssessment ?: PlausibilityAssessment(PlausibilityScore.NEUTRAL, "", null)
        }
    }
}
```

---

## UI Implementation

### Verdict Badge — UNVERIFIABLE with Plausibility

The standard `UNVERIFIABLE` badge is augmented with a plausibility sub-label:

```
Before:  [ UNVERIFIABLE ]

After:   [ UNVERIFIABLE ]
           ↳ High Implausibility
```

```kotlin
@Composable
fun VerdictBadge(verdict: Verdict, plausibility: PlausibilityAssessment?) {
    Column(horizontalAlignment = Alignment.Start) {
        // Standard verdict chip — unchanged
        VerdictChip(verdict)

        // Plausibility sub-label — only when UNVERIFIABLE
        if (verdict == Verdict.UNVERIFIABLE && plausibility != null) {
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    CorvusIcons.ChevronRight,
                    modifier = Modifier.size(10.dp),
                    tint = CorvusTheme.colors.textTertiary
                )
                Text(
                    plausibility.score.displayLabel(),
                    style = CorvusTheme.typography.caption,
                    color = plausibility.score.labelColor(),
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

fun PlausibilityScore.displayLabel() = when (this) {
    PlausibilityScore.IMPLAUSIBLE -> "High Implausibility"
    PlausibilityScore.UNLIKELY    -> "Unlikely"
    PlausibilityScore.NEUTRAL     -> "Genuinely Unknown"
    PlausibilityScore.PLAUSIBLE   -> "Plausible"
    PlausibilityScore.PROBABLE    -> "Probably True — Unconfirmed"
}

@Composable
fun PlausibilityScore.labelColor() = when (this) {
    PlausibilityScore.IMPLAUSIBLE -> CorvusTheme.colors.verdictFalse
    PlausibilityScore.UNLIKELY    -> CorvusTheme.colors.verdictMisleading
    PlausibilityScore.NEUTRAL     -> CorvusTheme.colors.textSecondary
    PlausibilityScore.PLAUSIBLE   -> CorvusTheme.colors.verdictTrue.copy(alpha = 0.8f)
    PlausibilityScore.PROBABLE    -> CorvusTheme.colors.verdictTrue
}
```

### Plausibility Detail Card

Shown below the verdict card when verdict is `UNVERIFIABLE`. Collapsible.

```
┌─────────────────────────────────────────────────┐
│  UNVERIFIABLE                                   │
│  ↳ High Implausibility                          │
├─────────────────────────────────────────────────┤
│  PLAUSIBILITY BREAKDOWN                   ▼     │
│                                                 │
│  Score:   IMPLAUSIBLE                           │
│  ━━━━━━━━░░░░░░░░░░░░░░░░░░░░░░░░░░░           │
│  Implaus. ←──────────────────────→ Probable     │
│                                                 │
│  Why: This claim contradicts all documented     │
│  construction records for the Petronas Twin     │
│  Towers and established structural engineering. │
│                                                 │
│  Closest evidence: The towers were designed     │
│  by Cesar Pelli & Associates and built by       │
│  Hazama Corp (Tower 1) and Samsung C&T          │
│  (Tower 2), with extensive public records.      │
└─────────────────────────────────────────────────┘
```

```kotlin
@Composable
fun PlausibilityDetailCard(plausibility: PlausibilityAssessment) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        border = BorderStroke(1.dp, CorvusTheme.colors.border),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column {
            // Collapsed header
            Row(
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PLAUSIBILITY BREAKDOWN",
                    style = CorvusTheme.typography.label,
                    color = CorvusTheme.colors.textSecondary
                )
                Icon(
                    if (expanded) CorvusIcons.ChevronUp else CorvusIcons.ChevronDown,
                    tint = CorvusTheme.colors.textTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Expanded content
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {

                    // Plausibility spectrum bar
                    PlausibilitySpectrumBar(plausibility.score)
                    Spacer(Modifier.height(16.dp))

                    // Reasoning
                    Text("WHY",
                        style = CorvusTheme.typography.label,
                        color = CorvusTheme.colors.textSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(plausibility.reasoning,
                        style = CorvusTheme.typography.body)

                    // Closest evidence
                    plausibility.closestEvidence?.let { evidence ->
                        Spacer(Modifier.height(12.dp))
                        Text("CLOSEST EVIDENCE",
                            style = CorvusTheme.typography.label,
                            color = CorvusTheme.colors.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(evidence,
                            style = CorvusTheme.typography.body,
                            color = CorvusTheme.colors.textSecondary)
                    }
                }
            }
        }
    }
}
```

### Plausibility Spectrum Bar

A horizontal position indicator showing where on the spectrum the claim sits.

```kotlin
@Composable
fun PlausibilitySpectrumBar(score: PlausibilityScore) {
    val position = when (score) {
        PlausibilityScore.IMPLAUSIBLE -> 0.05f
        PlausibilityScore.UNLIKELY    -> 0.25f
        PlausibilityScore.NEUTRAL     -> 0.50f
        PlausibilityScore.PLAUSIBLE   -> 0.75f
        PlausibilityScore.PROBABLE    -> 0.95f
    }

    Column {
        // Score label
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("IMPLAUSIBLE",
                style = CorvusTheme.typography.caption,
                color = CorvusTheme.colors.verdictFalse)
            Text(score.displayLabel().uppercase(),
                style = CorvusTheme.typography.label,
                color = score.labelColor())
            Text("PROBABLE",
                style = CorvusTheme.typography.caption,
                color = CorvusTheme.colors.verdictTrue)
        }
        Spacer(Modifier.height(6.dp))

        // Gradient track with position dot
        Box(modifier = Modifier.fillMaxWidth().height(6.dp)) {
            // Gradient bar
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                CorvusTheme.colors.verdictFalse,
                                CorvusTheme.colors.verdictMisleading,
                                CorvusTheme.colors.textSecondary,
                                CorvusTheme.colors.verdictTrue.copy(alpha = 0.7f),
                                CorvusTheme.colors.verdictTrue
                            )
                        )
                    )
            )

            // Position indicator dot
            val animatedPosition by animateFloatAsState(
                targetValue = position,
                animationSpec = tween(600, easing = FastOutSlowInEasing)
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .align(Alignment.CenterStart)
                    .offset(x = with(LocalDensity.current) {
                        (animatedPosition * (LocalConfiguration.current.screenWidthDp.dp - 48.dp)).toPx().toDp()
                    })
                    .background(Color.White, CircleShape)
                    .border(2.dp, score.labelColor(), CircleShape)
            )
        }
    }
}
```

### UNVERIFIABLE in History List

History list items with `UNVERIFIABLE` verdict show the plausibility sub-label inline.

```kotlin
// In HistoryItemRow
if (item.verdict == Verdict.UNVERIFIABLE && item.plausibilityScore != null) {
    Text(
        item.plausibilityScore.displayLabel(),
        style = CorvusTheme.typography.caption,
        color = item.plausibilityScore.labelColor()
    )
}
```

### Share / Export — Plausibility Label

```
CORVUS FACT-CHECK
Verdict: UNVERIFIABLE
Plausibility: High Implausibility
"No direct evidence exists, but this claim contradicts all documented
construction records and established engineering science."
Checked: 20 Mar 2026 · corvus.app
```

---

## Combined JSON Schema (Both Features Together)

Full prompt response schema with harm + plausibility both integrated:

```json
{
  "verdict": "TRUE|FALSE|MISLEADING|PARTIALLY_TRUE|UNVERIFIABLE",
  "confidence": 0.85,
  "explanation": "...",
  "key_facts": ["fact 1", "fact 2"],
  "sources_used": [0, 1],
  "harm_assessment": {
    "level": "NONE|LOW|MODERATE|HIGH",
    "category": "NONE|HEALTH|SAFETY|RACIAL_ETHNIC|RELIGIOUS|POLITICAL|FINANCIAL",
    "reason": "One sentence. Empty string if NONE."
  },
  "plausibility": {
    "score": "IMPLAUSIBLE|UNLIKELY|NEUTRAL|PLAUSIBLE|PROBABLE",
    "reasoning": "1-2 sentences. Only populated if verdict is UNVERIFIABLE.",
    "closest_evidence": "Best available evidence. Null if not applicable."
  }
}
```

`plausibility` is `null` in the JSON whenever verdict is not `UNVERIFIABLE`.

---

## Tasks — Plausibility Spectrum

- [ ] Add `PlausibilityScore` enum
- [ ] Add `PlausibilityAssessment` data class
- [ ] Extend `CorvusCheckResult.GeneralResult` with `plausibility` nullable field
- [ ] Extend `CorvusHistoryEntity` with `plausibilityScore` nullable column + Room migration
- [ ] Add plausibility conditional block to all LLM prompt templates
- [ ] Implement `parsePlausibility()` with null guard for non-UNVERIFIABLE verdicts
- [ ] Implement `PlausibilityEnricherUseCase` (secondary Groq call for UNVERIFIABLE only)
- [ ] Wire enricher into pipeline — triggered only when initial verdict is `UNVERIFIABLE`
- [ ] Implement `PlausibilitySpectrumBar` composable with animated position dot
- [ ] Implement `PlausibilityDetailCard` composable with expand/collapse
- [ ] Update `VerdictBadge` to show plausibility sub-label when verdict is `UNVERIFIABLE`
- [ ] Update `HistoryItemRow` with plausibility inline label for UNVERIFIABLE items
- [ ] Update share/export template with plausibility label
- [ ] Unit test: `parsePlausibility()` — null for non-UNVERIFIABLE, all 5 scores parsed
- [ ] Unit test: `PlausibilityEnricherUseCase` — skips enrichment for IMPLAUSIBLE/PROBABLE
- [ ] Integration test: aliens/Petronas claim → UNVERIFIABLE + IMPLAUSIBLE
- [ ] Integration test: private meeting claim → UNVERIFIABLE + NEUTRAL or PLAUSIBLE
- [ ] Integration test: FALSE claim → plausibility is null

**Estimated duration: 5 days**

---

---

## Combined Roadmap

| Phase | Feature | Duration | Notes |
|---|---|---|---|
| 1 | Harm Potential Flag | 4 days | Extend prompt schema + new UI components |
| 2 | Plausibility Spectrum | 5 days | Conditional enricher pass + spectrum UI |
| **Total** | | **~9 days** | Both share the same prompt schema extension — build together |

**Recommended:** Implement both in the same sprint. They both extend the same LLM JSON response schema, so the prompt engineering and parser work overlaps significantly. Building them together saves ~1.5 days versus building sequentially.

---

## New Dependencies

None. Both features use existing Groq/Gemini clients, existing Compose UI toolkit, and existing Room DB with schema migrations.

---

## New API Keys Required

None.

---

## Definition of Done

**Harm Potential**
- `harm_assessment` field present in all LLM responses with correct enum values
- Bleach/Covid claim returns `HIGH` + `HEALTH` harm assessment
- Sports score error returns `NONE` harm assessment
- `HarmPreScreener` correctly flags 5 known high-harm patterns
- HIGH harm verdict card renders with intensified red border + pulsing warning block
- MODERATE harm renders with amber inline tag only
- NONE harm renders standard card with no additional elements
- Room migration completes without data loss on existing history records
- HIGH harm dot visible on history list items

**Plausibility Spectrum**
- `plausibility` field is `null` for all non-UNVERIFIABLE verdicts
- All 5 `PlausibilityScore` values parse correctly from LLM JSON
- `PlausibilityEnricherUseCase` fires only for UNVERIFIABLE verdicts
- Enricher skips secondary call for IMPLAUSIBLE and PROBABLE (already strong signal)
- Spectrum bar renders with correct gradient and animated position dot
- Dot position animates smoothly on result reveal (600ms, FastOutSlowIn)
- Sub-label color matches plausibility score (red → green spectrum)
- `PlausibilityDetailCard` expands/collapses correctly
- Aliens/Petronas claim test → UNVERIFIABLE + IMPLAUSIBLE end-to-end
- Private meeting claim test → UNVERIFIABLE + NEUTRAL or PLAUSIBLE end-to-end
- History list shows plausibility sub-label on UNVERIFIABLE items
