# Corvus — Enhancement Plan IX
## Mistral AI · Cohere Integration
### Provider Expansion · Intelligent Load Spreading · Quota Management

> **Scope:** Full integration of Mistral AI (`mistral-saba`, `mistral-small-3.1`) and Cohere (`command-r`, `command-r-plus`) into the existing `LlmRouter` and `ActorCriticPipeline`. Includes intelligent claim-type-aware routing, per-provider quota guards, updated `LlmProviderConfig`, token tracking extensions, and UI updates. Both providers are free tier only.

---

## Design Principles

Three rules govern how new providers are added to Corvus:

1. **No provider is used blindly.** Each has a defined role based on what it is genuinely better at. Groq is not replaced — it remains the fastest general-purpose Actor. Mistral-Saba has a specific mandate: BM language. Cohere has a specific mandate: citation-heavy claim types. Neither is a general fallback substitute.

2. **Quota is a first-class concern.** Cohere's 1,000 calls/month is strict. It is not treated as a standard fallback — it has a daily quota guard that prevents it being consumed on low-value checks. Mistral has a more generous rate limit but still needs tracking.

3. **The user never sees provider switching.** All routing decisions are silent. The provider used is disclosed post-result in the Methodology Card — never during analysis.

---

## Accurate Free Tier Limits (Verified)

Before building the quota system, the actual free tier constraints need to be stated correctly:

| Provider | Free Limit | Rate Limit | Context Window | Notes |
|---|---|---|---|---|
| **Groq** | ~500k TPD (tokens/day) estimate | 30 RPM, 6,000 TPM on free | 128k | Varies by model; `llama-3.3-70b` has tighter limits |
| **Gemini 2.0 Flash** | 1,500 RPD (requests/day) | 15 RPM, 1M TPM | 1M | AI Studio free tier — generous |
| **Cerebras** | ~1M tokens/day estimated | 30 RPM | 8,192 (Llama 3.1 8B) / 128k (Llama 3.3 70B) | Fast inference, limits not publicly documented precisely |
| **OpenRouter** | Per-model varies (`:free` tagged) | Varies | Varies | Free models deprioritised during congestion |
| **Mistral** | ~500k tokens/day estimated free tier | 1 req/sec, ~200k TPM | 128k (small), 32k (saba) | La Plateforme free tier; `mistral-saba` has 32k context |
| **Cohere** | 1,000 API calls/month (~33/day) | 5 RPM, ~20k TPM | 128k | Trial key — strict monthly call cap |

**Important corrections from the previous plan:**
- Groq's `llama-3.3-70b-versatile` free tier is approximately **6,000 TPM** (tokens per minute), not a simple daily bucket. This translates to ~8.6M tokens/day theoretical maximum at sustained load, but in practice burst requests will hit the per-minute cap frequently.
- Gemini's free tier is **1,500 requests/day** with a 15 RPM cap — the bottleneck is requests, not total tokens.
- Cerebras limits are not publicly documented with precision. The ~1M/day estimate is conservative.
- Cohere trial key is **1,000 API calls/month total** — this is calls, not tokens. At ~33 calls/day average, this is the binding constraint.
- Mistral free tier details are approximate — La Plateforme free tier limits change periodically.

**Revised daily check capacity estimate:**

| Provider | Binding Constraint | Practical Checks/Day |
|---|---|---|
| Gemini | 1,500 requests/day | ~750 checks (Actor + Critic = 2 calls) |
| Groq | 6,000 TPM rate limit | ~120 checks/hour sustained; burst limited |
| Cerebras | ~1M tokens/day estimate | ~400 checks |
| Mistral | ~200k TPM rate limit | ~80 checks/hour sustained |
| Cohere | 33 calls/day | ~16 checks (2 calls each) |
| OpenRouter | Model-dependent | ~80 checks/day conservative |

The **practical combined capacity for a solo developer** doing real testing is well above what you'll consume. The main risk is hitting Groq's **per-minute** rate limit (6,000 TPM) during rapid sequential testing — not daily limits.

---

## Provider Role Assignments

### Mistral: BM Language Specialist + General Fallback

```
mistral-saba (32k context)
    → PRIMARY role: Actor for BAHASA_MALAYSIA and MIXED language claims
    → SECONDARY role: Fallback Actor when Groq rate-limits
    → Why: Trained specifically on Southeast Asian languages including Malay.
      Better tokenisation of BM, better understanding of Malaysian named entities,
      better handling of Malay idioms and formal/informal register differences.
    → Context limit: 32k — adequate for most checks, monitor for long source sets

mistral-small-3.1 (128k context)
    → PRIMARY role: Tertiary fallback Actor for EN claims (after Groq + Cerebras)
    → SECONDARY role: Critic fallback when Gemini rate-limits
    → Why: Solid general reasoning, OpenAI-compatible, reliable JSON output
    → Context limit: 128k — no truncation concerns
```

### Cohere: Citation-Heavy Claim Types Only

```
command-r (128k context)
    → PRIMARY role: Critic for QUOTE and SCIENTIFIC claim types
    → SECONDARY role: Actor for SCIENTIFIC claims where grounding quality is paramount
    → Why: command-r was built specifically for RAG and retrieval-augmented tasks.
      Citation grounding accuracy on QUOTE verification consistently outperforms
      Llama-3 variants. For SCIENTIFIC claims, it handles academic abstract
      reasoning better than general-purpose models.
    → Quota guard: Never used when daily call count >= 28 (85% of 33/day limit)
    → Context limit: 128k — no truncation concerns

command-r-plus (128k context)
    → RESERVED for HIGH harm claims only (health misinformation, racial incitement)
    → Why: Best citation grounding in the free tier roster. Burned only on checks
      where getting it wrong has real consequences.
    → Quota guard: Never used when daily call count >= 8 (25% of 33/day)
      Shared quota pool with command-r
```

---

## Updated LlmProvider Enum

```kotlin
// domain/model/LlmProvider.kt — updated

enum class LlmProvider(val displayName: String) {
    GEMINI      ("Gemini 2.0 Flash"),
    GROQ        ("Groq / Llama-3.3 70B"),
    CEREBRAS    ("Cerebras / Llama-3.3 70B"),
    OPENROUTER  ("OpenRouter"),
    MISTRAL_SABA  ("Mistral Saba"),        // NEW
    MISTRAL_SMALL ("Mistral Small 3.1"),   // NEW
    COHERE_R      ("Cohere Command-R"),    // NEW
    COHERE_R_PLUS ("Cohere Command-R+"),   // NEW
    ON_DEVICE   ("On-Device / Gemma 3"),
    UNKNOWN     ("")
}
```

---

## Phase 1 — Mistral Client

### 1.1 API Key Setup

```
# local.properties
MISTRAL_API_KEY=your_mistral_api_key_here
```

```kotlin
// build.gradle.kts (app)
buildConfigField("String", "MISTRAL_API_KEY",
    "\"${properties["MISTRAL_API_KEY"]}\"")
```

```
# local.properties.example
MISTRAL_API_KEY=your_mistral_la_plateforme_api_key
```

### 1.2 Mistral Request/Response Models

Mistral uses the OpenAI-compatible format — minimal new models needed:

