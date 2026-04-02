# Corvus — Feature Enhancement Plan V
## Google Knowledge Graph — Entity Context Panel

> **Scope:** Integration of the Google Knowledge Graph Search API as a parallel enrichment layer in the Corvus result screen. The panel is additive — it never affects the verdict, never blocks the pipeline, and renders silently as null when not relevant. Zero additional cost (100,000 free queries/day).

---

## Design Philosophy

The Entity Context Panel operates on three hard rules:

1. **Never blocks.** The KG call runs in parallel with the main pipeline. If it fails, times out, or returns nothing — the result screen renders normally, the panel simply does not appear. The user never sees an error, a placeholder, or a delay caused by KG.

2. **Never influences.** The `entityContext` field has no connection to `verdict`, `confidence`, `harmAssessment`, or any other verdict field. It is read-only contextual enrichment. The LLM never sees KG data — it reasons only over retrieved sources.

3. **Renders null gracefully.** The panel is conditionally composed — `if (result.entityContext != null)`. When null, it leaves zero visual trace in the layout. No empty card, no placeholder, no section header.

---

## What the Panel Surfaces

| Data Point | KG Field | Displayed When |
|---|---|---|
| Entity name | `name` | Always (if panel renders) |
| Short description | `description` | Always (if panel renders) |
| Detailed Wikipedia extract | `detailedDescription.articleBody` | Length > 60 chars |
| Entity image | `image.contentUrl` | URL is present and loads |
| Birth date | `birthDate` | Entity is a Person |
| Entity types | `@type` (Schema.org) | Always — filtered to human-readable |
| Wikipedia link | `detailedDescription.url` | URL is present |
| Founding date | `foundingDate` | Entity is an Organisation |
| Founding location | `foundingLocation` | Entity is an Organisation |
| Freshness warning | Internal logic | Claim involves current role/status |

---

## Architecture

### Where KG Fits

```
CorvusFactCheckUseCase.check(raw)
    │
    ├── async { runMainPipeline(raw) }          ← Existing pipeline unchanged
    │         ├── ClaimClassifier
    │         ├── ViralDetector
    │         ├── RetrievalLayer (Tavily, GFC, RSS)
    │         └── LlmRouter
    │
    └── async { kgEnricher.enrich(raw) }        ← NEW — parallel, isolated
              ├── EntityExtractor (LLM micro-call)
              ├── KnowledgeGraphClient
              └── EntityContextMapper
    │
    ▼
    awaitAll()
    mainResult.copy(entityContext = kgResult)   ← Merged — kgResult may be null
```

### Package Structure

```
data/
└── remote/
    └── knowledgegraph/
        ├── KnowledgeGraphClient.kt         ← HTTP client
        ├── KgApiResponse.kt                ← Raw API response models
        └── KgEntityMapper.kt               ← Maps raw response → EntityContext

domain/
├── model/
│   └── EntityContext.kt                    ← Domain model
└── usecase/
    └── KgEnricherUseCase.kt                ← Orchestrates extraction + fetch + map

ui/
└── components/
    ├── EntityContextPanel.kt               ← Main composable
    ├── EntityChip.kt                       ← Metadata chip
    └── EntityFreshnessNote.kt              ← Freshness warning sub-component
```

---

## Phase 1 — API Client

### 1.1 API Key Setup

Add to `local.properties`:
```
GOOGLE_KG_API_KEY=your_key_here
```

Add to `build.gradle.kts` (app):
```kotlin
android {
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField(
            "String",
            "GOOGLE_KG_API_KEY",
            "\"${properties["GOOGLE_KG_API_KEY"]}\""
        )
    }
}
```

Add to `local.properties.example`:
```
GOOGLE_KG_API_KEY=your_google_knowledge_graph_api_key
```

Update `KgModule.kt` (new Hilt module):
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object KgModule {
    @Provides
    @Singleton
    fun provideKgApiKey(): String = BuildConfig.GOOGLE_KG_API_KEY
}
```

### 1.2 Raw API Response Models

```kotlin
// data/remote/knowledgegraph/KgApiResponse.kt

@Serializable
data class KgSearchResponse(
    @SerialName("itemListElement")
    val items: List<KgItem> = emptyList()
)

@Serializable
data class KgItem(
    @SerialName("result")
    val result: KgResult,
    @SerialName("resultScore")
    val score: Double = 0.0
)

@Serializable
data class KgResult(
    @SerialName("@id")
    val id: String = "",
    @SerialName("name")
    val name: String = "",
    @SerialName("@type")
    val types: List<String> = emptyList(),
    @SerialName("description")
    val description: String? = null,
    @SerialName("detailedDescription")
    val detailedDescription: KgDetailedDescription? = null,
    @SerialName("image")
    val image: KgImage? = null,
    @SerialName("birthDate")
    val birthDate: String? = null,
    @SerialName("foundingDate")
    val foundingDate: String? = null,
    @SerialName("foundingLocation")
    val foundingLocation: KgLocation? = null,
    @SerialName("url")
    val officialUrl: String? = null
)

