# Corvus — Enhancement Plan VII
## LLM Pipeline Quality Fixes
### Snippet Length · Actor-Critic Architecture · Chain-of-Thought · Algorithmic Grounding

> **Scope:** Four targeted fixes to the core LLM reasoning pipeline. These are not new features — they are correctness and reliability upgrades that directly address hallucination, sycophancy, shallow context, and fabricated citations. Combined, they transform Corvus from a system that *looks* like it's doing RAG into one that *actually* does it.

---

## Why These Fixes Are Critical

The current pipeline has a structural flaw that compounds across every check:

```
150-char snippet                   → LLM has no real evidence to reason from
Single zero-shot prompt            → Verdict decided before evidence processed
Verdict first in JSON              → LLM commits to answer before reasoning
No programmatic citation check     → Fabricated source indices reach the UI
```

Each flaw amplifies the others. A 150-char snippet gives the LLM nothing to ground its reasoning in, so it falls back on pre-trained biases. The verdict-first schema means it commits to an answer before working through that thin evidence. The single-pass architecture has no critic to catch the resulting sycophancy. And fabricated citations make it all look credible in the UI.

These four fixes address each layer systematically.

---

## Fix 1 — MAX_SNIPPET_LENGTH Bottleneck

### The Problem

```kotlin
// LlmRepository.kt — current state
private const val MAX_SNIPPET_LENGTH = 150  // ← Critical bottleneck

val sourceContext = sources.mapIndexed { i, source ->
    "[$i] ${source.title}: ${source.snippet?.take(MAX_SNIPPET_LENGTH)}"
}.joinToString("\n")
```

150 characters is approximately 30 words — one or two sentences. For a source article that contains the critical paragraph three paragraphs in, the LLM never sees it. It receives only the lede, which may not contain the relevant evidence at all.

At 150 chars, the LLM is not doing RAG. It is doing:
- Pattern matching against its training data
- Surface-level keyword matching on the source title
- Confabulation dressed up as citation

### The Fix

Increase snippet length to 2,500 characters (approximately 500 words — a solid evidence paragraph), with per-provider context-aware scaling:

```kotlin
// LlmRepository.kt — updated constants

// Context budget per source in characters
// Calibrated to provider context windows minus prompt overhead
private val SNIPPET_LENGTH_BY_PROVIDER = mapOf(
    LlmProvider.GEMINI     to 3000,   // 1M token window — generous
    LlmProvider.GROQ       to 2500,   // 128k window — generous
    LlmProvider.CEREBRAS   to 800,    // 8k window — must be conservative
    LlmProvider.OPENROUTER to 2000    // Varies by model — conservative default
)

private fun getSnippetLength(provider: LlmProvider): Int =
    SNIPPET_LENGTH_BY_PROVIDER[provider] ?: 1500

// Max total source context (all sources combined) to prevent prompt overflow
private val MAX_TOTAL_CONTEXT_BY_PROVIDER = mapOf(
    LlmProvider.GEMINI     to 15_000,
    LlmProvider.GROQ       to 12_000,
    LlmProvider.CEREBRAS   to 4_000,
    LlmProvider.OPENROUTER to 8_000
)
```

### Source Context Builder — Updated

```kotlin
// data/remote/llm/SourceContextBuilder.kt — new utility class

class SourceContextBuilder @Inject constructor() {

    fun build(
        sources  : List<CorvusSource>,
        provider : LlmProvider
    ): String {
        val snippetLength = getSnippetLength(provider)
        val maxTotal      = MAX_TOTAL_CONTEXT_BY_PROVIDER[provider] ?: 8_000

        // Sort by credibility — most reliable sources first in context window
        val sorted = sources.sortedByDescending {
            it.credibilityTier.ordinal
        }

        val builder   = StringBuilder()
        var totalChars = 0

        sorted.forEachIndexed { index, source ->
            if (totalChars >= maxTotal) return@forEachIndexed

            // Prefer rawContent (full article), fall back to snippet
            val contentText = (source.rawContent ?: source.snippet ?: "")
                .take(snippetLength)

            // Trim to whole sentence boundary where possible
            val trimmed = trimToSentenceBoundary(contentText, snippetLength)

            val entry = buildString {
                appendLine("--- SOURCE [$index] ---")
                appendLine("Publisher : ${source.publisher ?: "Unknown"}")
                appendLine("Title     : ${source.title}")
                source.publishedDate?.let { appendLine("Date      : $it") }
                appendLine("URL       : ${source.url}")
                appendLine("Credibility: ${source.credibilityTier.name}")
                appendLine()
                appendLine("CONTENT:")
                appendLine(trimmed)
                appendLine()
            }

            if (totalChars + entry.length <= maxTotal) {
                builder.append(entry)
                totalChars += entry.length
            }
        }

        return builder.toString()
    }

    // Trim to nearest sentence end to avoid mid-sentence cuts
    private fun trimToSentenceBoundary(text: String, maxLength: Int): String {
        if (text.length <= maxLength) return text

        val truncated = text.take(maxLength)
        val lastSentenceEnd = truncated.lastIndexOfAny(charArrayOf('.', '!', '?'))

        return if (lastSentenceEnd > maxLength * 0.7) {
            // Found a sentence boundary in the last 30% — use it
            truncated.take(lastSentenceEnd + 1)
        } else {
            // No good boundary — use word boundary instead
            val lastSpace = truncated.lastIndexOf(' ')
            truncated.take(if (lastSpace > 0) lastSpace else maxLength) + "..."
        }
    }

    private fun getSnippetLength(provider: LlmProvider) =
        SNIPPET_LENGTH_BY_PROVIDER[provider] ?: 1500
}
```

### Tasks — Fix 1