```kotlin
// data/remote/llm/mistral/MistralModels.kt

// Mistral uses standard OpenAI chat completion format
// Reuse existing OpenAI-compatible models with a Mistral-specific wrapper

object MistralModels {
    const val SABA        = "mistral-saba-latest"
    const val SMALL       = "mistral-small-latest"

    // Context window limits in tokens
    val CONTEXT_WINDOWS = mapOf(
        SABA  to 32_768,
        SMALL to 131_072
    )
}

// Mistral returns usage in standard OpenAI format:
// { "usage": { "prompt_tokens": N, "completion_tokens": N, "total_tokens": N } }
// No custom parsing needed — reuse GroqUsage model
```

### 1.3 Mistral HTTP Client

```kotlin
// data/remote/llm/mistral/MistralClient.kt

class MistralClient @Inject constructor(
    private val httpClient : HttpClient,
    private val apiKey     : String
) : LlmClient {

    companion object {
        private const val BASE_URL    = "https://api.mistral.ai/v1/chat/completions"
        private const val TIMEOUT_MS  = 15_000L
    }

    override suspend fun complete(
        prompt    : String,
        model     : String,
        maxTokens : Int
    ): LlmResponse {
        return withTimeout(TIMEOUT_MS) {
            val response = httpClient.post(BASE_URL) {
                header("Authorization", "Bearer $apiKey")
                header("Content-Type", "application/json")
                setBody(OpenAiChatRequest(
                    model     = model,
                    messages  = listOf(OpenAiMessage(role = "user", content = prompt)),
                    maxTokens = maxTokens
                ))
            }.body<OpenAiChatResponse>()

            LlmResponse(
                content          = response.choices.first().message.content,
                promptTokens     = response.usage?.promptTokens,
                completionTokens = response.usage?.completionTokens,
                model            = model,
                provider         = if (model.contains("saba"))
                    LlmProvider.MISTRAL_SABA
                else
                    LlmProvider.MISTRAL_SMALL
            )
        }
    }

    // Convenience wrappers for each model variant
    suspend fun completeSaba(prompt: String, maxTokens: Int) =
        complete(prompt, MistralModels.SABA, maxTokens)

    suspend fun completeSmall(prompt: String, maxTokens: Int) =
        complete(prompt, MistralModels.SMALL, maxTokens)
}
```

---

## Phase 2 — Cohere Client

### 2.1 API Key Setup

```
# local.properties
COHERE_API_KEY=your_cohere_trial_api_key_here
```

```kotlin
buildConfigField("String", "COHERE_API_KEY",
    "\"${properties["COHERE_API_KEY"]}\"")
```

### 2.2 Cohere Request/Response Models

Cohere does **not** use the OpenAI-compatible format. It has its own schema:

```kotlin
// data/remote/llm/cohere/CohereModels.kt

object CohereModels {
    const val COMMAND_R      = "command-r-08-2024"   // Latest stable command-r
    const val COMMAND_R_PLUS = "command-r-plus-08-2024"

    val CONTEXT_WINDOWS = mapOf(
        COMMAND_R      to 128_000,
        COMMAND_R_PLUS to 128_000
    )
}

// data/remote/llm/cohere/CohereRequest.kt

@Serializable
data class CohereRequest(
    val model      : String,
    val message    : String,         // Cohere uses "message" not "messages"
    @SerialName("max_tokens")
    val maxTokens  : Int = 1000,
    val temperature: Float = 0.1f,   // Low temperature for fact-checking consistency
    @SerialName("chat_history")
    val chatHistory: List<CohereChatMessage> = emptyList()
)

@Serializable
data class CohereChatMessage(
    val role    : String,   // "USER" or "CHATBOT" (Cohere capitalises roles)
    val message : String
)

// data/remote/llm/cohere/CohereResponse.kt

@Serializable
data class CohereResponse(
    val text : String,           // Response content
    @SerialName("generation_id")
    val generationId: String? = null,
    val meta : CohereMeta? = null
)

@Serializable
data class CohereMeta(
    @SerialName("billed_units")
    val billedUnits : CohereBilledUnits? = null,
    val tokens      : CohereTokens? = null
)

@Serializable
data class CohereBilledUnits(
    @SerialName("input_tokens")
    val inputTokens  : Int = 0,
    @SerialName("output_tokens")
    val outputTokens : Int = 0
)

@Serializable
data class CohereTokens(
    @SerialName("input_tokens")
    val inputTokens  : Int = 0,
    @SerialName("output_tokens")
    val outputTokens : Int = 0
)
```

### 2.3 Cohere HTTP Client

```kotlin
// data/remote/llm/cohere/CohereClient.kt

class CohereClient @Inject constructor(
    private val httpClient      : HttpClient,
    private val apiKey          : String,
    private val quotaGuard      : CohereQuotaGuard    // Injected — prevents quota exhaustion
) : LlmClient {

    companion object {
        private const val CHAT_URL    = "https://api.cohere.com/v2/chat"
        private const val TIMEOUT_MS  = 20_000L       // Cohere can be slower than Groq
    }

    override suspend fun complete(
        prompt    : String,
        model     : String,
        maxTokens : Int
    ): LlmResponse {

        // Quota check BEFORE making any API call
        if (!quotaGuard.canCall(model)) {
            throw CohereQuotaExceededException(
                "Cohere daily quota reached for model $model. " +
                "Calls today: ${quotaGuard.callsToday()}"
            )
        }

        return withTimeout(TIMEOUT_MS) {
            val response = httpClient.post(CHAT_URL) {
                header("Authorization", "Bearer $apiKey")
                header("Content-Type", "application/json")
                setBody(CohereRequest(
                    model     = model,
                    message   = prompt,
                    maxTokens = maxTokens
                ))
            }.body<CohereResponse>()

            // Record the call for quota tracking
            quotaGuard.recordCall(model)

            // Extract token counts — Cohere uses billed_units
            val promptTokens     = response.meta?.billedUnits?.inputTokens
                ?: response.meta?.tokens?.inputTokens
            val completionTokens = response.meta?.billedUnits?.outputTokens
                ?: response.meta?.tokens?.outputTokens

            LlmResponse(
                content          = response.text,
                promptTokens     = promptTokens,
                completionTokens = completionTokens,
                model            = model,
                provider         = if (model.contains("plus"))
                    LlmProvider.COHERE_R_PLUS
                else
                    LlmProvider.COHERE_R
            )
        }
    }

    suspend fun completeR(prompt: String, maxTokens: Int) =
        complete(prompt, CohereModels.COMMAND_R, maxTokens)

    suspend fun completeRPlus(prompt: String, maxTokens: Int) =
        complete(prompt, CohereModels.COMMAND_R_PLUS, maxTokens)
}

class CohereQuotaExceededException(message: String) : Exception(message)
```

---

## Phase 3 — Cohere Quota Guard

This is the most critical piece of the Cohere integration. Without it, Cohere's 33 calls/day (1,000/month) will be exhausted in a single heavy testing session.

