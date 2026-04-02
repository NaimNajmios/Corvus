# Corvus — MVP Implementation Plan

> **Goal:** Corvus — a functional fact-checking app that accepts text input, retrieves evidence from free sources, and returns a structured verdict using free LLM providers. No paid APIs, no crawlers.

---

## Scope

| In Scope | Out of Scope |
|---|---|
| Manual text input | Share Sheet integration |
| Google Fact Check API (first pass) | OCR / screenshot input |
| Tavily free tier for retrieval | On-device inference |
| Gemini 2.0 Flash (AI Studio free) as primary LLM | History / saved checks |
| Groq (Llama 3.3 70B) as fallback LLM | Malaysian-specific source filtering |
| Structured verdict UI | User accounts |
| Source list display | Offline mode |

---

## Tech Stack

| Layer | Choice | Reason |
|---|---|---|
| UI | Jetpack Compose | Your existing stack |
| Networking | Ktor Client | Your existing stack |
| DI | Hilt | Your existing stack |
| State | ViewModel + StateFlow | Standard |
| LLM Primary | Gemini 2.0 Flash (AI Studio) | Free, search grounding built-in |
| LLM Fallback | Groq (Llama 3.3 70B) | Free, fast, strong reasoning |
| Search Retrieval | Tavily API (free tier) | Clean article content, AI-optimized |
| Fact-Check First Pass | Google Fact Check Tools API | Free, no quota issues |
| Config | `local.properties` + BuildConfig | API key management |

---

## Architecture

```
UI (Compose)
    ↓ StateFlow
CorvusViewModel
    ↓
CorvusFactCheckUseCase
    ├── GoogleFactCheckRepository   → Google Fact Check Tools API
    ├── TavilyRepository            → Tavily Search API
    └── LlmRepository
            ├── GeminiProvider      → Gemini 2.0 Flash (AI Studio)
            └── GroqProvider        → Groq Llama 3.3 70B (fallback)
```

---

## Data Models

```kotlin
enum class Verdict { TRUE, FALSE, MISLEADING, UNVERIFIABLE, CHECKING }

data class CorvusResult(
    val verdict: Verdict,
    val confidence: Float,           // 0.0 - 1.0
    val explanation: String,
    val keyFacts: List<String>,
    val sources: List<Source>
)

data class Source(
    val title: String,
    val url: String,
    val publisher: String? = null,
    val snippet: String? = null
)

data class CorvusUiState(
    val inputText: String = "",
    val isLoading: Boolean = false,
    val result: CorvusResult? = null,
    val error: String? = null,
    val currentStep: PipelineStep = PipelineStep.IDLE
)

enum class PipelineStep {
    IDLE, CHECKING_KNOWN_FACTS, RETRIEVING_SOURCES, ANALYZING, DONE
}
```

---

## Pipeline Logic

```kotlin
// CorvusFactCheckUseCase.kt
suspend fun check(claim: String): CorvusResult {

    // Layer 1 — Google Fact Check (fast path)
    val knownCheck = googleFactCheckRepo.search(claim)
    if (knownCheck != null) return knownCheck

    // Layer 2 — Retrieve supporting articles
    val articles = tavilyRepo.search(claim, maxResults = 5)

    // Layer 3 — LLM reasoning
    return try {
        llmRepo.analyze(claim, articles, provider = LlmProvider.GEMINI)
    } catch (e: RateLimitException) {
        llmRepo.analyze(claim, articles, provider = LlmProvider.GROQ)
    }
}
```

---

## Prompt Template (Groq / Gemini)

```
You are a fact-checking assistant. Analyze the claim below using the provided source articles.

CLAIM: "{claim}"

SOURCES:
{sources_as_numbered_list}

Instructions:
- Assess whether the claim is factually accurate based on the sources
- If sources are insufficient, return UNVERIFIABLE
- Be concise and neutral
- Do not fabricate information not in the sources

Respond ONLY with valid JSON, no markdown, no preamble:
{
  "verdict": "TRUE|FALSE|MISLEADING|UNVERIFIABLE",
  "confidence": 0.0,
  "explanation": "2-3 sentence explanation",
  "key_facts": ["fact 1", "fact 2"],
  "sources_used": [0, 1]
}
```

---

## Screen Structure

