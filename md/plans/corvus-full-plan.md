# Corvus — Full Application Implementation Plan

> **Goal:** Corvus — a production-grade, Malaysian-context-aware Android fact-checking application with multi-source retrieval, multi-provider LLM routing, on-device pre-screening, share sheet integration, history, and offline support. Zero paid API costs.

---

## Full Feature Set

| Category | Features |
|---|---|
| **Input** | Manual text, Android Share Sheet, OCR screenshot paste |
| **Pipeline** | 4-layer fact-check pipeline with provider fallback |
| **Sources** | Google Fact Check, Tavily, Google Custom Search (MY-filtered), Sebenarnya.my |
| **LLM** | Gemini 2.0 Flash (primary), Groq (fallback), Cerebras (tertiary), on-device Gemma 3 pre-screen |
| **Results** | Verdict + confidence + explanation + key facts + sources |
| **History** | Saved checks, Room DB, search/filter history |
| **UI/UX** | Animations, dark mode, share result, copy verdict |
| **Settings** | Preferred LLM provider, source preferences, language |
| **Offline** | On-device pre-screening (Gemma 3 via MediaPipe), cached results |
| **Malaysian context** | BM claim support, local source prioritization, Sebenarnya.my integration |

---

## Tech Stack

| Layer | Choice | Reason |
|---|---|---|
| UI | Jetpack Compose | Your existing stack |
| Navigation | Compose Navigation | Type-safe routes |
| Networking | Ktor Client | Your existing stack |
| DI | Hilt | Your existing stack |
| State | ViewModel + StateFlow | Standard |
| Local DB | Room | History persistence |
| On-device ML | MediaPipe LLM Inference API | Gemma 3 on-device |
| LLM Primary | Gemini 2.0 Flash (AI Studio) | Free, search grounding |
| LLM Fallback 1 | Groq (Llama 3.3 70B) | Free, fast |
| LLM Fallback 2 | Cerebras (Llama 3.3 70B) | Free, fastest inference |
| LLM Fallback 3 | OpenRouter (free models) | Last resort free tier |
| Search Layer 1 | Google Fact Check Tools API | Free, purpose-built |
| Search Layer 2 | Tavily API (free tier) | Clean content, AI-optimized |
| Search Layer 3 | Google Custom Search API | 100/day free, MY-filtered |
| Search Layer 4 | Sebenarnya.my | Malaysian government fact-checks |
| Serialization | Kotlinx Serialization | Your existing stack |
| Preferences | DataStore | Provider settings, user prefs |
| Image loading | Coil | Source favicons |

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    UI Layer (Compose)                    │
│  InputScreen │ ResultScreen │ HistoryScreen │ Settings   │
└─────────────────────────┬───────────────────────────────┘
                          │ StateFlow / Events
┌─────────────────────────▼───────────────────────────────┐
│                 ViewModel Layer                          │
│  CorvusViewModel │ HistoryViewModel │ SettingsVM      │
└─────────────────────────┬───────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────┐
│                  Domain Layer                            │
│  CorvusFactCheckUseCase │ HistoryUseCase │ ClaimExtractorUseCase│
└──────┬────────────┬────────────────┬─────────────────────┘
       │            │                │
┌──────▼──┐  ┌──────▼──────┐  ┌─────▼──────────────────────┐
│ On-device│  │  Retrieval  │  │       LLM Layer             │
│  Layer   │  │   Layer     │  │                             │
│          │  │             │  │  GeminiProvider             │
│ Gemma 3  │  │ GoogleFC    │  │  GroqProvider               │
│ MediaPipe│  │ Tavily      │  │  CerebrasProvider           │
│          │  │ GoogleCS    │  │  OpenRouterProvider         │
│          │  │ Sebenarnya  │  │  LlmRouter (fallback logic) │
└──────────┘  └─────────────┘  └─────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────┐
│                  Data Layer                              │
│  Room DB (History) │ DataStore (Prefs) │ Remote APIs     │
└─────────────────────────────────────────────────────────┘
```

---

## Full Pipeline (4 Layers)

```
User Claim
    ↓