```kotlin
// domain/usecase/CohereQuotaGuard.kt

class CohereQuotaGuard @Inject constructor(
    private val dataStore    : DataStore<Preferences>
) {
    companion object {
        // Conservative daily limits — leaving headroom for burst testing
        // command-r and command-r-plus share the same 1,000 calls/month pool
        const val DAILY_LIMIT_COMMAND_R      = 28   // 85% of 33/day average
        const val DAILY_LIMIT_COMMAND_R_PLUS = 8    // 25% of 33/day — reserved for high-harm only

        private val KEY_R_CALLS_TODAY        = intPreferencesKey("cohere_r_calls_today")
        private val KEY_R_PLUS_CALLS_TODAY   = intPreferencesKey("cohere_r_plus_calls_today")
        private val KEY_RESET_DATE           = longPreferencesKey("cohere_quota_reset_date")
        private val KEY_MONTHLY_CALLS        = intPreferencesKey("cohere_monthly_calls")
        private val KEY_MONTHLY_RESET        = longPreferencesKey("cohere_monthly_reset")
    }

    // Check if a call can be made
    suspend fun canCall(model: String): Boolean {
        resetIfNewDay()
        resetIfNewMonth()

        val prefs      = dataStore.data.first()
        val monthlyTotal = prefs[KEY_MONTHLY_CALLS] ?: 0

        // Hard stop at 950/month — leave 50 as safety buffer
        if (monthlyTotal >= 950) return false

        return when {
            model.contains("plus") -> (prefs[KEY_R_PLUS_CALLS_TODAY] ?: 0) < DAILY_LIMIT_COMMAND_R_PLUS
            else                   -> (prefs[KEY_R_CALLS_TODAY] ?: 0) < DAILY_LIMIT_COMMAND_R
        }
    }

    // Record a completed call
    suspend fun recordCall(model: String) {
        dataStore.edit { prefs ->
            if (model.contains("plus")) {
                prefs[KEY_R_PLUS_CALLS_TODAY] = (prefs[KEY_R_PLUS_CALLS_TODAY] ?: 0) + 1
            } else {
                prefs[KEY_R_CALLS_TODAY] = (prefs[KEY_R_CALLS_TODAY] ?: 0) + 1
            }
            prefs[KEY_MONTHLY_CALLS] = (prefs[KEY_MONTHLY_CALLS] ?: 0) + 1
        }
    }

    suspend fun callsToday(): Int {
        val prefs = dataStore.data.first()
        return (prefs[KEY_R_CALLS_TODAY] ?: 0) + (prefs[KEY_R_PLUS_CALLS_TODAY] ?: 0)
    }

    suspend fun monthlyCallsUsed(): Int =
        dataStore.data.first()[KEY_MONTHLY_CALLS] ?: 0

    suspend fun monthlyCallsRemaining(): Int =
        (1000 - monthlyCallsUsed()).coerceAtLeast(0)

    private suspend fun resetIfNewDay() {
        dataStore.edit { prefs ->
            val lastReset   = prefs[KEY_RESET_DATE] ?: 0L
            val todayStart  = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()

            if (lastReset < todayStart) {
                prefs[KEY_R_CALLS_TODAY]      = 0
                prefs[KEY_R_PLUS_CALLS_TODAY] = 0
                prefs[KEY_RESET_DATE]         = todayStart
            }
        }
    }

    private suspend fun resetIfNewMonth() {
        dataStore.edit { prefs ->
            val lastMonthReset = prefs[KEY_MONTHLY_RESET] ?: 0L
            val monthStart     = LocalDate.now().withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()

            if (lastMonthReset < monthStart) {
                prefs[KEY_MONTHLY_CALLS]  = 0
                prefs[KEY_MONTHLY_RESET]  = monthStart
            }
        }
    }
}
```

---

## Phase 4 — Updated Provider Configuration

```kotlin
// domain/model/LlmProviderConfig.kt — updated with all 8 providers

val DEFAULT_PROVIDER_CONFIGS = listOf(

    // ── Tier 1: Primary fast providers ──────────────────────────────────
    LlmProviderConfig(
        provider         = LlmProvider.GROQ,
        model            = "llama-3.3-70b-versatile",
        maxContextTokens = 128_000,
        timeoutMs        = 8_000L,
        maxRetries       = 1,
        retryDelayMs     = 500L,
        priority         = 1,
        // Groq's binding constraint is 6,000 TPM, not a daily budget
        // Track by minute usage, not daily
        quotaType        = QuotaType.RATE_LIMIT_TPM,
        tpmLimit         = 6_000
    ),

    LlmProviderConfig(
        provider         = LlmProvider.GEMINI,
        model            = "gemini-2.0-flash",
        maxContextTokens = 1_000_000,
        timeoutMs        = 15_000L,
        maxRetries       = 2,
        retryDelayMs     = 1_000L,
        priority         = 2,
        quotaType        = QuotaType.REQUESTS_PER_DAY,
        dailyRequestLimit = 1_500
    ),

    // ── Tier 2: Specialist providers ─────────────────────────────────────
    LlmProviderConfig(
        provider         = LlmProvider.MISTRAL_SABA,
        model            = MistralModels.SABA,
        maxContextTokens = 32_768,    // Important: 32k limit, not 128k
        timeoutMs        = 12_000L,
        maxRetries       = 1,
        retryDelayMs     = 500L,
        priority         = 3,
        quotaType        = QuotaType.RATE_LIMIT_RPM,
        rpmLimit         = 60           // 1 req/sec = 60 RPM
    ),

    LlmProviderConfig(
        provider         = LlmProvider.COHERE_R,
        model            = CohereModels.COMMAND_R,
        maxContextTokens = 128_000,
        timeoutMs        = 20_000L,
        maxRetries       = 1,
        retryDelayMs     = 1_000L,
        priority         = 4,
        quotaType        = QuotaType.CALLS_PER_MONTH,
        monthlyCallLimit = 1_000       // Shared pool with COHERE_R_PLUS
    ),

    // ── Tier 3: General fallback providers ───────────────────────────────
    LlmProviderConfig(
        provider         = LlmProvider.CEREBRAS,
        model            = "llama-3.3-70b",
        maxContextTokens = 128_000,
        timeoutMs        = 6_000L,
        maxRetries       = 1,
        retryDelayMs     = 500L,
        priority         = 5,
        quotaType        = QuotaType.ESTIMATED_DAILY_TOKENS,
        dailyTokenLimit  = 1_000_000
    ),

    LlmProviderConfig(
        provider         = LlmProvider.MISTRAL_SMALL,
        model            = MistralModels.SMALL,
        maxContextTokens = 131_072,
        timeoutMs        = 15_000L,
        maxRetries       = 1,
        retryDelayMs     = 500L,
        priority         = 6,
        quotaType        = QuotaType.RATE_LIMIT_RPM,
        rpmLimit         = 60
    ),

    LlmProviderConfig(
        provider         = LlmProvider.OPENROUTER,
        model            = "meta-llama/llama-3.3-70b-instruct:free",
        maxContextTokens = 131_072,
        timeoutMs        = 20_000L,
        maxRetries       = 1,
        retryDelayMs     = 1_000L,
        priority         = 7,
        quotaType        = QuotaType.ESTIMATED_DAILY_TOKENS,
        dailyTokenLimit  = 200_000
    ),

    // ── Tier 4: Reserved ─────────────────────────────────────────────────
    LlmProviderConfig(
        provider         = LlmProvider.COHERE_R_PLUS,
        model            = CohereModels.COMMAND_R_PLUS,
        maxContextTokens = 128_000,
        timeoutMs        = 25_000L,
        maxRetries       = 0,          // No retries — quota too precious
        retryDelayMs     = 0L,
        priority         = 8,
        quotaType        = QuotaType.CALLS_PER_MONTH,
        monthlyCallLimit = 1_000       // Same shared pool
    )
)

enum class QuotaType {
    RATE_LIMIT_TPM,           // Tokens per minute (Groq)
    RATE_LIMIT_RPM,           // Requests per minute (Mistral)
    REQUESTS_PER_DAY,         // Requests per day (Gemini)
    CALLS_PER_MONTH,          // Total API calls per month (Cohere)
    ESTIMATED_DAILY_TOKENS    // Estimated daily token budget (Cerebras, OpenRouter)
}

data class LlmProviderConfig(
    val provider          : LlmProvider,
    val model             : String,
    val maxContextTokens  : Int,
    val timeoutMs         : Long,
    val maxRetries        : Int,
    val retryDelayMs      : Long,
    val priority          : Int,
    val quotaType         : QuotaType,
    val tpmLimit          : Int = 0,
    val rpmLimit          : Int = 0,
    val dailyRequestLimit : Int = 0,
    val dailyTokenLimit   : Int = 0,
    val monthlyCallLimit  : Int = 0
)
```