@Serializable
data class KgDetailedDescription(
    @SerialName("articleBody")
    val articleBody: String? = null,
    @SerialName("url")
    val url: String? = null,
    @SerialName("license")
    val license: String? = null
)

@Serializable
data class KgImage(
    @SerialName("contentUrl")
    val contentUrl: String? = null,
    @SerialName("url")
    val url: String? = null
)

@Serializable
data class KgLocation(
    @SerialName("name")
    val name: String? = null
)
```

### 1.3 Knowledge Graph HTTP Client

```kotlin
// data/remote/knowledgegraph/KnowledgeGraphClient.kt

class KnowledgeGraphClient @Inject constructor(
    private val httpClient: HttpClient,
    private val apiKey: String
) {
    companion object {
        private const val BASE_URL =
            "https://kgsearch.googleapis.com/v1/entities:search"
        private const val MIN_SCORE_THRESHOLD = 100.0   // KG scores vary 0–1000+
        private const val REQUEST_TIMEOUT_MS  = 3000L   // Hard 3s timeout — never delays result
    }

    suspend fun searchEntity(query: String): KgItem? {
        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            try {
                val response: KgSearchResponse = httpClient.get(BASE_URL) {
                    parameter("query",  query)
                    parameter("key",    apiKey)
                    parameter("limit",  3)          // Top 3 candidates
                    parameter("indent", false)
                }

                response.items
                    .filter { it.score >= MIN_SCORE_THRESHOLD }
                    .maxByOrNull { it.score }       // Return highest-scoring entity
            } catch (e: Exception) {
                null    // Any network/parse error → silent null
            }
        }
    }

    // Overload for when we already know the entity name (from classifier)
    suspend fun searchEntityByName(
        name: String,
        types: List<String> = emptyList()
    ): KgItem? {
        val typeFilter = types.joinToString(",")
        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            try {
                val response: KgSearchResponse = httpClient.get(BASE_URL) {
                    parameter("query",  name)
                    parameter("key",    apiKey)
                    parameter("limit",  5)
                    if (typeFilter.isNotBlank()) parameter("types", typeFilter)
                }
                response.items
                    .filter { it.score >= MIN_SCORE_THRESHOLD }
                    .maxByOrNull { it.score }
            } catch (e: Exception) {
                null
            }
        }
    }
}
```

---

## Phase 2 — Domain Model & Mapper

### 2.1 EntityContext Domain Model

```kotlin
// domain/model/EntityContext.kt

data class EntityContext(
    val name: String,
    val description: String?,
    val detailedSnippet: String?,
    val entityTypes: List<EntityType>,
    val imageUrl: String?,
    val wikipediaUrl: String?,
    val officialUrl: String?,
    val birthDate: String?,
    val foundingDate: String?,
    val foundingLocation: String?,
    val kgScore: Float,
    val requiresFreshnessWarning: Boolean = false
)

// Human-readable entity type classification
enum class EntityType {
    PERSON,
    POLITICIAN,
    ORGANIZATION,
    GOVERNMENT_AGENCY,
    COMPANY,
    PLACE,
    COUNTRY,
    CITY,
    EVENT,
    CREATIVE_WORK,
    OTHER;

    fun displayLabel(): String = when (this) {
        PERSON            -> "Person"
        POLITICIAN        -> "Politician"
        ORGANIZATION      -> "Organisation"
        GOVERNMENT_AGENCY -> "Government Agency"
        COMPANY           -> "Company"
        PLACE             -> "Place"
        COUNTRY           -> "Country"
        CITY              -> "City"
        EVENT             -> "Event"
        CREATIVE_WORK     -> "Creative Work"
        OTHER             -> ""
    }
}
```

### 2.2 Entity Type Mapper

Schema.org types returned by KG are verbose. Map them to the `EntityType` enum:

```kotlin
// data/remote/knowledgegraph/KgEntityMapper.kt

object KgEntityMapper {

    // Schema.org type → EntityType mapping
    private val SCHEMA_TYPE_MAP = mapOf(
        "Person"                to EntityType.PERSON,
        "Politician"            to EntityType.POLITICIAN,
        "Organization"          to EntityType.ORGANIZATION,
        "GovernmentOrganization" to EntityType.GOVERNMENT_AGENCY,
        "Corporation"           to EntityType.COMPANY,
        "LocalBusiness"         to EntityType.COMPANY,
        "Place"                 to EntityType.PLACE,
        "Country"               to EntityType.COUNTRY,
        "City"                  to EntityType.CITY,
        "AdministrativeArea"    to EntityType.PLACE,
        "Event"                 to EntityType.EVENT,
        "CreativeWork"          to EntityType.CREATIVE_WORK,
        "Movie"                 to EntityType.CREATIVE_WORK,
        "Book"                  to EntityType.CREATIVE_WORK,
        "MusicGroup"            to EntityType.CREATIVE_WORK
    )