- [ ] Replace `MAX_SNIPPET_LENGTH = 150` constant with `SNIPPET_LENGTH_BY_PROVIDER` map
- [ ] Create `SourceContextBuilder` utility class
- [ ] Implement `trimToSentenceBoundary()` — no mid-sentence truncation
- [ ] Implement `MAX_TOTAL_CONTEXT_BY_PROVIDER` per-provider total budget
- [ ] Sort sources by credibility tier before building context
- [ ] Include structured source header (Publisher, Title, Date, URL, Credibility)
- [ ] Update all pipeline callers to use `SourceContextBuilder`
- [ ] Add `rawContent` preference over `snippet` in context builder
- [ ] Unit test: `trimToSentenceBoundary()` — 5 boundary cases
- [ ] Unit test: total context stays within `MAX_TOTAL_CONTEXT_BY_PROVIDER` limit
- [ ] Unit test: sources sorted by credibility before context build
- [ ] Integration test: verify Cerebras context stays under 4,000 chars total
- [ ] Regression test: Gemini and Groq receive 2,500-3,000 chars per source

**Estimated duration: 2 days**

---

---

## Fix 2 — Actor-Critic LLM Architecture

### The Problem

A single zero-shot prompt asks the LLM to simultaneously:
1. Read and understand N sources
2. Identify relevant evidence
3. Form a verdict
4. Write an explanation
5. Ground facts with citations
6. Assess harm
7. Assess plausibility

This is too many cognitive tasks for one pass. The LLM shortcuts by:
- Deciding the verdict in the first token (based on claim sentiment, not evidence)
- Constructing post-hoc justification for that pre-committed verdict
- Citing whichever source index seems plausible, not the one it actually read

This is sycophancy — the model agrees with the framing of the claim rather than interrogating the evidence.

### The Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  ACTOR (Pass 1)                                                 │
│  Provider: Groq (fast, low cost)                                │
│  Task: Read sources. Draft preliminary verdict + explanation.   │
│  Output: draft_verdict, draft_explanation, draft_facts          │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                    Actor output passed to Critic
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│  CRITIC (Pass 2)                                                │
│  Provider: Gemini (reliable, strong reasoning) or Groq          │
│  Task: Challenge the Actor's draft. Find unsupported claims.    │
│  Find wrong citations. Identify missing context.                │
│  Output: final_verdict, final_explanation, corrected_facts      │
└─────────────────────────────────────────────────────────────────┘
```

Both passes run sequentially — the Critic sees the Actor's full output. Total latency on Groq is approximately 3-5 seconds for both passes combined, which is acceptable.

### Provider Assignment Strategy

```kotlin
// Actor: Fast provider — Groq or Cerebras
// Critic: Reliable provider — Gemini or different Groq call

data class ActorCriticProviders(
    val actor  : LlmProvider,
    val critic : LlmProvider
)

fun selectActorCriticProviders(
    healthTracker : LlmProviderHealthTracker,
    prefs         : UserPreferences
): ActorCriticProviders {
    val preferred = prefs.preferredProvider

    // Actor: fastest available
    val actor = when {
        healthTracker.isAvailable(LlmProvider.GROQ)     -> LlmProvider.GROQ
        healthTracker.isAvailable(LlmProvider.CEREBRAS) -> LlmProvider.CEREBRAS
        healthTracker.isAvailable(LlmProvider.GEMINI)   -> LlmProvider.GEMINI
        else                                            -> LlmProvider.OPENROUTER
    }

    // Critic: most reliable available — prefer different provider than Actor
    val critic = when {
        healthTracker.isAvailable(LlmProvider.GEMINI) &&
        actor != LlmProvider.GEMINI                      -> LlmProvider.GEMINI
        healthTracker.isAvailable(LlmProvider.GROQ) &&
        actor != LlmProvider.GROQ                        -> LlmProvider.GROQ
        else                                             -> actor  // Same provider if no alternative
    }

    return ActorCriticProviders(actor = actor, critic = critic)
}
```

### Actor Prompt

The Actor's job is drafting only. It should not second-guess — it produces a complete but unverified draft:

```kotlin
fun buildActorPrompt(
    claim         : String,
    classified    : ClassifiedClaim,
    sourceContext : String
): String = """
You are a fact-checking analyst. Your job is to draft a preliminary fact-check
of the following claim based solely on the provided sources.

CLAIM: "$claim"
CLAIM TYPE: ${classified.type.name}
CLAIM LANGUAGE: ${classified.language.name}

SOURCES:
$sourceContext

YOUR TASK:
1. Read all sources carefully.
2. Identify which sources (if any) directly support or contradict the claim.
3. Draft a preliminary verdict and explanation.

IMPORTANT RULES:
- Base your analysis EXCLUSIVELY on the provided sources.
- Do not use internal training knowledge to fill gaps.
- If sources are insufficient, draft verdict as UNVERIFIABLE.
- For each key fact, note the specific source index it comes from.
- This is a DRAFT — it will be reviewed and corrected.

OUTPUT FORMAT — Respond ONLY with valid JSON:
{
  "reasoning_scratchpad": "Step-by-step walkthrough of what each source says and how it relates to the claim",
  "draft_verdict": "TRUE|FALSE|MISLEADING|PARTIALLY_TRUE|UNVERIFIABLE",
  "draft_confidence": 0.0,
  "draft_explanation": "Evidentiary explanation attributed to sources",
  "draft_key_facts": [
    {
      "statement": "...",
      "source_index": 0,
      "is_direct_quote": false,
      "source_text_evidence": "The exact text from the source that supports this fact"
    }
  ],
  "sources_used": [0, 1],
  "unsupported_assumptions": ["List any claims in the explanation not directly backed by sources"]
}
""".trimIndent()
```

Note the `source_text_evidence` field in `draft_key_facts` — the Actor is explicitly asked to quote the supporting text. This is fed to the Critic and the algorithmic verifier in Fix 4.

### Critic Prompt

The Critic receives the Actor's full output and the original sources. Its job is adversarial verification — finding errors, not finding agreement:

```kotlin
fun buildCriticPrompt(
    claim         : String,
    sourceContext : String,
    actorDraft    : ActorDraft
): String = """
You are a senior fact-checking editor. A junior analyst has produced a preliminary
fact-check. Your job is to rigorously verify it against the source evidence and
correct any errors.

ORIGINAL CLAIM: "$claim"

SOURCES (same sources the analyst had access to):
$sourceContext

ANALYST'S DRAFT:
Verdict    : ${actorDraft.draftVerdict}
Confidence : ${actorDraft.draftConfidence}
Explanation: ${actorDraft.draftExplanation}

Key facts drafted:
${actorDraft.draftKeyFacts.mapIndexed { i, f ->
    "  [$i] \"${f.statement}\" — attributed to Source [${f.sourceIndex}]\n" +
    "      Analyst's evidence: ${f.sourceTextEvidence ?: "None provided"}"
}.joinToString("\n")}

Unsupported assumptions flagged by analyst:
${actorDraft.unsupportedAssumptions.joinToString("\n") { "  - $it" }}

YOUR REVIEW TASKS — for each item, verify then correct:

1. CITATION AUDIT: For each key fact, check whether the quoted
   source_text_evidence actually appears in the assigned source.
   If it does not, either find the correct source index or strip the citation.

2. VERDICT AUDIT: Does the evidence actually support the drafted verdict?
   If the analyst has been sycophantic (agreed with claim without evidence),
   correct the verdict. Do not soften — be precise.

3. CONFIDENCE AUDIT: Is the confidence score appropriate for the evidence quality?
   Penalise for thin evidence, unresolved contradictions, or missing sources.

4. EXPLANATION AUDIT: Rewrite any sentence that asserts something not explicitly
   stated in the provided sources. Use "According to [source]..." framing.
   Remove any sentence that cannot be attributed to a source.

5. MISSING CONTEXT: Is there critical context missing that changes the claim's
   meaning? If so, populate missing_context.

OUTPUT FORMAT — Respond ONLY with valid JSON (this is the FINAL output):
{
  "reasoning_scratchpad": "Your step-by-step audit of the analyst's draft",
  "verdict": "TRUE|FALSE|MISLEADING|PARTIALLY_TRUE|UNVERIFIABLE",
  "confidence": 0.0,
  "explanation": "Corrected, evidence-attributed explanation",
  "key_facts": [
    {
      "statement": "...",
      "source_index": 0,
      "is_direct_quote": false,
      "critic_verified": true
    }
  ],
  "harm_assessment": {
    "level": "NONE|LOW|MODERATE|HIGH",
    "category": "NONE|HEALTH|SAFETY|RACIAL_ETHNIC|RELIGIOUS|POLITICAL|FINANCIAL",
    "reason": ""
  },
  "missing_context": {
    "content": "...",
    "context_type": "TEMPORAL|GEOGRAPHIC|ATTRIBUTION|STATISTICAL|SELECTIVE|GENERAL"
  },
  "kernel_of_truth": { ... },
  "plausibility": { ... },
  "corrections_made": [
    "List of specific corrections made to the analyst's draft"
  ]
}
""".trimIndent()
```

### Actor-Critic Pipeline Implementation

```kotlin
// domain/usecase/ActorCriticPipeline.kt