### Screen 1 — Input Screen
- `OutlinedTextField` — claim input (multiline, max 500 chars)
- Char counter
- `Button("Check Fact")` — triggers pipeline
- Pipeline step indicator (text label: "Checking known facts...", "Retrieving sources...", etc.)

### Screen 2 — Result Screen
- Verdict badge (color-coded card)
  - ✅ Green — TRUE
  - ❌ Red — FALSE
  - ⚠️ Amber — MISLEADING
  - ❓ Grey — UNVERIFIABLE
- Confidence bar
- Explanation text
- Sources list (title + publisher + link chip)
- "Check Another" button

---

## Phase Breakdown

### Phase 1 — Project Setup (Day 1)
- [ ] Create new Android project (Compose + Hilt + Ktor)
- [ ] Add dependencies: Ktor, Hilt, Kotlinx Serialization, Coroutines
- [ ] Set up `local.properties` for API keys
- [ ] Set up BuildConfig fields for all keys
- [ ] Create base package structure:
  ```
  com.najmi.corvus
  ├── data/
  │   ├── remote/
  │   └── repository/
  ├── domain/
  │   ├── model/
  │   └── usecase/
  └── ui/
      ├── input/
      └── result/
  ```

### Phase 2 — Data Layer (Day 2–3)
- [ ] Implement `GoogleFactCheckClient` (Ktor)
  - Endpoint: `https://factchecktools.googleapis.com/v1alpha1/claims:search`
  - Params: `query`, `key`
  - Parse `claims[].claimReview` array → `CorvusResult`
- [ ] Implement `TavilyClient` (Ktor)
  - Endpoint: `https://api.tavily.com/search`
  - Body: `{ query, search_depth: "advanced", max_results: 5, include_raw_content: false }`
  - Parse results → `List<Source>`
- [ ] Implement `GeminiClient` (Ktor)
  - Endpoint: AI Studio REST API
  - Model: `gemini-2.0-flash`
  - Parse structured JSON response
- [ ] Implement `GroqClient` (Ktor)
  - OpenAI-compatible endpoint: `https://api.groq.com/openai/v1/chat/completions`
  - Model: `llama-3.3-70b-versatile`
  - Parse JSON from `choices[0].message.content`

### Phase 3 — Domain Layer (Day 4)
- [ ] Implement `CorvusFactCheckUseCase` with layered pipeline
- [ ] Implement JSON response parser with fallback (strip markdown fences, try-catch)
- [ ] Implement rate limit detection and provider fallback logic
- [ ] Write unit tests for parser and pipeline logic

### Phase 4 — UI Layer (Day 5–6)
- [ ] `InputScreen` composable
- [ ] `ResultScreen` composable
- [ ] `VerdictBadge` composable (reusable)
- [ ] `SourceCard` composable
- [ ] `PipelineStepIndicator` composable
- [ ] Navigation setup (NavHost, 2 routes)
- [ ] `CorvusViewModel` wiring

### Phase 5 — Polish & Testing (Day 7)
- [ ] Error states (no internet, all providers failed, empty result)
- [ ] Loading skeleton or shimmer
- [ ] Input validation (empty, too short, too long)
- [ ] Manual test against known true/false claims
- [ ] Proguard rules for Ktor + Serialization

---

## API Keys Required

| Key | Where to Get | Free Limit |
|---|---|---|
| `GOOGLE_FACT_CHECK_API_KEY` | Google Cloud Console | Generous / effectively free |
| `TAVILY_API_KEY` | tavily.com | 1,000 searches/month |
| `GEMINI_API_KEY` | aistudio.google.com | Free with rate limits |
| `GROQ_API_KEY` | console.groq.com | Free with rate limits |

Store all in `local.properties`, expose via `BuildConfig`. Never commit to Git.

---

## Estimated Timeline

| Phase | Duration |
|---|---|
| Setup | 1 day |
| Data Layer | 2 days |
| Domain Layer | 1 day |
| UI Layer | 2 days |
| Polish & Testing | 1 day |
| **Total** | **~7 days** |

---

## Definition of Done (MVP)

- User can type a claim and receive a verdict
- All 3 pipeline layers execute in sequence
- Result shows verdict, explanation, and at least one source
- Graceful error handling for network failures
- Fallback from Gemini → Groq works automatically
- App does not crash on any tested input