    fun mapTypes(rawTypes: List<String>): List<EntityType> {
        return rawTypes
            .mapNotNull { SCHEMA_TYPE_MAP[it] }
            .distinct()
            .take(3)    // Cap at 3 types for UI
            .ifEmpty { listOf(EntityType.OTHER) }
    }

    fun map(item: KgItem, requiresFreshnessWarning: Boolean): EntityContext {
        val result = item.result
        val types  = mapTypes(result.types)

        // Clean article body — strip citations, truncate
        val cleanSnippet = result.detailedDescription?.articleBody
            ?.replace(Regex("\\[\\d+\\]"), "")  // Remove Wikipedia citation brackets
            ?.trim()
            ?.take(400)                          // Max 400 chars in UI

        // Best image URL — prefer contentUrl
        val imageUrl = result.image?.contentUrl
            ?: result.image?.url

        return EntityContext(
            name                   = result.name,
            description            = result.description,
            detailedSnippet        = cleanSnippet?.takeIf { it.length > 60 },
            entityTypes            = types,
            imageUrl               = imageUrl,
            wikipediaUrl           = result.detailedDescription?.url,
            officialUrl            = result.officialUrl,
            birthDate              = result.birthDate?.formatKgDate(),
            foundingDate           = result.foundingDate?.formatKgDate(),
            foundingLocation       = result.foundingLocation?.name,
            kgScore                = item.score.toFloat(),
            requiresFreshnessWarning = requiresFreshnessWarning
        )
    }

    // KG dates come in various formats — normalise to readable string
    private fun String.formatKgDate(): String {
        return try {
            val parsed = LocalDate.parse(this.take(10))  // Take first 10 chars (YYYY-MM-DD)
            parsed.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        } catch (e: Exception) {
            this  // Return raw string if parse fails
        }
    }
}
```

---

## Phase 3 — Entity Extraction & Enricher Use Case

### 3.1 Entity Extractor

The KG API works best with a clean entity name, not a full claim sentence. Extract the primary entity before querying:

```kotlin
// domain/usecase/EntityExtractorUseCase.kt