[Pre-screen] On-device Gemma 3 (MediaPipe)
    → Is this a check-worthy factual claim? If not, tell user.
    → Quick plausibility pre-verdict (fast, private, offline)
    ↓
[Layer 1] Google Fact Check Tools API + Sebenarnya.my
    → Already fact-checked by an established publisher? Return directly.
    ↓ (no result)
[Layer 2] Tavily + Google Custom Search (MY-filtered domains)
    → Retrieve top 5-7 relevant articles with content snippets
    ↓
[Layer 3] LLM Reasoning
    Primary:   Gemini 2.0 Flash (AI Studio)
    Fallback1: Groq (Llama 3.3 70B)
    Fallback2: Cerebras (Llama 3.3 70B)
    Fallback3: OpenRouter (free model)
    → Reason over retrieved context → Structured verdict
    ↓
[Post-process] Confidence calibration + source deduplication
    ↓
Result → UI + Room DB (save to history)
```

---

## Data Models

```kotlin
// Core verdict
enum class Verdict {
    TRUE, FALSE, MISLEADING, PARTIALLY_TRUE, UNVERIFIABLE, NOT_A_CLAIM
}

// Full fact check result
data class CorvusResult(
    val id: String = UUID.randomUUID().toString(),
    val claim: String,
    val verdict: Verdict,
    val confidence: Float,
    val explanation: String,
    val keyFacts: List<String>,
    val sources: List<Source>,
    val providerUsed: LlmProvider,
    val language: ClaimLanguage,
    val checkedAt: Long = System.currentTimeMillis(),
    val isFromKnownFactCheck: Boolean = false
)

data class Source(
    val title: String,
    val url: String,
    val publisher: String?,
    val snippet: String?,
    val publishedDate: String?,
    val isLocalSource: Boolean = false   // MY-origin source
)

// LLM provider enum for routing
enum class LlmProvider { GEMINI, GROQ, CEREBRAS, OPENROUTER, ON_DEVICE }

// Language detection for BM vs EN routing
enum class ClaimLanguage { ENGLISH, BAHASA_MALAYSIA, MIXED, UNKNOWN }

// UI state
data class CorvusUiState(
    val inputText: String = "",
    val isLoading: Boolean = false,
    val result: CorvusResult? = null,
    val error: CorvusError? = null,
    val currentStep: PipelineStep = PipelineStep.IDLE,
    val preScreenResult: PreScreenResult? = null
)

enum class PipelineStep {
    IDLE,
    PRE_SCREENING,          // On-device Gemma 3
    CHECKING_KNOWN_FACTS,   // Google Fact Check / Sebenarnya
    RETRIEVING_SOURCES,     // Tavily / Google CS
    ANALYZING,              // LLM reasoning
    DONE
}

sealed class CorvusError {
    object NetworkError : CorvusError()
    object AllProvidersExhausted : CorvusError()
    object NotACheckableClaim : CorvusError()
    data class Unknown(val message: String) : CorvusError()
}

// Room entity
@Entity(tableName = "corvus_history")
data class CorvusHistoryEntity(
    @PrimaryKey val id: String,
    val claim: String,
    val verdict: String,
    val confidence: Float,
    val explanation: String,
    val sourcesJson: String,      // JSON serialized
    val providerUsed: String,
    val checkedAt: Long
)
```

---

## LLM Router

```kotlin
class LlmRouter @Inject constructor(
    private val gemini: GeminiProvider,
    private val groq: GroqProvider,
    private val cerebras: CerebrasProvider,
    private val openRouter: OpenRouterProvider,
    private val prefs: UserPreferences
) {
    private val providerOrder: List<LlmProvider> get() =
        listOf(prefs.preferredProvider) +
        listOf(GEMINI, GROQ, CEREBRAS, OPENROUTER).filter { it != prefs.preferredProvider }

    suspend fun analyze(claim: String, sources: List<Source>): Pair<CorvusResult, LlmProvider> {
        for (provider in providerOrder) {
            try {
                val result = getProvider(provider).analyze(claim, sources)
                return result to provider
            } catch (e: RateLimitException) {
                continue
            } catch (e: ProviderUnavailableException) {
                continue
            }
        }
        throw AllProvidersExhaustedException()
    }
}
```

---

## Malaysian Context Integration

### Source Filtering Strategy
```kotlin
val MALAYSIAN_DOMAINS = listOf(
    "malaysiakini.com",
    "bernama.com",
    "freemalaysiatoday.com",
    "themalaymailonline.com",
    "astroawani.com",
    "thestar.com.my",
    "nst.com.my",
    "sebenarnya.my"
)