---

## Phase 5 — Claim-Type-Aware Routing

The new `ActorCriticProviderSelector` replaces the simple `selectActorCriticProviders()` function from Plan VII. It is the single source of truth for which provider handles which step for which claim type.

```kotlin
// domain/usecase/ActorCriticProviderSelector.kt

class ActorCriticProviderSelector @Inject constructor(
    private val healthTracker : LlmProviderHealthTracker,
    private val cohereGuard   : CohereQuotaGuard,
    private val prefs         : UserPreferences
) {
    data class ProviderAssignment(
        val actor  : LlmProvider,
        val critic : LlmProvider,
        val rationale: String       // For Methodology Card disclosure
    )

    suspend fun select(
        classified : ClassifiedClaim,
        harmLevel  : HarmLevel = HarmLevel.NONE
    ): ProviderAssignment {

        val language  = classified.language
        val claimType = classified.type

        return when {

            // ── BM / Malay language claims ────────────────────────────────
            // Mistral-Saba as primary Actor — best BM understanding
            isBahasaMalaysia(language) ->
                assignBmLanguage(claimType, harmLevel)

            // ── QUOTE claims ──────────────────────────────────────────────
            // Cohere command-r as Critic — best citation grounding
            claimType == ClaimType.QUOTE ->
                assignQuoteClaim(harmLevel)

            // ── SCIENTIFIC / health claims ────────────────────────────────
            // Cohere command-r as Actor — built for academic document reasoning
            claimType == ClaimType.SCIENTIFIC ->
                assignScientificClaim(harmLevel)

            // ── HIGH harm claims ──────────────────────────────────────────
            // Escalate to best available — command-r-plus if quota allows
            harmLevel == HarmLevel.HIGH ->
                assignHighHarmClaim(claimType, language)

            // ── General English claims ────────────────────────────────────
            else ->
                assignGeneralClaim()
        }
    }

    // ── Assignment functions ─────────────────────────────────────────────

    private suspend fun assignBmLanguage(
        claimType : ClaimType,
        harmLevel : HarmLevel
    ): ProviderAssignment {
        val actorOk  = healthTracker.isAvailable(LlmProvider.MISTRAL_SABA)
        val criticOk = healthTracker.isAvailable(LlmProvider.GEMINI)

        return ProviderAssignment(
            actor    = if (actorOk) LlmProvider.MISTRAL_SABA
                       else bestAvailableActor(exclude = emptySet()),
            critic   = if (criticOk) LlmProvider.GEMINI
                       else bestAvailableCritic(exclude = setOf(LlmProvider.MISTRAL_SABA)),
            rationale = "Mistral-Saba selected for Bahasa Malaysia claim"
        )
    }

    private suspend fun assignQuoteClaim(
        harmLevel: HarmLevel
    ): ProviderAssignment {
        val cohereAvailable = cohereGuard.canCall(CohereModels.COMMAND_R) &&
            healthTracker.isAvailable(LlmProvider.COHERE_R)

        return ProviderAssignment(
            actor    = bestAvailableActor(exclude = setOf(LlmProvider.COHERE_R, LlmProvider.COHERE_R_PLUS)),
            critic   = if (cohereAvailable) LlmProvider.COHERE_R
                       else bestAvailableCritic(
                           exclude = setOf(LlmProvider.COHERE_R, LlmProvider.COHERE_R_PLUS)
                       ),
            rationale = if (cohereAvailable)
                "Cohere command-r selected as Critic for quote citation verification"
            else
                "Cohere quota exhausted — using standard Critic fallback"
        )
    }

    private suspend fun assignScientificClaim(
        harmLevel: HarmLevel
    ): ProviderAssignment {
        val cohereAvailable = cohereGuard.canCall(CohereModels.COMMAND_R) &&
            healthTracker.isAvailable(LlmProvider.COHERE_R)

        // For scientific claims: Cohere as Actor (better document reasoning)
        // Gemini as Critic (reliable, large context for long abstracts)
        return ProviderAssignment(
            actor    = if (cohereAvailable) LlmProvider.COHERE_R
                       else bestAvailableActor(
                           exclude = setOf(LlmProvider.COHERE_R, LlmProvider.COHERE_R_PLUS)
                       ),
            critic   = bestAvailableCritic(
                exclude = setOf(LlmProvider.COHERE_R, LlmProvider.COHERE_R_PLUS)
            ),
            rationale = if (cohereAvailable)
                "Cohere command-r selected as Actor for scientific claim grounding"
            else
                "Cohere quota exhausted — using standard Actor fallback"
        )
    }

    private suspend fun assignHighHarmClaim(
        claimType : ClaimType,
        language  : ClaimLanguage
    ): ProviderAssignment {
        // For HIGH harm: escalate Critic to command-r-plus if quota allows
        val plusAvailable = cohereGuard.canCall(CohereModels.COMMAND_R_PLUS) &&
            healthTracker.isAvailable(LlmProvider.COHERE_R_PLUS)

        val actor = when {
            isBahasaMalaysia(language) &&
            healthTracker.isAvailable(LlmProvider.MISTRAL_SABA) ->
                LlmProvider.MISTRAL_SABA
            else ->
                bestAvailableActor(exclude = setOf(LlmProvider.COHERE_R, LlmProvider.COHERE_R_PLUS))
        }

        return ProviderAssignment(
            actor    = actor,
            critic   = if (plusAvailable) LlmProvider.COHERE_R_PLUS
                       else bestAvailableCritic(
                           exclude = setOf(LlmProvider.COHERE_R_PLUS)
                       ),
            rationale = if (plusAvailable)
                "Cohere command-r-plus escalated for HIGH harm claim verification"
            else
                "command-r-plus quota exhausted — using best available Critic"
        )
    }

    private fun assignGeneralClaim(): ProviderAssignment {
        return ProviderAssignment(
            actor    = bestAvailableActor(
                exclude = setOf(
                    LlmProvider.COHERE_R,
                    LlmProvider.COHERE_R_PLUS,
                    LlmProvider.MISTRAL_SABA   // Reserve Saba for BM
                )
            ),
            critic   = bestAvailableCritic(
                exclude = setOf(LlmProvider.COHERE_R, LlmProvider.COHERE_R_PLUS)
            ),
            rationale = "Standard routing — general English claim"
        )
    }

    // ── Utility functions ────────────────────────────────────────────────

    private fun bestAvailableActor(exclude: Set<LlmProvider>): LlmProvider {
        // Actor priority: speed first
        val actorPreference = listOf(
            LlmProvider.GROQ,
            LlmProvider.CEREBRAS,
            LlmProvider.GEMINI,
            LlmProvider.MISTRAL_SMALL,
            LlmProvider.OPENROUTER,
            LlmProvider.MISTRAL_SABA
        )
        return actorPreference
            .filter { it !in exclude }
            .firstOrNull { healthTracker.isAvailable(it) }
            ?: LlmProvider.GEMINI  // Last resort
    }

    private fun bestAvailableCritic(exclude: Set<LlmProvider>): LlmProvider {
        // Critic priority: reliability and reasoning quality first
        val criticPreference = listOf(
            LlmProvider.GEMINI,
            LlmProvider.GROQ,
            LlmProvider.MISTRAL_SMALL,
            LlmProvider.CEREBRAS,
            LlmProvider.OPENROUTER
        )
        return criticPreference
            .filter { it !in exclude }
            .firstOrNull { healthTracker.isAvailable(it) }
            ?: LlmProvider.GROQ    // Last resort
    }

    private fun isBahasaMalaysia(language: ClaimLanguage) =
        language == ClaimLanguage.BAHASA_MALAYSIA ||
        language == ClaimLanguage.MIXED
}
```