class ActorCriticPipeline @Inject constructor(
    private val groqClient      : GroqClient,
    private val geminiClient    : GeminiClient,
    private val cerebrasClient  : CerebrasClient,
    private val openRouterClient: OpenRouterClient,
    private val contextBuilder  : SourceContextBuilder,
    private val healthTracker   : LlmProviderHealthTracker,
    private val prefs           : UserPreferences
) {
    suspend fun analyze(
        claim      : String,
        classified : ClassifiedClaim,
        sources    : List<CorvusSource>
    ): CorvusCheckResult.GeneralResult {

        val providers     = selectActorCriticProviders(healthTracker, prefs)
        val actorContext  = contextBuilder.build(sources, providers.actor)
        val criticContext = contextBuilder.build(sources, providers.critic)

        // ── Pass 1: Actor ────────────────────────────────────────────────
        val actorPrompt = buildActorPrompt(claim, classified, actorContext)
        val actorRaw    = getClient(providers.actor).complete(
            prompt    = actorPrompt,
            maxTokens = 2000
        )
        val actorDraft  = parseActorDraft(actorRaw)

        // ── Pass 2: Critic ───────────────────────────────────────────────
        val criticPrompt = buildCriticPrompt(claim, criticContext, actorDraft)
        val criticRaw    = getClient(providers.critic).complete(
            prompt    = criticPrompt,
            maxTokens = 2500
        )
        val finalResult  = parseCriticOutput(criticRaw, sources)

        // Attach metadata
        return finalResult.copy(
            actorProvider  = providers.actor,
            criticProvider = providers.critic,
            correctionsLog = actorDraft.unsupportedAssumptions +
                             (finalResult.correctionsLog ?: emptyList())
        )
    }

    private fun getClient(provider: LlmProvider): LlmClient = when (provider) {
        LlmProvider.GROQ       -> groqClient
        LlmProvider.GEMINI     -> geminiClient
        LlmProvider.CEREBRAS   -> cerebrasClient
        LlmProvider.OPENROUTER -> openRouterClient
        else                   -> geminiClient
    }
}
```

### Actor Draft Model

```kotlin
data class ActorDraft(
    val reasoningScratchpad    : String,
    val draftVerdict           : Verdict,
    val draftConfidence        : Float,
    val draftExplanation       : String,
    val draftKeyFacts          : List<ActorGroundedFact>,
    val sourcesUsed            : List<Int>,
    val unsupportedAssumptions : List<String>
)