// Tavily search with site restriction
val query = buildString {
    append(claim)
    append(" site:${MALAYSIAN_DOMAINS.joinToString(" OR site:")}")
}
```

### BM Claim Handling
- Detect language via `LanguageIdentifier` (ML Kit, free)
- If BM detected → prepend translation context to LLM prompt
- Bias source retrieval toward BM-language results
- Return explanation in same language as input

### Sebenarnya.my Integration
```
GET https://sebenarnya.my/wp-json/wp/v2/posts?search={claim}&per_page=5
```
Parse WordPress REST API (public, no key needed) → map to `CorvusResult`.

---

## Share Sheet Integration

```xml
<!-- AndroidManifest.xml -->
<activity android:name=".ui.input.CorvusInputActivity">
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
</activity>
```

```kotlin
// InputActivity.kt
if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
    viewModel.prefillAndAutoCheck(sharedText)
}
```

---

## On-Device Pre-Screening (Gemma 3 via MediaPipe)

**Purpose:** Fast local pre-check before hitting any network API. Catches obviously false claims, non-claims, and spam.

```kotlin
class OnDevicePrescreener @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var inferenceSession: LlmInference? = null

    suspend fun preScreen(claim: String): PreScreenResult {
        val session = inferenceSession ?: initSession()
        val prompt = """
            Is the following a specific, checkable factual claim?
            If yes, do you have high confidence it is clearly true or false?
            Claim: "$claim"
            Respond with JSON only: {"is_claim": true/false, "quick_verdict": "TRUE|FALSE|UNCLEAR", "confidence": 0.0}
        """.trimIndent()
        val response = session.generateResponse(prompt)
        return parsePreScreenResponse(response)
    }
}
```

Model file: `gemma-3-1b-it-int4.task` (~1GB) — downloaded on first launch via DownloadManager.

---

## History Feature

```kotlin
// HistoryDao.kt
@Dao
interface HistoryDao {
    @Query("SELECT * FROM corvus_history ORDER BY checkedAt DESC")
    fun getAllHistory(): Flow<List<CorvusHistoryEntity>>

    @Query("SELECT * FROM corvus_history WHERE claim LIKE '%' || :query || '%'")
    fun searchHistory(query: String): Flow<List<CorvusHistoryEntity>>

    @Query("SELECT * FROM corvus_history WHERE verdict = :verdict")
    fun filterByVerdict(verdict: String): Flow<List<CorvusHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CorvusHistoryEntity)

    @Delete
    suspend fun delete(entity: CorvusHistoryEntity)

    @Query("DELETE FROM corvus_history")
    suspend fun clearAll()
}
```

---

## Prompt Template (Full Version)

### English Claims
```
You are a rigorous, neutral fact-checking assistant. Your task is to evaluate the factual accuracy of a claim using only the provided source articles.

CLAIM: "{claim}"
CLAIM LANGUAGE: {language}

SOURCE ARTICLES:
{sources}

EVALUATION RULES:
1. Only use information from the provided sources
2. If sources contradict each other, note the conflict
3. If sources are insufficient, return UNVERIFIABLE
4. Distinguish between the claim being false vs misleading vs unverifiable
5. MISLEADING = technically true but missing crucial context
6. Do not inject knowledge not present in the sources
7. Consider the credibility and recency of each source