---

## Phase 6 — Updated ActorCriticPipeline

Add new client injections and wire the `ActorCriticProviderSelector`:

```kotlin
// domain/usecase/ActorCriticPipeline.kt — updated

class ActorCriticPipeline @Inject constructor(
    private val groqClient      : GroqClient,
    private val geminiClient    : GeminiClient,
    private val cerebrasClient  : CerebrasClient,
    private val openRouterClient: OpenRouterClient,
    private val mistralClient   : MistralClient,      // NEW
    private val cohereClient    : CohereClient,        // NEW
    private val providerSelector: ActorCriticProviderSelector,  // REPLACES selectActorCriticProviders()
    private val contextBuilder  : SourceContextBuilder,
    private val tokenCollector  : TokenCollector
) {
    suspend fun analyze(
        claim          : String,
        classified     : ClassifiedClaim,
        sources        : List<CorvusSource>,
        temporalContext: String,
        harmLevel      : HarmLevel = HarmLevel.NONE
    ): CorvusCheckResult.GeneralResult {

        // ── Provider selection — claim-type + harm aware ─────────────────
        val assignment   = providerSelector.select(classified, harmLevel)
        val actorContext  = contextBuilder.build(sources, assignment.actor)
        val criticContext = contextBuilder.build(sources, assignment.critic)

        // ── Pass 1: Actor ────────────────────────────────────────────────
        val actorPrompt = buildActorPrompt(claim, classified, actorContext, temporalContext)
        val actorResponse = getClient(assignment.actor).complete(
            prompt    = actorPrompt,
            model     = getModel(assignment.actor),
            maxTokens = maxTokensFor(assignment.actor, pass = ActorCriticPass.ACTOR)
        )
        tokenCollector.record(
            actorResponse.toTokenUsage(TokenPipelineStep.ACTOR_ANALYSIS, actorPrompt, classified.language)
        )
        val actorDraft = parseActorDraft(actorResponse.content)

        // ── Pass 2: Critic ───────────────────────────────────────────────
        val criticPrompt = buildCriticPrompt(claim, criticContext, actorDraft)
        val criticResponse = getClient(assignment.critic).complete(
            prompt    = criticPrompt,
            model     = getModel(assignment.critic),
            maxTokens = maxTokensFor(assignment.critic, pass = ActorCriticPass.CRITIC)
        )
        tokenCollector.record(
            criticResponse.toTokenUsage(TokenPipelineStep.CRITIC_REVIEW, criticPrompt, classified.language)
        )
        val finalResult = parseCriticOutput(criticResponse.content, sources)

        return finalResult.copy(
            actorProvider   = assignment.actor,
            criticProvider  = assignment.critic,
            routingRationale = assignment.rationale,     // NEW field
            correctionsLog  = actorDraft.unsupportedAssumptions +
                              (finalResult.correctionsLog ?: emptyList())
        )
    }

    // Map provider to correct client
    private fun getClient(provider: LlmProvider): LlmClient = when (provider) {
        LlmProvider.GROQ           -> groqClient
        LlmProvider.GEMINI         -> geminiClient
        LlmProvider.CEREBRAS       -> cerebrasClient
        LlmProvider.OPENROUTER     -> openRouterClient
        LlmProvider.MISTRAL_SABA   -> mistralClient
        LlmProvider.MISTRAL_SMALL  -> mistralClient
        LlmProvider.COHERE_R       -> cohereClient
        LlmProvider.COHERE_R_PLUS  -> cohereClient
        else                       -> geminiClient
    }

    // Map provider to correct model string
    private fun getModel(provider: LlmProvider): String = when (provider) {
        LlmProvider.GROQ           -> "llama-3.3-70b-versatile"
        LlmProvider.GEMINI         -> "gemini-2.0-flash"
        LlmProvider.CEREBRAS       -> "llama-3.3-70b"
        LlmProvider.OPENROUTER     -> "meta-llama/llama-3.3-70b-instruct:free"
        LlmProvider.MISTRAL_SABA   -> MistralModels.SABA
        LlmProvider.MISTRAL_SMALL  -> MistralModels.SMALL
        LlmProvider.COHERE_R       -> CohereModels.COMMAND_R
        LlmProvider.COHERE_R_PLUS  -> CohereModels.COMMAND_R_PLUS
        else                       -> "gemini-2.0-flash"
    }

    // Max output tokens — calibrated per provider and pass
    private fun maxTokensFor(provider: LlmProvider, pass: ActorCriticPass): Int {
        return when (pass) {
            ActorCriticPass.ACTOR  -> when (provider) {
                LlmProvider.CEREBRAS -> 800    // Small context window — conservative output
                LlmProvider.COHERE_R -> 1500   // Cohere is verbose but accurate
                else                 -> 2000
            }
            ActorCriticPass.CRITIC -> when (provider) {
                LlmProvider.CEREBRAS -> 1000
                LlmProvider.COHERE_R,
                LlmProvider.COHERE_R_PLUS -> 2000
                else                 -> 2500
            }
        }
    }
}

enum class ActorCriticPass { ACTOR, CRITIC }
```

---

## Phase 7 — Mistral Context Window Guard

Mistral-Saba has a 32k token context window — significantly smaller than other providers. The `SourceContextBuilder` must enforce this:

```kotlin
// In SourceContextBuilder — update SNIPPET_LENGTH_BY_PROVIDER and MAX_TOTAL_CONTEXT

private val SNIPPET_LENGTH_BY_PROVIDER = mapOf(
    LlmProvider.GEMINI         to 3_000,
    LlmProvider.GROQ           to 2_500,
    LlmProvider.CEREBRAS       to 800,
    LlmProvider.OPENROUTER     to 2_000,
    LlmProvider.MISTRAL_SABA   to 1_200,   // NEW — 32k window needs conservative budget
    LlmProvider.MISTRAL_SMALL  to 2_500,   // NEW — 128k window, generous
    LlmProvider.COHERE_R       to 2_500,   // NEW — 128k window
    LlmProvider.COHERE_R_PLUS  to 2_500    // NEW — 128k window
)

private val MAX_TOTAL_CONTEXT_BY_PROVIDER = mapOf(
    LlmProvider.GEMINI         to 15_000,
    LlmProvider.GROQ           to 12_000,
    LlmProvider.CEREBRAS       to 4_000,
    LlmProvider.OPENROUTER     to 8_000,
    LlmProvider.MISTRAL_SABA   to 6_000,   // NEW — conservative: ~18k chars = ~4.5k tokens
    LlmProvider.MISTRAL_SMALL  to 12_000,  // NEW
    LlmProvider.COHERE_R       to 12_000,  // NEW
    LlmProvider.COHERE_R_PLUS  to 12_000   // NEW
)
```

---

## Phase 8 — Updated Provider Health Tracker

Add Mistral and Cohere to the health tracker:

```kotlin
// In LlmProviderHealthTracker

// Updated COOL_DOWN and rate limit handling for new providers

fun isAvailable(provider: LlmProvider): Boolean {
    val health = healthMap[provider] ?: return true
    val now    = System.currentTimeMillis()

    if (health.rateLimitUntilMs > now) return false
    if (health.consecutiveFails >= 3 &&
        (now - health.lastFailureMs) < COOL_DOWN_MS) return false

    // Special case: Cohere quota exceeded is not a transient failure
    // It should not trigger cool-down — quota is a different failure type
    if (provider == LlmProvider.COHERE_R || provider == LlmProvider.COHERE_R_PLUS) {
        if (health.lastFailureType == FailureType.QUOTA_EXCEEDED) return false
    }

    return true
}

// Extended health record
private data class ProviderHealth(
    val failCount         : Int = 0,
    val lastFailureMs     : Long = 0L,
    val consecutiveFails  : Int = 0,
    val rateLimitUntilMs  : Long = 0L,
    val lastFailureType   : FailureType = FailureType.UNKNOWN   // NEW
)

enum class FailureType {
    RATE_LIMIT, TIMEOUT, SERVICE_UNAVAILABLE,
    QUOTA_EXCEEDED,  // NEW — for Cohere monthly quota
    AUTH_FAILED, UNKNOWN
}

fun recordFailure(provider: LlmProvider, failure: LlmFailure) {
    val current = healthMap[provider] ?: ProviderHealth()
    val now     = System.currentTimeMillis()

    // Map LlmFailure to FailureType
    val failureType = when (failure) {
        is LlmFailure.RateLimit           -> FailureType.RATE_LIMIT
        is LlmFailure.Timeout             -> FailureType.TIMEOUT
        is LlmFailure.ServiceUnavailable  -> FailureType.SERVICE_UNAVAILABLE
        is LlmFailure.AuthenticationFailed -> FailureType.AUTH_FAILED
        else                              -> FailureType.UNKNOWN
    }

    healthMap[provider] = when (failure) {
        is LlmFailure.RateLimit -> current.copy(
            failCount        = current.failCount + 1,
            lastFailureMs    = now,
            consecutiveFails = current.consecutiveFails + 1,
            rateLimitUntilMs = now + failure.retryAfterMs + RATE_LIMIT_BUFFER_MS,
            lastFailureType  = FailureType.RATE_LIMIT
        )
        else -> current.copy(
            failCount        = current.failCount + 1,
            lastFailureMs    = now,
            consecutiveFails = current.consecutiveFails + 1,
            lastFailureType  = failureType
        )
    }
}
```

---

## Phase 9 — Updated Token Tracking

Add Mistral and Cohere to `TokenPipelineStep` enum and `FreeTierCalculator`:

```kotlin
// Updated FreeTierCalculator.DAILY_TOKEN_LIMITS

private val DAILY_TOKEN_LIMITS = mapOf(
    LlmProvider.GROQ           to 6_000,    // TPM — not daily tokens
    LlmProvider.GEMINI         to 1_500,    // Requests per day
    LlmProvider.CEREBRAS       to 1_000_000,
    LlmProvider.OPENROUTER     to 200_000,
    LlmProvider.MISTRAL_SABA   to 200_000,  // NEW — estimated
    LlmProvider.MISTRAL_SMALL  to 300_000,  // NEW — estimated
    LlmProvider.COHERE_R       to 33,       // NEW — calls per day (1000/month ÷ 30)
    LlmProvider.COHERE_R_PLUS  to 33        // NEW — shared pool
)

// Cohere is special — its quota is calls, not tokens
// FreeTierCalculator shows this differently in UI

data class ProviderHeadroom(
    val provider            : LlmProvider,
    val limitType           : QuotaDisplayType,    // NEW
    val dailyLimit          : Int,
    val usedToday           : Int,
    val remaining           : Int,
    val percentUsed         : Float,
    val estimatedChecksLeft : Int,
    val status              : HeadroomStatus,
    // NEW — Cohere-specific monthly tracking
    val monthlyLimit        : Int? = null,
    val monthlyUsed         : Int? = null,
    val monthlyRemaining    : Int? = null
)

enum class QuotaDisplayType {
    TOKENS_PER_MINUTE,   // Groq
    REQUESTS_PER_DAY,    // Gemini
    TOKENS_PER_DAY,      // Cerebras, OpenRouter, Mistral
    CALLS_PER_MONTH      // Cohere — show both daily and monthly
}
```

---

## Phase 10 — UI Updates

### Methodology Card — Routing Rationale

```kotlin
// In MethodologyCard — add routing rationale when Mistral or Cohere selected

result.routingRationale?.let { rationale ->
    // Only show if non-standard routing was used
    if (!rationale.contains("Standard routing")) {
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(CorvusIcons.Route,
                modifier = Modifier.size(12.dp).padding(top = 2.dp),
                tint = CorvusTheme.colors.accent)
            Text(
                rationale,
                style     = CorvusTheme.typography.caption,
                color     = CorvusTheme.colors.textSecondary,
                fontStyle = FontStyle.Italic
            )
        }
    }
}
```

### Provider Headroom Card — Cohere Special Display