class EntityExtractorUseCase @Inject constructor(
    private val groqClient: GroqClient
) {
    companion object {
        // Fast, cheap local extraction first — Groq only as fallback
        private val NAMED_ENTITY_PATTERN = Regex(
            """(?:^|\s)([A-Z][a-z]+(?:\s+[A-Z][a-z]+){0,3})""" // Simple NER regex
        )
    }

    suspend fun extract(claim: String, classified: ClassifiedClaim): ExtractedEntity? {

        // 1. If classifier already found entities — use them first
        if (classified.entities.isNotEmpty()) {
            return ExtractedEntity(
                name         = classified.entities.first(),
                isSpeaker    = classified.speaker != null,
                claimInvolveCurrentStatus = involvesCurrentStatus(claim)
            )
        }

        // 2. Regex-based extraction — free, instant
        val regexMatch = NAMED_ENTITY_PATTERN.find(claim)
        if (regexMatch != null) {
            return ExtractedEntity(
                name         = regexMatch.groupValues[1],
                isSpeaker    = false,
                claimInvolveCurrentStatus = involvesCurrentStatus(claim)
            )
        }

        // 3. LLM extraction — only if no entity found above
        // Use a very short, cheap prompt
        return try {
            val response = groqClient.complete(
                prompt = """
                    Extract the primary named entity (person, organisation, or place)
                    from this claim. Return ONLY the entity name, nothing else.
                    If no named entity exists, return: NONE
                    
                    Claim: "$claim"
                """.trimIndent(),
                model     = "llama-3.3-70b-versatile",
                maxTokens = 20   // Extremely short — just a name
            )
            val name = response.trim()
            if (name == "NONE" || name.isBlank()) null
            else ExtractedEntity(
                name         = name,
                isSpeaker    = false,
                claimInvolveCurrentStatus = involvesCurrentStatus(claim)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun involvesCurrentStatus(claim: String): Boolean {
        val statusKeywords = listOf(
            "is", "are", "currently", "now", "still", "serves as",
            "menjadi", "sedang", "masih", "kini"
        )
        val lower = claim.lowercase()
        return statusKeywords.any { lower.contains(" $it ") }
    }
}

data class ExtractedEntity(
    val name: String,
    val isSpeaker: Boolean,
    val claimInvolveCurrentStatus: Boolean
)
```

### 3.2 KG Enricher Use Case

Orchestrates extraction → KG fetch → mapping. This is the single entry point called from the main use case:

```kotlin
// domain/usecase/KgEnricherUseCase.kt

class KgEnricherUseCase @Inject constructor(
    private val entityExtractor: EntityExtractorUseCase,
    private val kgClient: KnowledgeGraphClient,
    private val mapper: KgEntityMapper
) {
    // Claim types where KG enrichment is worth attempting
    private val ENRICHABLE_CLAIM_TYPES = setOf(
        ClaimType.PERSON_FACT,
        ClaimType.QUOTE,
        ClaimType.HISTORICAL,
        ClaimType.GENERAL
    )

    // Claim types where KG is never useful
    private val SKIP_CLAIM_TYPES = setOf(
        ClaimType.STATISTICAL,
        ClaimType.SCIENTIFIC,
        ClaimType.CURRENT_EVENT   // KG too stale for breaking/current events
    )

    suspend fun enrich(
        claim: String,
        classified: ClassifiedClaim
    ): EntityContext? {

        // Skip immediately for non-enrichable claim types
        if (classified.type in SKIP_CLAIM_TYPES) return null

        // Extract entity name
        val entity = entityExtractor.extract(claim, classified) ?: return null

        // Query Knowledge Graph
        val kgItem = if (classified.type == ClaimType.PERSON_FACT) {
            kgClient.searchEntityByName(entity.name, types = listOf("Person"))
        } else {
            kgClient.searchEntity(entity.name)
        } ?: return null

        // Map to domain model
        val requiresFreshnessWarning =
            entity.claimInvolveCurrentStatus &&
            kgItem.result.types.any { it == "Person" }

        return KgEntityMapper.map(kgItem, requiresFreshnessWarning)
    }
}
```

### 3.3 Integration into Main Use Case

```kotlin
// domain/usecase/CorvusFactCheckUseCase.kt — updated check() function

suspend fun check(raw: String): CorvusCheckResult = coroutineScope {

    // Classify claim first — needed for both pipelines
    val classified = claimClassifier.classify(raw)

    // Main pipeline and KG enrichment run in parallel
    val mainResultDeferred = async {
        compositePipeline.check(raw, classified)
    }

    val entityContextDeferred = async {
        runCatching {
            kgEnricher.enrich(raw, classified)
        }.getOrNull()  // Any exception → null, never propagates
    }

    val mainResult   = mainResultDeferred.await()
    val entityContext = entityContextDeferred.await()

    // Merge — entityContext may be null, that is expected and fine
    return@coroutineScope when (mainResult) {
        is CorvusCheckResult.GeneralResult ->
            mainResult.copy(entityContext = entityContext)
        is CorvusCheckResult.QuoteResult ->
            mainResult.copy(entityContext = entityContext)
        is CorvusCheckResult.CompositeCheckResult ->
            mainResult.copy(entityContext = entityContext)
        else -> mainResult
    }
}
```

---

## Phase 4 — UI Components

### 4.1 EntityContextPanel — Main Composable

```kotlin
// ui/components/EntityContextPanel.kt

@Composable
fun EntityContextPanel(
    entity: EntityContext,
    modifier: Modifier = Modifier
) {
    var imageLoadFailed by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, CorvusTheme.colors.border),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = CorvusTheme.colors.surfaceRaised
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            // ── Section header ───────────────────────────────────────────
            Text(
                text          = "ENTITY CONTEXT",
                style         = CorvusTheme.typography.labelSmall,
                color         = CorvusTheme.colors.textSecondary,
                letterSpacing = 1.sp,
                fontFamily    = IbmPlexMono
            )

            Spacer(Modifier.height(12.dp))

            // ── Entity header row: image + name + description ────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.Top
            ) {
                // Entity image — only if URL present and loads successfully
                if (entity.imageUrl != null && !imageLoadFailed) {
                    AsyncImage(
                        model           = entity.imageUrl,
                        contentDescription = entity.name,
                        modifier        = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .border(
                                1.dp,
                                CorvusTheme.colors.border,
                                RoundedCornerShape(2.dp)
                            ),
                        contentScale    = ContentScale.Crop,
                        onError         = { imageLoadFailed = true }
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    // Entity name in DM Serif — editorial weight
                    Text(
                        text      = entity.name,
                        style     = CorvusTheme.typography.headlineSmall,
                        color     = CorvusTheme.colors.textPrimary,
                        fontFamily = DmSerifDisplay,
                        maxLines  = 2,
                        overflow  = TextOverflow.Ellipsis
                    )

                    // Short KG description — accent colour
                    entity.description?.let { desc ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text      = desc,
                            style     = CorvusTheme.typography.labelSmall,
                            color     = CorvusTheme.colors.accent,
                            fontFamily = IbmPlexMono,
                            maxLines  = 2,
                            overflow  = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // ── Detailed Wikipedia snippet ───────────────────────────────
            entity.detailedSnippet?.let { snippet ->
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = CorvusTheme.colors.border)
                Spacer(Modifier.height(12.dp))
                Text(
                    text     = snippet,
                    style    = CorvusTheme.typography.bodySmall,
                    color    = CorvusTheme.colors.textSecondary,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
            }

            // ── Metadata chips ───────────────────────────────────────────
            val chips = buildEntityChips(entity)
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding        = PaddingValues(0.dp)
                ) {
                    items(chips, key = { it.label }) { chip ->
                        EntityChip(chip)
                    }
                }
            }

            // ── Freshness warning ────────────────────────────────────────
            if (entity.requiresFreshnessWarning) {
                Spacer(Modifier.height(10.dp))
                EntityFreshnessNote()
            }

            // ── Footer: Wikipedia link ───────────────────────────────────
            entity.wikipediaUrl?.let { url ->
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = CorvusTheme.colors.border)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text      = "Source: Wikipedia / Knowledge Graph",
                        style     = CorvusTheme.typography.labelSmall,
                        color     = CorvusTheme.colors.textTertiary,
                        fontFamily = IbmPlexMono
                    )
                    TextButton(
                        onClick         = { uriHandler.openUri(url) },
                        contentPadding  = PaddingValues(0.dp)
                    ) {
                        Text(
                            text      = "Read more →",
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
```

### 4.2 Entity Chip Data & Composable

```kotlin
// ui/components/EntityChip.kt

data class EntityChipData(
    val label: String,
    val icon: ImageVector? = null
)

fun buildEntityChips(entity: EntityContext): List<EntityChipData> = buildList {
    // Entity types — max 2
    entity.entityTypes
        .filter { it != EntityType.OTHER }
        .take(2)
        .forEach { add(EntityChipData(it.displayLabel())) }

    // Person-specific
    entity.birthDate?.let {
        add(EntityChipData("Born $it", CorvusIcons.Calendar))
    }

    // Organisation-specific
    entity.foundingDate?.let {
        add(EntityChipData("Founded $it", CorvusIcons.Calendar))
    }
    entity.foundingLocation?.let {
        add(EntityChipData(it, CorvusIcons.MapPin))
    }
}

@Composable
fun EntityChip(chip: EntityChipData) {
    Surface(
        color  = CorvusTheme.colors.surface,
        shape  = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, CorvusTheme.colors.border)
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            chip.icon?.let { icon ->
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    modifier           = Modifier.size(10.dp),
                    tint               = CorvusTheme.colors.textTertiary
                )
            }
            Text(
                text      = chip.label,
                style     = CorvusTheme.typography.labelSmall,
                color     = CorvusTheme.colors.textTertiary,
                fontFamily = IbmPlexMono
            )
        }
    }
}
```

### 4.3 Freshness Note Sub-Component

```kotlin
// ui/components/EntityFreshnessNote.kt

@Composable
fun EntityFreshnessNote() {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector        = CorvusIcons.Info,
            contentDescription = null,
            modifier           = Modifier.size(12.dp),
            tint               = CorvusTheme.colors.textTertiary
        )
        Text(
            text       = "Entity data may not reflect recent changes in role or status.",
            style      = CorvusTheme.typography.labelSmall,
            color      = CorvusTheme.colors.textTertiary,
            fontFamily = IbmPlexMono,
            fontStyle  = FontStyle.Italic
        )
    }
}
```

### 4.4 Panel Entry Animation

The panel reveals with a subtle fade + vertical slide after the main result cards are visible. It arrives slightly later, reinforcing its supplementary nature:

```kotlin
// In ResultScreen.kt — inside LazyColumn