Respond ONLY with valid JSON (no markdown, no preamble):
{
  "verdict": "TRUE|FALSE|MISLEADING|PARTIALLY_TRUE|UNVERIFIABLE",
  "confidence": 0.0,
  "explanation": "3-4 sentence neutral explanation",
  "key_facts": ["fact 1", "fact 2", "fact 3"],
  "sources_used": [0, 1, 2],
  "conflicting_sources": [],
  "context_missing": "Optional: what context would change the verdict"
}
```

### Bahasa Malaysia Extension
```
Tambahan: Klaim ini dalam Bahasa Malaysia. Sila berikan penjelasan dalam Bahasa Malaysia.
Fokuskan carian kepada sumber-sumber media Malaysia yang dipercayai.
```

---

## Screen Map

```
SplashScreen
    └── InputScreen (default)
            ├── [typing] → ClaimInputCard
            ├── [loading] → PipelineProgressCard
            ├── [result] → ResultScreen
            │       ├── VerdictBadge
            │       ├── ConfidenceBar
            │       ├── ExplanationCard
            │       ├── KeyFactsList
            │       ├── SourcesList
            │       └── ShareResultButton
            └── [nav] → HistoryScreen
                    ├── HistoryList (grouped by date)
                    ├── SearchBar
                    ├── FilterChips (TRUE / FALSE / MISLEADING)
                    └── HistoryDetailScreen (tap item)
                Settings (bottom sheet or screen)
                    ├── Preferred LLM Provider
                    ├── Prioritize Malaysian Sources
                    ├── Response Language
                    └── Clear History
```

---

## Phased Implementation Roadmap

### Phase 1 — Foundation (Week 1)
- [ ] Project setup: Compose + Hilt + Ktor + Room + DataStore
- [ ] Package structure and base classes
- [ ] API key management via `local.properties` + BuildConfig
- [ ] Ktor HTTP client setup with logging + serialization
- [ ] Base `LlmProvider` interface and response parser

### Phase 2 — Core Pipeline (Week 2)
- [ ] `GoogleFactCheckClient` implementation
- [ ] `TavilyClient` implementation
- [ ] `GeminiClient` implementation (primary LLM)
- [ ] `GroqClient` implementation (fallback)
- [ ] `CorvusFactCheckUseCase` with 3-layer pipeline
- [ ] JSON verdict parser with robust error handling
- [ ] Unit tests for parser + use case

### Phase 3 — UI Core (Week 3)
- [ ] `InputScreen` composable
- [ ] `ResultScreen` composable
- [ ] `VerdictBadge` + `ConfidenceBar` composables
- [ ] `SourceCard` composable
- [ ] `PipelineStepIndicator` composable
- [ ] Navigation setup
- [ ] `CorvusViewModel` wiring
- [ ] Error state UI

### Phase 4 — Extended Retrieval (Week 4)
- [ ] `GoogleCustomSearchClient` with Malaysian domain filtering
- [ ] `SebenarnyaClient` (WordPress REST API)
- [ ] `CerebrasClient` implementation
- [ ] `OpenRouterClient` implementation
- [ ] `LlmRouter` with full provider fallback chain
- [ ] Source deduplication + ranking logic

### Phase 5 — Share Sheet + Input Modes (Week 5)
- [ ] Android Share Sheet `intent-filter` setup
- [ ] Auto-populate + auto-trigger on share
- [ ] OCR screenshot input (ML Kit Text Recognition)
- [ ] Clipboard paste detection
- [ ] Language detection (ML Kit `LanguageIdentifier`)
- [ ] BM prompt adaptation

### Phase 6 — On-Device Screening (Week 6)
- [ ] MediaPipe LLM Inference API integration
- [ ] Gemma 3 model download manager (first-launch)
- [ ] `OnDevicePrescreener` implementation
- [ ] Pre-screen result display in UI
- [ ] Offline mode: pre-screen only when no internet

### Phase 7 — History + Settings (Week 7)
- [ ] Room DB setup + `CorvusHistoryEntity`
- [ ] `HistoryDao` implementation
- [ ] `HistoryScreen` composable (grouped list, search, filter)
- [ ] `HistoryDetailScreen`
- [ ] `HistoryViewModel`
- [ ] Settings screen (DataStore-backed)
- [ ] Export history (share as text/JSON)

### Phase 8 — Polish + Production (Week 8)
- [ ] Dark mode support
- [ ] Animations (result card reveal, verdict badge pulse)
- [ ] Share result as image/text
- [ ] Copy verdict to clipboard
- [ ] Proguard rules
- [ ] Crash analytics setup (Firebase Crashlytics, free)
- [ ] Performance profiling
- [ ] End-to-end testing against 50 known claims
- [ ] Play Store listing prep

---

## API Keys Required

| Key | Source | Free Limit |
|---|---|---|
| `GOOGLE_FACT_CHECK_API_KEY` | Google Cloud Console | Generous |
| `GOOGLE_CUSTOM_SEARCH_API_KEY` | Google Cloud Console | 100 queries/day |
| `GOOGLE_CSE_ID` | programmablesearchengine.google.com | Free |
| `TAVILY_API_KEY` | tavily.com | 1,000/month |
| `GEMINI_API_KEY` | aistudio.google.com | Free with limits |
| `GROQ_API_KEY` | console.groq.com | Free with limits |
| `CEREBRAS_API_KEY` | inference.cerebras.ai | Free with limits |
| `OPENROUTER_API_KEY` | openrouter.ai | Free models available |

---

## Rate Limit & Quota Strategy

```kotlin
// Track daily usage per provider in DataStore
data class ProviderQuota(
    val provider: LlmProvider,
    val requestsToday: Int,
    val dailyLimit: Int,
    val resetAtMidnight: Long
)