data class ActorGroundedFact(
    val statement         : String,
    val sourceIndex       : Int?,
    val isDirectQuote     : Boolean,
    val sourceTextEvidence: String?  // Actor's claimed evidence text
)
```

### Extended Result with Corrections Log

```kotlin
// Extended CorvusCheckResult.GeneralResult — add Actor-Critic metadata
data class CorvusCheckResult.GeneralResult(
    // ... all existing fields ...
    val actorProvider   : LlmProvider? = null,    // NEW
    val criticProvider  : LlmProvider? = null,    // NEW
    val correctionsLog  : List<String>? = null,   // NEW — what Critic changed
    val reasoningScratchpad: String? = null        // NEW — full CoT reasoning
)
```

### UI — Corrections Disclosure

The Methodology Card gets a new section showing what the Critic corrected:

```kotlin
// In MethodologyCard expanded section
result.correctionsLog?.takeIf { it.isNotEmpty() }?.let { corrections ->
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = CorvusTheme.colors.border)
    Spacer(Modifier.height(8.dp))

    Text(
        "CRITIC CORRECTIONS",
        style     = CorvusTheme.typography.labelSmall,
        color     = CorvusTheme.colors.textSecondary,
        fontFamily = IbmPlexMono
    )
    Spacer(Modifier.height(4.dp))

    corrections.forEach { correction ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.Top
        ) {
            Text("→", color = CorvusTheme.colors.verdictMisleading,
                style = CorvusTheme.typography.caption)
            Text(
                correction,
                style = CorvusTheme.typography.caption,
                color = CorvusTheme.colors.textSecondary
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}
```

### Tasks — Fix 2

- [ ] Create `ActorDraft` data model
- [ ] Create `ActorGroundedFact` data model with `sourceTextEvidence` field
- [ ] Implement `buildActorPrompt()` with `source_text_evidence` in schema
- [ ] Implement `buildCriticPrompt()` with explicit audit task list
- [ ] Implement `selectActorCriticProviders()` — health-aware selection
- [ ] Implement `ActorCriticPipeline` class
- [ ] Implement `parseActorDraft()` JSON parser
- [ ] Implement `parseCriticOutput()` JSON parser
- [ ] Add `actorProvider`, `criticProvider`, `correctionsLog`, `reasoningScratchpad` to `CorvusCheckResult.GeneralResult`
- [ ] Replace existing single-pass `LlmRepository.analyze()` with `ActorCriticPipeline.analyze()`
- [ ] Add corrections log section to `MethodologyCard`
- [ ] Wire Actor-Critic into Hilt module
- [ ] Unit test: `selectActorCriticProviders()` — all health combinations
- [ ] Unit test: `parseActorDraft()` — valid JSON, missing fields, malformed
- [ ] Unit test: `parseCriticOutput()` — verdict correction cases
- [ ] Integration test: sycophancy test — claim that sounds true but is false
- [ ] Integration test: Critic corrects Actor's fabricated citation
- [ ] Performance test: total Actor + Critic latency <= 8 seconds on Groq

**Estimated duration: 6 days**

---

---

## Fix 3 — Chain-of-Thought for Verdict

### The Problem

Current JSON schema forces verdict first:

```json
{
  "verdict": "FALSE",      ← LLM commits here, at token 1
  "confidence": 0.88,
  "explanation": "..."     ← Justification constructed post-hoc
}
```

Because LLMs generate tokens sequentially, outputting `verdict` as the first key means the model has committed to an answer before generating the explanation. Everything that follows is post-hoc rationalisation — the explanation justifies the verdict rather than causing it.

This is not unique to smaller models. Even Gemini and GPT-4o show measurable accuracy improvement when forced to reason before committing.

### The Fix

Restructure the JSON schema so reasoning comes first and verdict comes last. This is the Chain-of-Thought (CoT) principle applied to structured output:

```json
{
  "evidentiary_analysis": "FIRST — reason through the evidence",
  "key_facts": [],          ← SECOND — extract grounded facts from analysis
  "missing_context": {},    ← THIRD — identify what context is absent
  "harm_assessment": {},    ← FOURTH — assess harm
  "kernel_of_truth": {},    ← FIFTH — split true/false parts if misleading
  "plausibility": {},       ← SIXTH — assess plausibility if unverifiable
  "confidence": 0.0,        ← SEVENTH — confidence based on evidence quality
  "verdict": "..."          ← LAST — conclusion drawn from all of the above
}
```

By the time the model generates the `verdict` token, it has already:
- Written out its evidential reasoning
- Identified and cited key facts
- Acknowledged missing context
- Assessed harm
- Measured its own confidence

The verdict is now a *conclusion* rather than a *premise*.

### Updated Schema Instruction

```kotlin
// In buildActorPrompt() and buildCriticPrompt()

const val CHAIN_OF_THOUGHT_INSTRUCTION = """
OUTPUT SCHEMA RULES:
You MUST output the JSON keys in the EXACT ORDER listed below.
Do NOT reorder them. This order is deliberate:
the verdict must be derived from your analysis, not the other way around.

Step 1 — evidentiary_analysis:
  Walk through each source methodically. For each source, note:
  - What does it explicitly say about the claim?
  - Does it support, contradict, or not address the claim?
  - How credible is this source relative to the others?
  Then synthesise what the combined evidence shows.
  This field MUST be at least 3 sentences. Write your full reasoning here.

Step 2 — key_facts (grounded citations)
Step 3 — missing_context (if applicable)
Step 4 — harm_assessment
Step 5 — kernel_of_truth (if MISLEADING or PARTIALLY_TRUE)
Step 6 — plausibility (if UNVERIFIABLE)
Step 7 — confidence (0.0–1.0, based on evidence strength, not gut feeling)
Step 8 — verdict (YOUR CONCLUSION — drawn from everything above)
Step 9 — explanation (A clean, reader-facing summary of your evidentiary_analysis)

IMPORTANT: The verdict is the LAST reasoning step, not the first.
If your evidentiary_analysis leads to a different conclusion than you initially
expected, trust the evidence and correct your verdict accordingly.
"""
```

### Full CoT JSON Schema

```kotlin
const val COT_JSON_SCHEMA = """
Respond ONLY with valid JSON in this EXACT key order:
{
  "evidentiary_analysis": "Your step-by-step reasoning through the evidence. Minimum 3 sentences. Source-attributed.",

  "key_facts": [
    {
      "statement": "Specific factual claim",
      "source_index": 0,
      "is_direct_quote": false,
      "source_text_evidence": "Exact text from source supporting this fact"
    }
  ],

  "missing_context": {
    "content": "Critical context absent from the claim",
    "context_type": "TEMPORAL|GEOGRAPHIC|ATTRIBUTION|STATISTICAL|SELECTIVE|GENERAL"
  },

  "harm_assessment": {
    "level": "NONE|LOW|MODERATE|HIGH",
    "category": "NONE|HEALTH|SAFETY|RACIAL_ETHNIC|RELIGIOUS|POLITICAL|FINANCIAL",
    "reason": ""
  },

  "kernel_of_truth": {
    "true_parts":  [...],
    "false_parts": [...],
    "twist_explanation": "..."
  },

  "plausibility": {
    "score": "IMPLAUSIBLE|UNLIKELY|NEUTRAL|PLAUSIBLE|PROBABLE",
    "reasoning": "...",
    "closest_evidence": "..."
  },

  "confidence": 0.0,

  "verdict": "TRUE|FALSE|MISLEADING|PARTIALLY_TRUE|UNVERIFIABLE",

  "explanation": "3-5 sentence reader-facing summary. Evidence-attributed. Written after the verdict is determined."
}

CONDITIONAL FIELDS:
- missing_context   : null if verdict is TRUE or FALSE with no context issue
- kernel_of_truth   : null unless verdict is MISLEADING or PARTIALLY_TRUE
- plausibility      : null unless verdict is UNVERIFIABLE
"""
```

### Parser — Scratchpad Extraction

```kotlin
fun parseCoTResponse(raw: String): CorvusCheckResult.GeneralResult {
    val json = extractAndParseJson(raw)

    // Extract reasoning scratchpad — valuable for debugging and Methodology card
    val reasoningScratchpad = json["evidentiary_analysis"]
        ?.jsonPrimitive?.content
        ?.takeIf { it.isNotBlank() }

    // Verdict is now the LAST field — parse as normal
    val verdict = parseVerdict(json["verdict"]?.jsonPrimitive?.content)

    // Confidence comes before verdict — sanity check it
    val rawConfidence = json["confidence"]?.jsonPrimitive?.floatOrNull ?: 0.5f
    val confidence    = rawConfidence.coerceIn(0f, 1f)

    // Standard field parsing
    val keyFacts   = parseKeyFacts(json, verdict)
    val explanation = json["explanation"]?.jsonPrimitive?.content ?: ""

    return CorvusCheckResult.GeneralResult(
        verdict                = verdict,
        confidence             = confidence,
        explanation            = explanation,
        keyFacts               = keyFacts,
        sources                = emptyList(),  // Injected by pipeline after parse
        claimType              = ClaimType.GENERAL,
        harmAssessment         = parseHarmAssessment(json),
        plausibility           = parsePlausibility(json, verdict),
        missingContext         = parseMissingContext(json, verdict),
        kernelOfTruth          = parseKernelOfTruth(json, verdict),
        reasoningScratchpad    = reasoningScratchpad
    )
}
```

### UI — Reasoning Scratchpad (Power User Feature)

The `evidentiary_analysis` field is surfaced in the Methodology Card as a collapsible "Full Reasoning" section:

```kotlin
// In MethodologyCard expanded section — at the bottom

result.reasoningScratchpad?.let { scratchpad ->
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = CorvusTheme.colors.border)
    Spacer(Modifier.height(8.dp))

    var showReasoning by remember { mutableStateOf(false) }

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clickable { showReasoning = !showReasoning }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            "FULL REASONING",
            style     = CorvusTheme.typography.labelSmall,
            color     = CorvusTheme.colors.textSecondary,
            fontFamily = IbmPlexMono
        )
        Icon(
            if (showReasoning) CorvusIcons.ChevronUp else CorvusIcons.ChevronDown,
            modifier = Modifier.size(14.dp),
            tint     = CorvusTheme.colors.textTertiary
        )
    }

    AnimatedVisibility(visible = showReasoning) {
        Text(
            text      = scratchpad,
            style     = CorvusTheme.typography.caption,
            color     = CorvusTheme.colors.textSecondary,
            modifier  = Modifier.padding(top = 6.dp),
            lineHeight = 18.sp
        )
    }
}
```

### Tasks — Fix 3

- [ ] Define `CHAIN_OF_THOUGHT_INSTRUCTION` constant
- [ ] Define `COT_JSON_SCHEMA` constant with enforced key ordering
- [ ] Update all LLM prompt builders to use CoT schema
- [ ] Update `parseCoTResponse()` — extract `evidentiary_analysis` as `reasoningScratchpad`
- [ ] Verify JSON parser handles verdict as last key correctly
- [ ] Add `reasoningScratchpad` field to `CorvusCheckResult.GeneralResult`
- [ ] Add "FULL REASONING" collapsible section to `MethodologyCard`
- [ ] Update Actor prompt to use CoT schema
- [ ] Update Critic prompt to use CoT schema
- [ ] Regression test: parser handles verdict in last position correctly
- [ ] Accuracy test: 10 known TRUE/FALSE claims — measure verdict accuracy vs old schema
- [ ] Unit test: `reasoningScratchpad` extracted and non-null when LLM provides it
- [ ] Unit test: `confidence` value clamped to 0.0–1.0

**Estimated duration: 3 days**

---

---

## Fix 4 — Algorithmic Grounding Verification

### The Problem

Even with Fix 2 (Actor-Critic) and Fix 3 (CoT), the LLM can still output:

```json
{
  "statement": "The Prime Minister stated this in Parliament on 12 March 2024.",
  "source_index": 1,
  "is_direct_quote": true
}
```

...when Source 1 contains no such statement. The Critic may catch some of these, but it is another LLM — it can also hallucinate. The only way to guarantee no fabricated direct-quote citation reaches the UI is a deterministic, algorithmic check.

### The Guarantee

```
For every GroundedFact where is_direct_quote == true:
    Extract the quoted text from the statement
    Search source[source_index].rawContent for that text
    If not found (Levenshtein distance > threshold):
        Strip source_index → null
        Set is_direct_quote → false
        Set verification.confidence → LOW_CONFIDENCE
        Reduce overall CorvusCheckResult.confidence by penalty factor