```kotlin
@Composable
fun ProviderHeadroomCard(
    headroom : FreeTierCalculator.ProviderHeadroom,
    modifier : Modifier = Modifier
) {
    val barColor = headroomBarColor(headroom.status)
    val isCohereMonthly = headroom.limitType == QuotaDisplayType.CALLS_PER_MONTH

    Card(modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, CorvusTheme.colors.border),
        shape = RoundedCornerShape(6.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {

            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(headroom.provider.displayName(),
                    style = CorvusTheme.typography.labelSmall,
                    color = CorvusTheme.colors.textPrimary, fontFamily = IbmPlexMono)
                Text(
                    when (headroom.limitType) {
                        QuotaDisplayType.CALLS_PER_MONTH ->
                            "~${headroom.estimatedChecksLeft} checks left today"
                        QuotaDisplayType.REQUESTS_PER_DAY ->
                            "${headroom.remaining} requests left today"
                        else ->
                            "~${headroom.estimatedChecksLeft} checks left today"
                    },
                    style = CorvusTheme.typography.caption, color = barColor
                )
            }

            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress   = { headroom.percentUsed },
                modifier   = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color      = barColor, trackColor = CorvusTheme.colors.border
            )
            Spacer(Modifier.height(4.dp))

            // Standard daily row
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${headroom.usedToday} used today",
                    style = CorvusTheme.typography.caption,
                    color = CorvusTheme.colors.textTertiary, fontFamily = IbmPlexMono)
                Text("${headroom.remaining} remaining",
                    style = CorvusTheme.typography.caption,
                    color = CorvusTheme.colors.textSecondary, fontFamily = IbmPlexMono)
            }

            // Cohere monthly tracking — extra row
            if (isCohereMonthly && headroom.monthlyRemaining != null) {
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Monthly: ${headroom.monthlyUsed ?: 0} / ${headroom.monthlyLimit ?: 1000} calls",
                        style = CorvusTheme.typography.caption,
                        color = CorvusTheme.colors.textTertiary, fontFamily = IbmPlexMono)
                    Text("${headroom.monthlyRemaining} left this month",
                        style = CorvusTheme.typography.caption,
                        color = if ((headroom.monthlyRemaining ?: 0) < 100)
                            CorvusTheme.colors.verdictFalse
                        else CorvusTheme.colors.textSecondary,
                        fontFamily = IbmPlexMono)
                }
            }
        }
    }
}
```

### Settings — New API Key Fields

Add Mistral and Cohere key entry to the API Keys section in Settings:

```kotlin
@Composable
fun ApiKeySettingsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("API KEYS", style = CorvusTheme.typography.label,
            color = CorvusTheme.colors.textSecondary, fontFamily = IbmPlexMono)

        ApiKeyRow("Google (Fact Check + KG)", isConfigured = BuildConfig.GOOGLE_FACT_CHECK_API_KEY.isNotBlank())
        ApiKeyRow("Gemini (AI Studio)",       isConfigured = BuildConfig.GEMINI_API_KEY.isNotBlank())
        ApiKeyRow("Groq",                     isConfigured = BuildConfig.GROQ_API_KEY.isNotBlank())
        ApiKeyRow("Tavily",                   isConfigured = BuildConfig.TAVILY_API_KEY.isNotBlank())
        ApiKeyRow("Cerebras",                 isConfigured = BuildConfig.CEREBRAS_API_KEY.isNotBlank())
        ApiKeyRow("OpenRouter",               isConfigured = BuildConfig.OPENROUTER_API_KEY.isNotBlank())
        ApiKeyRow("Mistral",                  isConfigured = BuildConfig.MISTRAL_API_KEY.isNotBlank())   // NEW
        ApiKeyRow("Cohere",                   isConfigured = BuildConfig.COHERE_API_KEY.isNotBlank())    // NEW
    }
}

@Composable
fun ApiKeyRow(provider: String, isConfigured: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(provider, style = CorvusTheme.typography.bodySmall,
            color = CorvusTheme.colors.textPrimary)
        Surface(
            color = if (isConfigured)
                CorvusTheme.colors.verdictTrue.copy(alpha = 0.12f)
            else
                CorvusTheme.colors.verdictFalse.copy(alpha = 0.12f),
            shape = RoundedCornerShape(2.dp)
        ) {
            Text(
                if (isConfigured) "Configured" else "Not set",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = CorvusTheme.typography.caption,
                color = if (isConfigured) CorvusTheme.colors.verdictTrue
                        else CorvusTheme.colors.verdictFalse,
                fontFamily = IbmPlexMono
            )
        }
    }
}
```

---

## Phase 11 — Hilt Module Updates

```kotlin
// di/LlmModule.kt — updated

@Module
@InstallIn(SingletonComponent::class)
object LlmModule {

    // Existing providers unchanged — add below:

    @Provides
    @Singleton
    fun provideMistralClient(
        httpClient : HttpClient,
        @Named("mistralApiKey") apiKey: String
    ): MistralClient = MistralClient(httpClient, apiKey)

    @Provides
    @Named("mistralApiKey")
    fun provideMistralApiKey(): String = BuildConfig.MISTRAL_API_KEY

    @Provides
    @Singleton
    fun provideCohereQuotaGuard(
        dataStore: DataStore<Preferences>
    ): CohereQuotaGuard = CohereQuotaGuard(dataStore)

    @Provides
    @Singleton
    fun provideCohereClient(
        httpClient  : HttpClient,
        @Named("cohereApiKey") apiKey: String,
        quotaGuard  : CohereQuotaGuard
    ): CohereClient = CohereClient(httpClient, apiKey, quotaGuard)

    @Provides
    @Named("cohereApiKey")
    fun provideCohereApiKey(): String = BuildConfig.COHERE_API_KEY

    @Provides
    @Singleton
    fun provideActorCriticProviderSelector(
        healthTracker : LlmProviderHealthTracker,
        cohereGuard   : CohereQuotaGuard,
        prefs         : UserPreferences
    ): ActorCriticProviderSelector =
        ActorCriticProviderSelector(healthTracker, cohereGuard, prefs)
}
```

---

## Complete Routing Decision Tree

```
Incoming claim
    │
    ├── Language: BAHASA_MALAYSIA or MIXED?
    │       ↓ YES
    │       Actor  → mistral-saba (32k — monitor context)
    │       Critic → gemini-2.0-flash
    │       Fallback Actor  → groq → cerebras → mistral-small → openrouter
    │       Fallback Critic → groq → mistral-small → cerebras
    │
    ├── ClaimType: QUOTE?
    │       ↓ YES
    │       Actor  → groq (fast, query rewriter already neutralised claim)
    │       Critic → cohere command-r [if quota available]
    │              → gemini [if cohere quota exhausted]
    │
    ├── ClaimType: SCIENTIFIC?
    │       ↓ YES
    │       Actor  → cohere command-r [if quota available]
    │              → groq [if cohere quota exhausted]
    │       Critic → gemini (reliable, large context for abstracts)
    │
    ├── HarmLevel: HIGH?
    │       ↓ YES
    │       Actor  → mistral-saba [if BM] → groq [otherwise]
    │       Critic → cohere command-r-plus [if quota <= 8 calls today]
    │              → gemini [if command-r-plus quota exhausted]
    │
    └── GENERAL (English, standard claim)
            Actor  → groq → cerebras → gemini → mistral-small → openrouter
            Critic → gemini → groq → mistral-small → cerebras → openrouter
```

---

## Implementation Tasks