// After GroundedFactsList item:
item(key = "entity_context") {
    result.entityContext?.let { entity ->

        var visible by remember { mutableStateOf(false) }

        LaunchedEffect(entity) {
            delay(450)  // Arrives after verdict card (280ms) and facts
            visible = true
        }

        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(400)) +
                      slideInVertically(
                          initialOffsetY = { it / 4 },
                          animationSpec  = tween(400, easing = FastOutSlowInEasing)
                      )
        ) {
            EntityContextPanel(
                entity   = entity,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
```

### 4.5 Loading State (Skeleton)

While KG resolves in parallel, show a skeleton placeholder in the entity context position. Only if the main result has rendered — the skeleton never shows before the verdict:

```kotlin
@Composable
fun EntityContextSkeleton() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        border = BorderStroke(1.dp, CorvusTheme.colors.border),
        shape  = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = CorvusTheme.colors.surfaceRaised)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            ShimmerBox(width = 120.dp, height = 11.dp)   // "ENTITY CONTEXT" label
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShimmerBox(width = 52.dp, height = 52.dp)  // Image placeholder
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShimmerBox(width = 160.dp, height = 18.dp)  // Name
                    ShimmerBox(width = 120.dp, height = 12.dp)  // Description
                }
            }
        }
    }
}