```

For non-direct-quote facts (paraphrases, summaries), the RAG Verifier from Fix VI handles keyword-based verification. Fix 4 specifically targets `is_direct_quote: true` claims — the highest-confidence, highest-risk category.

### Levenshtein Distance Utility

```kotlin
// domain/util/StringSimilarity.kt

object StringSimilarity {

    // Normalised Levenshtein similarity: 0.0 (completely different) → 1.0 (identical)
    fun normalizedLevenshtein(a: String, b: String): Float {
        if (a == b) return 1.0f
        if (a.isEmpty() || b.isEmpty()) return 0.0f

        val maxLength = maxOf(a.length, b.length)
        val distance  = levenshteinDistance(a, b)
        return 1f - (distance.toFloat() / maxLength)
    }

    // Standard Levenshtein distance — space-optimised DP
    private fun levenshteinDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length

        var previousRow = IntArray(n + 1) { it }
        var currentRow  = IntArray(n + 1)

        for (i in 1..m) {
            currentRow[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                currentRow[j] = minOf(
                    currentRow[j - 1] + 1,         // Insertion
                    previousRow[j] + 1,             // Deletion
                    previousRow[j - 1] + cost       // Substitution
                )
            }
            previousRow = currentRow.also { currentRow = previousRow }
        }