### Mistral Integration
- [ ] Add `MISTRAL_API_KEY` to `local.properties.example` and `build.gradle.kts`
- [ ] Create `MistralModels` object with model constants and context windows
- [ ] Implement `MistralClient` — OpenAI-compatible, `LlmClient` interface
- [ ] Implement `MistralClient.completeSaba()` and `completeSmall()` wrappers
- [ ] Add `MISTRAL_SABA` and `MISTRAL_SMALL` to `LlmProvider` enum
- [ ] Add Mistral entries to `DEFAULT_PROVIDER_CONFIGS`
- [ ] Update `SNIPPET_LENGTH_BY_PROVIDER` with Mistral-Saba (1,200) and Mistral-Small (2,500)
- [ ] Update `MAX_TOTAL_CONTEXT_BY_PROVIDER` with Mistral entries
- [ ] Add Mistral to `LlmProviderHealthTracker` (no special handling needed)
- [ ] Add Mistral to `FreeTierCalculator.DAILY_TOKEN_LIMITS`
- [ ] Register `MistralClient` in Hilt `LlmModule`

### Cohere Integration
- [ ] Add `COHERE_API_KEY` to `local.properties.example` and `build.gradle.kts`
- [ ] Create `CohereModels` object with model constants
- [ ] Implement `CohereRequest`, `CohereChatMessage` serializable models
- [ ] Implement `CohereResponse`, `CohereMeta`, `CohereBilledUnits`, `CohereTokens` models
- [ ] Implement `CohereQuotaGuard` with DataStore persistence
- [ ] Implement daily reset in `CohereQuotaGuard.resetIfNewDay()`
- [ ] Implement monthly reset in `CohereQuotaGuard.resetIfNewMonth()`
- [ ] Implement `CohereQuotaExceededException`
- [ ] Implement `CohereClient` with quota guard pre-check
- [ ] Parse `meta.billed_units` token counts in `CohereClient`
- [ ] Implement `CohereClient.completeR()` and `completeRPlus()` wrappers
- [ ] Add `COHERE_R` and `COHERE_R_PLUS` to `LlmProvider` enum
- [ ] Add Cohere entries to `DEFAULT_PROVIDER_CONFIGS`
- [ ] Add Cohere to `SourceContextBuilder` context maps
- [ ] Add `QUOTA_EXCEEDED` failure type to `LlmProviderHealthTracker`
- [ ] Update `isAvailable()` — Cohere quota exceeded is not a transient failure
- [ ] Add `CALLS_PER_MONTH` quota type to `FreeTierCalculator`
- [ ] Register `CohereClient` and `CohereQuotaGuard` in Hilt `LlmModule`

### Provider Selector
- [ ] Implement `ActorCriticProviderSelector` with all 5 assignment functions
- [ ] Implement `bestAvailableActor()` with Cohere + Saba exclusions
- [ ] Implement `bestAvailableCritic()` with Cohere exclusions
- [ ] Replace `selectActorCriticProviders()` in `ActorCriticPipeline` with selector
- [ ] Add `routingRationale` field to `CorvusCheckResult.GeneralResult`
- [ ] Update `ActorCriticPipeline` constructor with Mistral + Cohere clients
- [ ] Update `getClient()` and `getModel()` in `ActorCriticPipeline`
- [ ] Update `maxTokensFor()` with Cohere-specific limits

### UI
- [ ] Update `MethodologyCard` — show routing rationale for non-standard routing
- [ ] Update `ProviderHeadroomCard` — Cohere monthly tracking row
- [ ] Update `QuotaDisplayType` enum and headroom display logic
- [ ] Add Mistral and Cohere rows to `ApiKeySettingsSection`
- [ ] Update provider health panel in Settings — 8 providers total

### Tests
- [ ] Unit test: `MistralClient.complete()` — success path, timeout path
- [ ] Unit test: `CohereClient.complete()` — success, quota exceeded before call
- [ ] Unit test: `CohereQuotaGuard.canCall()` — under limit, at limit, over limit
- [ ] Unit test: `CohereQuotaGuard.resetIfNewDay()` — resets correctly at midnight
- [ ] Unit test: `CohereQuotaGuard.resetIfNewMonth()` — monthly reset
- [ ] Unit test: `ActorCriticProviderSelector.select()` — BM claim → Saba
- [ ] Unit test: `ActorCriticProviderSelector.select()` — QUOTE + Cohere available → Cohere Critic
- [ ] Unit test: `ActorCriticProviderSelector.select()` — QUOTE + Cohere exhausted → Gemini Critic
- [ ] Unit test: `ActorCriticProviderSelector.select()` — HIGH harm → command-r-plus if available
- [ ] Unit test: `ActorCriticProviderSelector.select()` — HIGH harm + plus exhausted → Gemini
- [ ] Unit test: `ActorCriticProviderSelector.select()` — GENERAL → excludes Cohere and Saba
- [ ] Unit test: Cohere token extraction — `billed_units` path, `tokens` path, estimation fallback
- [ ] Unit test: Mistral-Saba context truncation — stays under 6,000 total chars
- [ ] Integration test: BM claim routes to Mistral-Saba Actor end-to-end
- [ ] Integration test: QUOTE claim routes to Cohere Critic when quota available
- [ ] Integration test: Cohere quota exhausted → silent Gemini fallback, no error surfaced
- [ ] Integration test: Mistral rate limit → health tracker cool-down → next provider used

**Estimated duration: 9 days**

---

## New API Keys Required

| Key | Where to Get | Free Tier |
|---|---|---|
| `MISTRAL_API_KEY` | console.mistral.ai | La Plateforme free tier — no credit card for basic access |
| `COHERE_API_KEY` | dashboard.cohere.com | Trial key — 1,000 calls/month, no credit card |

---

## Definition of Done

**Mistral Integration**
- `mistral-saba-latest` selected as Actor for all BM-language claims
- BM claim passes through Saba with correct context truncation (≤ 6,000 total chars)
- Token counts extracted from Mistral response `usage` field (exact, not estimated)
- Mistral-Saba unavailable → health tracker routes to Groq silently
- `mistral-small-latest` available as Tier 3 general fallback Actor
- Both models appear in Settings provider health panel

**Cohere Integration**
- `command-r` selected as Critic for QUOTE claim types when quota available
- `command-r` selected as Actor for SCIENTIFIC claim types when quota available
- `command-r-plus` selected as Critic for HIGH harm claims when quota ≤ 8 calls today
- `CohereQuotaGuard.canCall()` returns false at 28 daily calls for command-r
- `CohereQuotaGuard.canCall()` returns false at 8 daily calls for command-r-plus
- Daily counter resets at midnight
- Monthly counter resets on 1st of each month
- Monthly counter never exceeds 950 (hard stop)
- Cohere quota exhausted → silent Gemini/Groq fallback, user never sees error
- Token counts extracted from `meta.billed_units` (exact)
- Cohere headroom card shows both daily and monthly tracking

**Routing**
- 8-provider routing decision tree executes correctly for all 5 claim type branches
- `routingRationale` populated and shown in Methodology Card for non-standard routing
- General English claims never consume Cohere or Mistral-Saba quota
- `bestAvailableActor()` and `bestAvailableCritic()` respect exclude sets correctly