// Shimmer utility composable — infinite shimmer animation
@Composable
fun ShimmerBox(width: Dp, height: Dp) {
    val shimmerColors = listOf(
        CorvusTheme.colors.border,
        CorvusTheme.colors.surfaceRaised,
        CorvusTheme.colors.border
    )
    val transition = rememberInfiniteTransition()
    val offset by transition.animateFloat(
        initialValue   = 0f,
        targetValue    = 1000f,
        animationSpec  = infiniteRepeatable(tween(1200), RepeatMode.Restart)
    )
    Box(
        modifier = Modifier
            .size(width, height)
            .clip(RoundedCornerShape(2.dp))
            .background(
                Brush.horizontalGradient(
                    colors      = shimmerColors,
                    startX      = offset - 500f,
                    endX        = offset
                )
            )
    )
}
```

### 4.6 Result Screen LazyColumn — Full Item Order

With the entity panel positioned correctly:

```kotlin
LazyColumn(
    state           = listState,
    contentPadding  = PaddingValues(bottom = 88.dp),   // Space for action bar
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    // 1. Recency warning (conditional)
    result.recencySignal?.let { signal ->
        if (signal == RecencySignal.BREAKING) {
            item(key = "recency_banner") {
                RecencyWarningBanner(signal, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }

    // 2. Viral warning (conditional)
    result.viralDetection?.let { viral ->
        if (viral is ViralDetectionResult.KnownHoax) {
            item(key = "viral_banner") {
                ViralWarningBanner(viral, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }

    // 3. Verdict card — always present
    item(key = "verdict_card") {
        VerdictCard(result, modifier = Modifier.padding(horizontal = 16.dp))
    }

    // 4. Missing context callout (conditional — MISLEADING / OUT_OF_CONTEXT)
    result.missingContext?.let { ctx ->
        item(key = "missing_context") {
            MissingContextCallout(ctx, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }

    // 5. Kernel of truth (conditional — MISLEADING / PARTIALLY_TRUE)
    result.kernelOfTruth?.let { kernel ->
        item(key = "kernel_of_truth") {
            KernelOfTruthCard(
                kernel       = kernel,
                sources      = result.sources,
                onSourceClick = { scrollToSource(it) },
                modifier     = Modifier.padding(horizontal = 16.dp)
            )
        }
    }

    // 6. Grounded key facts
    item(key = "key_facts") {
        GroundedFactsList(
            facts        = result.keyFacts,
            sources      = result.sources,
            onSourceClick = { scrollToSource(it) },
            modifier     = Modifier.padding(horizontal = 16.dp)
        )
    }

    // 7. Confidence timeline (conditional — 2+ data points)
    result.confidenceTimeline?.let { timeline ->
        if (timeline.entries.size >= 2) {
            item(key = "confidence_timeline") {
                ConfidenceTimelineCard(timeline, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }

    // ── ENTITY CONTEXT PANEL ────────────────────────────────────────────
    // 8. Entity context (conditional — KG returned data)
    item(key = "entity_context") {
        val entityVisible = remember { mutableStateOf(false) }
        LaunchedEffect(result.entityContext) {
            delay(450)
            entityVisible.value = true
        }

        AnimatedVisibility(visible = entityVisible.value) {
            result.entityContext?.let { entity ->
                EntityContextPanel(
                    entity   = entity,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // Show skeleton while KG is still loading (entityContext == null but loading)
        if (result.entityContext == null && result.isEntityContextLoading) {
            EntityContextSkeleton()
        }
    }
    // ────────────────────────────────────────────────────────────────────

    // 9. Sources header
    item(key = "sources_header") {
        SourcesSectionHeader(
            sources  = result.sources,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }

    // 10. Source cards
    itemsIndexed(
        items = result.sources,
        key   = { _, source -> source.url ?: source.title }
    ) { index, source ->
        SourceCard(
            source      = source,
            numberLabel = index + 1,
            modifier    = Modifier.padding(horizontal = 16.dp)
        )
    }

    // 11. Methodology card
    item(key = "methodology") {
        result.methodologyMetadata?.let { meta ->
            MethodologyCard(meta, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}
```

---

## Phase 5 — ViewModel & UI State

### 5.1 UI State Extension

```kotlin
// ui/viewmodel/CorvusUiState.kt — updated

data class CorvusUiState(
    val inputText              : String = "",
    val isLoading              : Boolean = false,
    val result                 : CorvusCheckResult? = null,
    val error                  : CorvusError? = null,
    val currentStep            : PipelineStep = PipelineStep.IDLE,
    val isEntityContextLoading : Boolean = false   // NEW — shows skeleton while KG resolves
)
```

### 5.2 ViewModel Update

```kotlin
// ui/viewmodel/CorvusViewModel.kt — updated check() function

fun check(claim: String) {
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, isEntityContextLoading = true) }

        try {
            // Main result arrives first (KG runs in parallel inside use case)
            val result = factCheckUseCase.check(claim)

            _uiState.update {
                it.copy(
                    isLoading              = false,
                    isEntityContextLoading = result.entityContext == null,
                    // If entity context came back with main result — loading is done
                    result                 = result
                )
            }

            // If entity context wasn't ready with main result,
            // update state again when KG resolves
            // (In practice this is rare — KG usually finishes within 3s)
            if (result.entityContext == null) {
                val enriched = kgEnricher.enrich(claim, factCheckUseCase.lastClassified)
                _uiState.update { state ->
                    state.copy(
                        isEntityContextLoading = false,
                        result = state.result?.withEntityContext(enriched)
                    )
                }
            }

        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading              = false,
                    isEntityContextLoading = false,
                    error                  = CorvusError.Unknown(e.message ?: "")
                )
            }
        }
    }
}
```

---

## Phase 6 — Hilt Dependency Wiring

```kotlin
// di/KgModule.kt

@Module
@InstallIn(SingletonComponent::class)
object KgModule {

    @Provides
    @Singleton
    fun provideKnowledgeGraphClient(
        httpClient : HttpClient,
        @Named("kgApiKey") apiKey: String
    ): KnowledgeGraphClient = KnowledgeGraphClient(httpClient, apiKey)

    @Provides
    @Named("kgApiKey")
    fun provideKgApiKey(): String = BuildConfig.GOOGLE_KG_API_KEY

    @Provides
    @Singleton
    fun provideKgEntityMapper(): KgEntityMapper = KgEntityMapper

    @Provides
    @Singleton
    fun provideEntityExtractorUseCase(
        groqClient: GroqClient
    ): EntityExtractorUseCase = EntityExtractorUseCase(groqClient)

    @Provides
    @Singleton
    fun provideKgEnricherUseCase(
        extractor : EntityExtractorUseCase,
        client    : KnowledgeGraphClient,
        mapper    : KgEntityMapper
    ): KgEnricherUseCase = KgEnricherUseCase(extractor, client, mapper)
}
```

---

## Phase 7 — Caching (Quota Protection)

100,000 free queries/day is generous, but the same entity will be searched repeatedly — every time a user checks a claim about Anwar Ibrahim, for example. Cache KG results in Room to avoid redundant API calls.

### Cache Entity

```kotlin
// data/local/entity/KgCacheEntity.kt

@Entity(tableName = "kg_cache")
data class KgCacheEntity(
    @PrimaryKey
    val queryKey   : String,         // Normalised entity name (lowercase, trimmed)
    val entityJson : String,         // JSON-serialised EntityContext
    val cachedAt   : Long = System.currentTimeMillis()
)

@Dao
interface KgCacheDao {
    @Query("SELECT * FROM kg_cache WHERE queryKey = :key LIMIT 1")
    suspend fun get(key: String): KgCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: KgCacheEntity)

    // Expire entries older than 7 days
    // KG data is stable — 7 days is appropriate for biographical facts
    @Query("DELETE FROM kg_cache WHERE cachedAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)
}
```

### Cache-Aware Client

```kotlin
// In KgEnricherUseCase — wrap KG call with cache

suspend fun enrich(claim: String, classified: ClassifiedClaim): EntityContext? {
    val entity = entityExtractor.extract(claim, classified) ?: return null
    val cacheKey = entity.name.lowercase().trim()

    // 1. Check cache first
    val cached = kgCacheDao.get(cacheKey)
    if (cached != null) {
        val age = System.currentTimeMillis() - cached.cachedAt
        if (age < 7.days.inWholeMilliseconds) {
            return Json.decodeFromString<EntityContext>(cached.entityJson)
        }
    }

    // 2. Fetch from API
    val kgItem = kgClient.searchEntityByName(entity.name) ?: return null
    val entityContext = KgEntityMapper.map(
        kgItem,
        entity.claimInvolveCurrentStatus && kgItem.result.types.contains("Person")
    )

    // 3. Persist to cache
    kgCacheDao.insert(
        KgCacheEntity(
            queryKey   = cacheKey,
            entityJson = Json.encodeToString(entityContext)
        )
    )

    return entityContext
}
```

### Cache Purge Worker

```kotlin
class KgCachePurgeWorker(
    context : Context,
    params  : WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val cutoff = System.currentTimeMillis() - 7.days.inWholeMilliseconds
        kgCacheDao.purgeOlderThan(cutoff)
        return Result.success()
    }
}

// Schedule weekly — lightweight
WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "kg_cache_purge",
    ExistingPeriodicWorkPolicy.KEEP,
    PeriodicWorkRequestBuilder<KgCachePurgeWorker>(7, TimeUnit.DAYS).build()
)
```

---

## Phase 8 — Testing

### Unit Tests

```kotlin
// KgEntityMapperTest.kt
class KgEntityMapperTest {

    @Test fun `maps Person type correctly`() { ... }
    @Test fun `maps Organisation type correctly`() { ... }
    @Test fun `cleans Wikipedia citation brackets from snippet`() { ... }
    @Test fun `truncates snippet to 400 chars`() { ... }
    @Test fun `formats KG date string correctly`() { ... }
    @Test fun `returns null snippet when under 60 chars`() { ... }
    @Test fun `sets requiresFreshnessWarning true for current status Person claims`() { ... }
    @Test fun `sets requiresFreshnessWarning false for historical claims`() { ... }
}

// EntityExtractorUseCaseTest.kt
class EntityExtractorUseCaseTest {

    @Test fun `extracts entity from classified claim entities list`() { ... }
    @Test fun `falls back to regex when no classified entities`() { ... }
    @Test fun `detects current status keywords correctly in English`() { ... }
    @Test fun `detects current status keywords correctly in BM`() { ... }
    @Test fun `returns null when no entity detected`() { ... }
}

// KgEnricherUseCaseTest.kt
class KgEnricherUseCaseTest {

    @Test fun `returns null for STATISTICAL claim type without entity`() { ... }
    @Test fun `returns null for CURRENT_EVENT claim type`() { ... }
    @Test fun `returns null when KG client returns null`() { ... }
    @Test fun `returns null when KG client throws exception`() { ... }
    @Test fun `returns EntityContext when all layers succeed`() { ... }
    @Test fun `returns cached result without calling API`() { ... }
    @Test fun `calls API when cache expired`() { ... }
}
```

### Integration Tests

```kotlin
// Claim: "Anwar Ibrahim is the Prime Minister of Malaysia"
// Expected: EntityContext with name="Anwar Ibrahim", description contains "Prime Minister"

// Claim: "Malaysia's GDP grew 3.4% in Q4 2023"
// Expected: entityContext == null (STATISTICAL type — no named person entity)

// Claim: "Siti Nurhaliza released a new album"
// Expected: EntityContext with name="Siti Nurhaliza", entityTypes contains CREATIVE_WORK or PERSON

// Claim: "KG API times out"
// Expected: main result unaffected, entityContext == null, no error shown to user
```

### UI Tests

```kotlin
// EntityContextPanelTest.kt
@Test fun `panel renders when entityContext is non-null`() { ... }
@Test fun `panel is absent when entityContext is null`() { ... }
@Test fun `freshness note renders when requiresFreshnessWarning is true`() { ... }
@Test fun `image placeholder shows when imageUrl is null`() { ... }
@Test fun `Wikipedia link opens correct URL`() { ... }
@Test fun `chips render correct labels for Person entity`() { ... }
@Test fun `chips render correct labels for Organisation entity`() { ... }
@Test fun `skeleton renders when isEntityContextLoading is true`() { ... }
@Test fun `panel animates in 450ms after result renders`() { ... }
```

---

## API Key Required

| Key | Source | Free Limit |
|---|---|---|
| `GOOGLE_KG_API_KEY` | Google Cloud Console (same project as Fact Check key) | 100,000 req/day |

Same Google Cloud project as existing `GOOGLE_FACT_CHECK_API_KEY`. Enable *Knowledge Graph Search API* in the Cloud Console — no new billing setup needed if the project already has billing attached for Fact Check.

---

## Estimated Roadmap

| Phase | Task | Duration |
|---|---|---|
| 1 | API client + response models | 1 day |
| 2 | Domain model + entity mapper | 1 day |
| 3 | Entity extractor + enricher use case | 2 days |
| 4 | UI components (panel, chips, skeleton, animation) | 3 days |
| 5 | ViewModel + UI state wiring | 1 day |
| 6 | Hilt module wiring | 0.5 day |
| 7 | Room cache + purge worker | 1 day |
| 8 | Unit + integration + UI tests | 2 days |
| **Total** | | **~11.5 days** |

---

## Definition of Done

**Pipeline**
- KG call always runs in parallel — never sequential, never blocking main result
- KG timeout (3s) never delays result screen render
- Any KG exception silently returns null — zero propagation to main result
- `entityContext` is null for STATISTICAL and CURRENT_EVENT claim types always
- Cached result returned within 7-day window without API call

**Entity Mapper**
- Wikipedia citation brackets `[1]` stripped from detailed snippet
- Snippet truncated to 400 chars with no mid-word cut
- Date strings formatted as `d MMM yyyy`
- Schema.org types correctly mapped to `EntityType` enum
- `requiresFreshnessWarning` true only for Person + current-status claims

**UI**
- Panel renders only when `entityContext != null`
- Zero visual trace when `entityContext == null` — no empty space, no placeholder header
- Skeleton shows only when `isEntityContextLoading == true` AND main result already rendered
- Panel entry animation fires 450ms after result screen appears
- Image load failure falls back cleanly to no-image layout
- Freshness note renders when `requiresFreshnessWarning == true`
- Wikipedia link opens correctly in system browser
- All chips render correct labels for Person and Organisation entity types

**Cache**
- Cache hit avoids API call — verified via mock
- Cache miss calls API and persists result
- Entries older than 7 days are purged by weekly WorkManager job
- Cache table included in Room schema migration