        return previousRow[n]
    }

    // Sliding window search — find best match of query within a longer text
    // Used when quote may be a substring of the source content
    fun bestSubstringMatch(query: String, text: String): Float {
        if (query.length > text.length) return 0f
        if (text.contains(query, ignoreCase = true)) return 1.0f

        // Slide query-sized window across text
        val windowSize  = query.length
        var bestScore   = 0f

        for (i in 0..(text.length - windowSize)) {
            val window = text.substring(i, i + windowSize)
            val score  = normalizedLevenshtein(
                query.lowercase(),
                window.lowercase()
            )
            if (score > bestScore) bestScore = score
            if (bestScore >= 0.95f) break   // Good enough — stop early
        }

        return bestScore
    }
}
```

### Algorithmic Grounding Verifier

```kotlin
// domain/usecase/AlgorithmicGroundingVerifier.kt

class AlgorithmicGroundingVerifier @Inject constructor() {

    companion object {
        // Similarity thresholds
        const val DIRECT_QUOTE_VERIFIED_THRESHOLD = 0.80f   // 80% match = verified quote
        const val DIRECT_QUOTE_PARTIAL_THRESHOLD  = 0.55f   // 55-79% = partial match
        // Below 55% → citation stripped, flagged LOW_CONFIDENCE

        // Confidence penalties applied to overall result
        const val FABRICATED_CITATION_PENALTY     = 0.15f   // Per fabricated direct quote
        const val MAX_TOTAL_PENALTY               = 0.40f   // Cap total penalty
    }

    data class GroundingVerificationResult(
        val verifiedFacts         : List<GroundedFact>,
        val totalConfidencePenalty: Float,
        val fabricatedCitations   : List<FabricatedCitation>
    )

    data class FabricatedCitation(
        val factIndex      : Int,
        val originalStatement: String,
        val claimedSourceIndex: Int,
        val bestSimilarityScore: Float
    )

    fun verify(
        facts   : List<GroundedFact>,
        sources : List<CorvusSource>
    ): GroundingVerificationResult {

        val verifiedFacts          = mutableListOf<GroundedFact>()
        val fabricatedCitations    = mutableListOf<FabricatedCitation>()
        var totalPenalty           = 0f

        facts.forEachIndexed { index, fact ->

            // Only algorithmically verify direct quotes
            // Paraphrases handled by RAG Verifier (Fix VI Feature 1)
            if (!fact.isDirectQuote || fact.sourceIndex == null) {
                verifiedFacts.add(fact)
                return@forEachIndexed
            }

            val claimedSource = sources.getOrNull(fact.sourceIndex)
            val sourceText    = claimedSource?.rawContent
                ?: claimedSource?.snippet
                ?: ""

            if (sourceText.isBlank()) {
                // Source has no text to verify against — cannot confirm, flag it
                verifiedFacts.add(
                    fact.copy(
                        isDirectQuote = false,   // Downgrade from direct quote
                        sourceIndex   = null,    // Strip unverifiable citation
                        verification  = FactVerification(
                            factIndex          = index,
                            confidence         = CitationConfidence.LOW_CONFIDENCE,
                            coverageScore      = 0f,
                            matchedFragment    = null,
                            matchedSourceIndex = null
                        )
                    )
                )
                totalPenalty = minOf(totalPenalty + FABRICATED_CITATION_PENALTY, MAX_TOTAL_PENALTY)
                return@forEachIndexed
            }

            // Extract the quoted portion from the statement
            val quotedText = extractQuotedText(fact.statement)
                ?: fact.statement   // If no quotes found, use full statement

            // Search for the quote in the source text
            val similarityScore = StringSimilarity.bestSubstringMatch(quotedText, sourceText)

            when {
                similarityScore >= DIRECT_QUOTE_VERIFIED_THRESHOLD -> {
                    // Quote verified — find and attach the matched fragment
                    val fragment = findMatchedFragment(quotedText, sourceText)
                    verifiedFacts.add(
                        fact.copy(
                            verification = FactVerification(
                                factIndex          = index,
                                confidence         = CitationConfidence.VERIFIED,
                                coverageScore      = similarityScore,
                                matchedFragment    = fragment,
                                matchedSourceIndex = fact.sourceIndex
                            )
                        )
                    )
                }

                similarityScore >= DIRECT_QUOTE_PARTIAL_THRESHOLD -> {
                    // Partial match — downgrade from direct quote but keep citation
                    verifiedFacts.add(
                        fact.copy(
                            isDirectQuote = false,   // No longer claiming verbatim
                            verification  = FactVerification(
                                factIndex          = index,
                                confidence         = CitationConfidence.PARTIAL,
                                coverageScore      = similarityScore,
                                matchedFragment    = null,
                                matchedSourceIndex = fact.sourceIndex
                            )
                        )
                    )
                }

                else -> {
                    // Fabricated citation — strip it
                    fabricatedCitations.add(
                        FabricatedCitation(
                            factIndex              = index,
                            originalStatement      = fact.statement,
                            claimedSourceIndex     = fact.sourceIndex,
                            bestSimilarityScore    = similarityScore
                        )
                    )
                    totalPenalty = minOf(
                        totalPenalty + FABRICATED_CITATION_PENALTY,
                        MAX_TOTAL_PENALTY
                    )

                    // Strip citation — fact remains but is unattributed
                    verifiedFacts.add(
                        fact.copy(
                            sourceIndex   = null,
                            isDirectQuote = false,
                            verification  = FactVerification(
                                factIndex          = index,
                                confidence         = CitationConfidence.LOW_CONFIDENCE,
                                coverageScore      = similarityScore,
                                matchedFragment    = null,
                                matchedSourceIndex = null
                            )
                        )
                    )
                }
            }
        }

        return GroundingVerificationResult(
            verifiedFacts          = verifiedFacts,
            totalConfidencePenalty = totalPenalty,
            fabricatedCitations    = fabricatedCitations
        )
    }