// Soft limits (conservative, below actual limits)
val SOFT_LIMITS = mapOf(
    LlmProvider.GEMINI      to 100,   // actual: ~1500/day
    LlmProvider.GROQ        to 100,   // actual: 14,400/day (10 RPM * 24hr * 60min / request)
    LlmProvider.CEREBRAS    to 50,
    LlmProvider.OPENROUTER  to 50
)
```

---

## Confidence Calibration

Raw LLM confidence values are often overconfident. Apply post-processing:

```kotlin
fun calibrateConfidence(raw: Float, sourceCount: Int, isKnownFactCheck: Boolean): Float {
    var calibrated = raw
    if (sourceCount < 2) calibrated *= 0.7f       // penalize low source count
    if (sourceCount >= 5) calibrated *= 1.1f       // reward rich evidence
    if (isKnownFactCheck) calibrated = minOf(calibrated * 1.2f, 1.0f)
    return calibrated.coerceIn(0.0f, 1.0f)
}
```

---

## Malaysian Differentiators (Summary)

1. **Sebenarnya.my** — Official Malaysian government fact-check portal integrated as Layer 1 alongside Google Fact Check
2. **Domain filtering** — Tavily and Google Custom Search restricted to verified Malaysian media outlets
3. **BM detection** — ML Kit `LanguageIdentifier` routes BM claims to BM-adapted prompts
4. **BM explanation** — LLM instructed to return explanation in BM if claim is in BM
5. **Local source labeling** — Sources from MY domains labeled as "Local Source" in UI

---

## Estimated Timeline

| Phase | Duration | Deliverable |
|---|---|---|
| 1 — Foundation | 1 week | Buildable project skeleton |
| 2 — Core Pipeline | 1 week | Working 3-layer fact-check |
| 3 — UI Core | 1 week | Usable input + result screens |
| 4 — Extended Retrieval | 1 week | Full 4-provider LLM routing |
| 5 — Share + Input Modes | 1 week | Share Sheet, OCR, BM support |
| 6 — On-Device | 1 week | Gemma 3 pre-screen + offline |
| 7 — History + Settings | 1 week | Full persistence layer |
| 8 — Polish + Production | 1 week | Play Store ready |
| **Total** | **~8 weeks** | **Full production app** |

---

## Definition of Done (Full App)

- All 4 pipeline layers functional
- All 4 LLM providers routing with automatic fallback
- Share Sheet integration working across WhatsApp, Chrome, Twitter
- BM claims detected and handled with localized output
- Sebenarnya.my returns results for Malaysian hoaxes
- On-device Gemma 3 pre-screens claim validity before network calls
- History stored in Room, searchable and filterable
- Settings persist across app restarts
- App functional with zero internet (pre-screen only mode)
- No crashes across 100 test claims
- Proguard + release build working
- Dark mode fully supported