    // Extract text within quotation marks from a fact statement
    private fun extractQuotedText(statement: String): String? {
        val patterns = listOf(
            Regex(""""([^"]{10,})""""),          // Standard double quotes
            Regex("""'([^']{10,})'"""),           // Single quotes
            Regex("""\u201C([^\u201D]{10,})\u201D""")  // Unicode smart quotes
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(statement)?.groupValues?.getOrNull(1)
        }
    }

    // Find the sentence in source text that best matches the quote
    private fun findMatchedFragment(quote: String, sourceText: String): String? {
        val sentences = sourceText.split(Regex("[.!?]\\s+"))
        return sentences
            .map { sentence ->
                sentence to StringSimilarity.normalizedLevenshtein(
                    quote.lowercase().take(100),
                    sentence.lowercase().take(100)
                )
            }
            .filter { (_, score) -> score >= 0.50f }
            .maxByOrNull { (_, score) -> score }
            ?.first
            ?.let { "\"...${it.trim()}...\"" }
    }
}
```

### Pipeline Integration

```kotlin
// In GeneralFactCheckPipeline — after Actor-Critic analysis

suspend fun analyze(
    claim      : String,
    classified : ClassifiedClaim,
    sources    : List<CorvusSource>
): CorvusCheckResult.GeneralResult {

    // Actor-Critic analysis
    val llmResult = actorCriticPipeline.analyze(claim, classified, sources)

    // ── Fix 4: Algorithmic Grounding Verification ────────────────────
    val groundingResult = algorithmicVerifier.verify(llmResult.keyFacts, sources)

    // Apply confidence penalty for fabricated citations
    val penalisedConfidence = (llmResult.confidence - groundingResult.totalConfidencePenalty)
        .coerceAtLeast(0.05f)   // Floor at 5% — never zero

    // Log fabricated citations for Methodology card
    val fabricationLog = groundingResult.fabricatedCitations.map { fab ->
        "Citation stripped: fact [${ fab.factIndex }] was not found in Source [${fab.claimedSourceIndex}] " +
        "(similarity: ${(fab.bestSimilarityScore * 100).roundToInt()}%)"
    }
    // ────────────────────────────────────────────────────────────────

    return llmResult.copy(
        keyFacts       = groundingResult.verifiedFacts,
        confidence     = penalisedConfidence,
        correctionsLog = (llmResult.correctionsLog ?: emptyList()) + fabricationLog
    )
}
```

### UI — Fabrication Warning

When fabricated citations are stripped, show a warning in the Methodology Card:

```kotlin
// In MethodologyCard
if (fabricatedCitations.isNotEmpty()) {
    Spacer(Modifier.height(8.dp))

    Surface(
        color  = CorvusTheme.colors.verdictFalse.copy(alpha = 0.07f),
        shape  = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, CorvusTheme.colors.verdictFalse.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                "${fabricatedCitations.size} CITATION(S) STRIPPED",
                style     = CorvusTheme.typography.labelSmall,
                color     = CorvusTheme.colors.verdictFalse,
                fontFamily = IbmPlexMono
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Corvus detected ${fabricatedCitations.size} direct-quote citation(s) " +
                "that could not be verified in the assigned source. " +
                "These citations have been removed and the confidence score adjusted.",
                style = CorvusTheme.typography.caption,
                color = CorvusTheme.colors.textSecondary
            )
        }
    }
}
```

### Tasks — Fix 4

- [ ] Implement `StringSimilarity.normalizedLevenshtein()` — space-optimised DP
- [ ] Implement `StringSimilarity.bestSubstringMatch()` — sliding window
- [ ] Add early exit when score >= 0.95 in sliding window
- [ ] Implement `AlgorithmicGroundingVerifier.verify()`
- [ ] Implement `extractQuotedText()` — standard, single, and smart quote patterns
- [ ] Implement `findMatchedFragment()` — sentence-level match
- [ ] Integrate verifier into `GeneralFactCheckPipeline` after Actor-Critic
- [ ] Implement confidence penalty accumulation with `MAX_TOTAL_PENALTY` cap
- [ ] Implement floor at 0.05f — never zero confidence
- [ ] Add `fabricatedCitations` to `correctionsLog` in result
- [ ] Implement fabrication warning block in `MethodologyCard`
- [ ] Unit test: `normalizedLevenshtein()` — identical, similar, different strings
- [ ] Unit test: `bestSubstringMatch()` — exact substring, fuzzy match, no match
- [ ] Unit test: `extractQuotedText()` — all 3 quote patterns + no-quote fallback
- [ ] Unit test: direct quote verified when present in source (similarity >= 0.80)
- [ ] Unit test: direct quote stripped when absent from source (similarity < 0.55)
- [ ] Unit test: confidence penalty applied correctly, capped at MAX_TOTAL_PENALTY
- [ ] Unit test: non-direct-quote facts pass through verifier unchanged
- [ ] Integration test: fabricated quote citation → stripped + Methodology warning
- [ ] Integration test: real verbatim quote → VERIFIED with matched fragment
- [ ] Integration test: paraphrased quote → downgraded to PARTIAL, kept in results

**Estimated duration: 5 days**

---

---

## Combined Architecture — All Four Fixes

This is the complete updated pipeline flow with all four fixes applied:

```
Raw claim
    ↓
[Classifier] → ClassifiedClaim (existing)
    ↓
[Viral Detector] parallel (existing)
    ↓
[Retrieval Layer] → List<CorvusSource> with rawContent (existing + Fix 1 rawContent)
    ↓
[SourceContextBuilder] → Builds context at 2,500 chars/source, sentence-trimmed (Fix 1)
    ↓
[ActorCriticPipeline] (Fix 2)
    │
    ├── Actor (Groq): Chain-of-Thought draft (Fix 3)
    │     → reasoning_scratchpad
    │     → draft_verdict (LAST in schema)
    │     → draft_key_facts with source_text_evidence
    │
    └── Critic (Gemini): Adversarial review
          → corrected_verdict (LAST in schema)
          → corrected_facts
          → corrections_log
    ↓
[AlgorithmicGroundingVerifier] (Fix 4)
    → Verify every is_direct_quote == true fact
    → Strip fabricated citations
    → Apply confidence penalty
    ↓
[RagVerifier] (Enhancement Plan VI)
    → Keyword/numeric verification of all facts
    → Explanation grounding check
    ↓
[KgEnricher] parallel (Enhancement Plan V)
    → EntityContext panel data
    ↓
Final CorvusCheckResult → UI
```

---

## Combined Roadmap

| Fix | Feature | Duration | Dependency |
|---|---|---|---|
| 1 | Snippet Length Fix | 2 days | None — do first |
| 3 | Chain-of-Thought Schema | 3 days | Fix 1 (prompts updated together) |
| 2 | Actor-Critic Architecture | 6 days | Fix 1 + Fix 3 (prompts must be CoT) |
| 4 | Algorithmic Grounding | 5 days | Fix 2 (`source_text_evidence` from Actor) |
| **Total** | | **~16 days** | Sequential — each fix builds on the last |

**Build strictly in order: Fix 1 → Fix 3 → Fix 2 → Fix 4.**

Fix 1 (snippet length) is the highest ROI / lowest effort item. It can be deployed independently and immediately improves accuracy before any other fix lands. Fix 3 (CoT schema) is updated at the same time as Fix 1 since both touch the prompt. Fix 2 (Actor-Critic) depends on CoT prompts being in place. Fix 4 (Algorithmic Grounding) needs the `source_text_evidence` field that only exists after the Actor prompt is updated.

---

## New Dependencies

```kotlin
// No new external libraries required
// StringSimilarity — implemented from scratch (standard Levenshtein DP)
// All other utilities use existing Ktor, Kotlinx, and Coroutines tooling
```

---

## New API Keys

None. Actor-Critic uses existing provider keys. Two calls per check = 2x quota consumption on the actor provider. At Groq's free tier (14,400 req/day / 2 = 7,200 checks/day), this is not a practical concern for solo dev usage.

---

## Definition of Done

**Fix 1 — Snippet Length**
- Gemini and Groq receive minimum 2,500 chars per source (previously 150)
- Cerebras receives maximum 800 chars per source — stays under 4k total
- Sources sorted by credibility tier before context build
- Snippet trimmed to nearest sentence boundary — no mid-sentence cuts
- Total context stays within `MAX_TOTAL_CONTEXT_BY_PROVIDER` for all providers
- `rawContent` used when available, `snippet` as fallback

**Fix 2 — Actor-Critic**
- Two distinct LLM calls per check — Actor drafts, Critic corrects
- Actor and Critic use different providers when both are healthy
- `corrections_log` populated whenever Critic changes something
- `ActorCriticProviders` selection respects health tracker state
- Total Actor + Critic latency stays within 8 seconds on Groq
- Sycophancy test: claim that sounds true but is false → Critic corrects verdict

**Fix 3 — Chain-of-Thought**
- `evidentiary_analysis` is always the FIRST key in JSON output
- `verdict` is always the LAST key in JSON output
- `reasoningScratchpad` non-null for all successful checks
- Parser correctly extracts verdict from last position
- "FULL REASONING" section in Methodology Card shows scratchpad when expanded
- Accuracy regression test: CoT schema improves or matches previous accuracy on 10 test claims

**Fix 4 — Algorithmic Grounding**
- Every `is_direct_quote: true` fact verified against source text
- Fabricated citations (similarity < 55%) stripped before reaching UI
- Confidence penalty applied per fabricated citation, capped at 40%
- Confidence never reaches 0.0 — floored at 0.05
- Verified quotes show matched fragment in UI
- Methodology Card shows count and details of stripped citations
- Integration test: fabricated direct quote → stripped, not displayed to user
