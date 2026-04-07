# Corvus — Enhancement Plan X
## Contextual Media Enrichment
### Entity Portraits · Country Flags · Organisation Logos · Place Photos

> **Scope:** Add contextual media to the result screen when a claim involves a named entity whose image can be retrieved unambiguously from free APIs. Hooks into the existing `EntityContext` system from Plan V. No new paid dependencies. No new API keys required. Strictly non-blocking — media never delays the pipeline verdict.

---

## Why This Matters

The current `EntityContextPanel` is text-only. A claim about Anwar Ibrahim shows his description and a Wikipedia extract — but no visual anchor. A claim about Malaysia shows country context — but no flag. For users reading fact-check results on a phone, visual context is processed faster than text and builds immediate confidence that the right entity was identified.

Three explicit scenarios where media adds clear value:

**Person claims** — A portrait confirms "yes, this is the same Anwar Ibrahim who is the Prime Minister, not a different person with the same name." Disambiguation via image rather than text.

**Country claims** — A flag is instantly scannable. Users recognise a flag in under 100ms. The text "Malaysia" takes longer to process than the Jalur Gemilang.

**Organisation claims** — A logo creates immediate brand recognition. "Petronas" with the logo is more anchored than "Petronas" as plain text.

---

## Design Constraints

Three rules that govern this feature:

1. **Never delay the verdict.** All media resolution runs in parallel with the main pipeline under a hard 5-second timeout. If it misses, it misses silently — `EntityMedia.NotFound` is a valid result, not an error.

2. **Only show unambiguous media.** A Wikipedia portrait returned for "Ahmad" (a very common name) is likely wrong. Disambiguation checks and aspect ratio validation reject bad matches before they reach the UI.

3. **Attribution is mandatory.** Wikipedia images are Creative Commons licensed. Every Wikipedia-sourced image shows a "Wikipedia" attribution link. Clearbit logos are used under Clearbit's terms.

---

## Free APIs Used

No new API keys required. All four sources are keyless:

| API | What It Provides | Auth | Rate Limit |
|---|---|---|---|
| Wikipedia REST API | Person portraits, place photos, org images, descriptions | None | Polite use — no hard limit |
| REST Countries API | Official country data, flag URLs | None | None documented |
| FlagCDN | SVG + PNG flags by ISO 2-letter code | None | CDN-served, no limit |
| Clearbit Logo API | Company logos by domain | None | ~600 req/month implicit |

Wikipedia requires a descriptive `User-Agent` header. `WikipediaMediaClient` sends `User-Agent: CorvusFactChecker/1.0` on every request to comply with their bot policy.

---

## Phase 1 — Data Models

```kotlin
// domain/model/EntityMedia.kt

sealed class EntityMedia {

    data class Portrait(
        val imageUrl   : String,
        val altText    : String,         // Person's name — for accessibility
        val sourceUrl  : String,         // Wikipedia article URL — for attribution
        val confidence : MediaConfidence
    ) : EntityMedia()

    data class CountryFlag(
        val svgUrl      : String,
        val pngUrl      : String,
        val isoCode     : String,        // "MY", "US", "GB" etc.
        val countryName : String,
        val capital     : String?,
        val population  : Long?
    ) : EntityMedia()

    data class OrgLogo(
        val imageUrl   : String,
        val orgName    : String,
        val domain     : String?,        // null if source was Wikipedia not Clearbit
        val sourceUrl  : String,
        val confidence : MediaConfidence
    ) : EntityMedia()

    data class PlacePhoto(
        val imageUrl    : String,
        val placeName   : String,
        val description : String?,
        val sourceUrl   : String
    ) : EntityMedia()

    // Lookup ran but no suitable media found
    object NotFound : EntityMedia()

    // Lookup was intentionally skipped (STATISTICAL/SCIENTIFIC claim, etc.)
    object Skipped : EntityMedia()
}

enum class MediaConfidence {
    HIGH,    // Exact Wikipedia page match, unambiguous — e.g. "Anwar_Ibrahim" redirects directly
    MEDIUM,  // Wikipedia match but disambiguation was needed
    LOW      // Clearbit fallback or indirect match — UI shows warning overlay
}
```

### Update `EntityContext`

```kotlin
// domain/model/EntityContext.kt — add media field

data class EntityContext(
    val entityName  : String,
    val description : String?,
    val wikiExtract : String?,
    val wikiUrl     : String?,
    val entityType  : EntityType,
    val media       : EntityMedia = EntityMedia.Skipped,   // ← NEW
    val cachedAt    : Long = System.currentTimeMillis()
)

enum class EntityType {
    PERSON, COUNTRY, ORGANISATION, PLACE, EVENT, CONCEPT, UNKNOWN
}
```

---

## Phase 2 — Wikipedia Media Client

```kotlin
// data/remote/media/WikipediaMediaClient.kt

class WikipediaMediaClient @Inject constructor(
    private val httpClient: HttpClient
) {
    companion object {
        private const val BASE_URL   = "https://en.wikipedia.org/api/rest_v1/page/summary"
        private const val TIMEOUT_MS = 4_000L
    }

    suspend fun fetchSummary(pageTitle: String): WikipediaSummary? {
        return withTimeout(TIMEOUT_MS) {
            runCatching {
                httpClient.get("$BASE_URL/${pageTitle.encodeURLPath()}") {
                    header("Accept", "application/json")
                    header("User-Agent", "CorvusFactChecker/1.0")
                }.body<WikipediaSummary>()
            }.getOrNull()
        }
    }

    // Person lookup with disambiguation retry
    // Tries exact title first, then with Malaysian-context qualifiers
    suspend fun fetchPersonSummary(personName: String): WikipediaSummary? {
        val normalised = personName.trim().replace(" ", "_")

        // Pass 1: exact match
        fetchSummary(normalised)?.let { summary ->
            if (summary.type != "disambiguation" && summary.thumbnail != null) {
                return summary
            }
        }

        // Pass 2: Malaysian political context qualifiers
        val qualifiers = listOf(
            "politician",
            "Prime_Minister_of_Malaysia",
            "Malaysian_politician",
            "Deputy_Prime_Minister_of_Malaysia",
            "Malaysian_businessman",
            "Malaysian_footballer"
        )

        qualifiers.forEach { qualifier ->
            fetchSummary("${normalised}_($qualifier)")?.let { summary ->
                if (summary.type != "disambiguation" && summary.thumbnail != null) {
                    return summary
                }
            }
        }

        return null
    }
}

// Serializable response models

@Serializable
data class WikipediaSummary(
    val type        : String,
    val title       : String,
    @SerialName("display_title")
    val displayTitle : String? = null,
    val description  : String? = null,
    val extract      : String? = null,
    val thumbnail    : WikiThumbnail? = null,
    @SerialName("content_urls")
    val contentUrls  : WikiContentUrls? = null
)

@Serializable
data class WikiThumbnail(
    val source : String,
    val width  : Int,
    val height : Int
)

@Serializable
data class WikiContentUrls(
    val desktop : WikiUrlPair? = null
)

@Serializable
data class WikiUrlPair(
    val page : String? = null
)
```

---

## Phase 3 — REST Countries Client

```kotlin
// data/remote/media/RestCountriesClient.kt

class RestCountriesClient @Inject constructor(
    private val httpClient: HttpClient
) {
    companion object {
        private const val BASE_URL   = "https://restcountries.com/v3.1"
        private const val TIMEOUT_MS = 4_000L

        // FlagCDN — faster than REST Countries for flags alone
        fun flagPngUrl(isoCode: String) =
            "https://flagcdn.com/w160/${isoCode.lowercase()}.png"

        fun flagSvgUrl(isoCode: String) =
            "https://flagcdn.com/${isoCode.lowercase()}.svg"
    }

    // By country name — used when ISO code not in local map
    suspend fun fetchByName(countryName: String): RestCountry? {
        return withTimeout(TIMEOUT_MS) {
            runCatching {
                httpClient.get("$BASE_URL/name/${countryName.encodeURLPath()}") {
                    parameter("fields", "name,cca2,flags,capital,population,region")
                }.body<List<RestCountry>>()
                    .firstOrNull()
            }.getOrNull()
        }
    }

    // By ISO code — used when code is already known
    suspend fun fetchByIso(isoCode: String): RestCountry? {
        return withTimeout(TIMEOUT_MS) {
            runCatching {
                httpClient.get("$BASE_URL/alpha/$isoCode") {
                    parameter("fields", "name,cca2,flags,capital,population")
                }.body<List<RestCountry>>()
                    .firstOrNull()
            }.getOrNull()
        }
    }
}

@Serializable
data class RestCountry(
    val name       : RestCountryName,
    val cca2       : String,
    val flags      : RestCountryFlags,
    val capital    : List<String>? = null,
    val population : Long? = null,
    val region     : String? = null
)

@Serializable
data class RestCountryName(
    val common   : String,
    val official : String
)

@Serializable
data class RestCountryFlags(
    val png : String,
    val svg : String
)
```

---

## Phase 4 — Clearbit Logo Client

Clearbit is URL-only — no HTTP client needed. URLs are constructed and passed directly to Coil for image loading. If the URL 404s, Coil's error placeholder handles it gracefully.

```kotlin
// data/remote/media/ClearbitLogoClient.kt

object ClearbitLogoClient {

    fun logoUrl(domain: String): String =
        "https://logo.clearbit.com/${domain.lowercase().trim()}"

    // Map of known Malaysian and major organisations to their domains
    // Used for the fast-path — avoids needing Wikipedia for well-known orgs
    fun guessOrgDomain(orgName: String): String? =
        KNOWN_ORG_DOMAINS[orgName.lowercase().trim()]

    private val KNOWN_ORG_DOMAINS = mapOf(
        // Malaysian GLCs and major corporations
        "petronas"               to "petronas.com",
        "petroliam nasional"     to "petronas.com",
        "maybank"                to "maybank.com",
        "malayan banking"        to "maybank.com",
        "cimb"                   to "cimb.com",
        "cimb bank"              to "cimb.com",
        "tenaga nasional"        to "tnb.com.my",
        "tnb"                    to "tnb.com.my",
        "malaysia airlines"      to "malaysiaairlines.com",
        "mas"                    to "malaysiaairlines.com",
        "airasia"                to "airasia.com",
        "air asia"               to "airasia.com",
        "sime darby"             to "simedarby.com",
        "khazanah"               to "khazanah.com.my",
        "khazanah nasional"      to "khazanah.com.my",
        "maxis"                  to "maxis.com.my",
        "celcom"                 to "celcom.com.my",
        "digi"                   to "digi.com.my",
        "telekom malaysia"       to "tm.com.my",
        "tm"                     to "tm.com.my",
        "bank negara"            to "bnm.gov.my",
        "bank negara malaysia"   to "bnm.gov.my",
        "bernama"                to "bernama.com",
        "rtm"                    to "rtm.gov.my",
        "astro"                  to "astro.com.my",
        "rhb"                    to "rhb.com.my",
        "rhb bank"               to "rhb.com.my",
        "public bank"            to "pbebank.com",
        "hong leong bank"        to "hlb.com.my",
        "ioi"                    to "ioigroup.com",
        "ioi group"              to "ioigroup.com",
        "ytl"                    to "ytl.com",
        "ytl corporation"        to "ytl.com",
        "axiata"                 to "axiata.com",
        "mimos"                  to "mimos.my",
        "mdec"                   to "mdec.my",
        "mida"                   to "mida.gov.my",
        "pemandu"                to "pemandu.gov.my",
        // Global tech — most Clearbit claims are these
        "google"                 to "google.com",
        "microsoft"              to "microsoft.com",
        "apple"                  to "apple.com",
        "meta"                   to "meta.com",
        "amazon"                 to "amazon.com",
        "openai"                 to "openai.com",
        "anthropic"              to "anthropic.com"
    )
}
```

---

## Phase 5 — Entity Type Detector

Detects whether an entity name refers to a person, country, organisation, or place. Uses the KG entity type if available, falls back to heuristics.

```kotlin
// domain/util/EntityTypeDetector.kt

object EntityTypeDetector {

    fun detect(entityName: String, kgEntityType: String?): EntityType {
        // 1. KG entity type from Google Knowledge Graph — most reliable
        if (!kgEntityType.isNullOrBlank()) {
            return when (kgEntityType.lowercase()) {
                "person"                      -> EntityType.PERSON
                "country", "nation"           -> EntityType.COUNTRY
                "place", "location",
                "city", "state", "region",
                "administrativearea"          -> EntityType.PLACE
                "organization", "company",
                "government", "corporation",
                "ngo", "institution"          -> EntityType.ORGANISATION
                else                          -> EntityType.UNKNOWN
            }
        }

        val lower = entityName.lowercase().trim()

        // 2. Country name lookup
        if (COUNTRY_NAMES.contains(lower)) return EntityType.COUNTRY

        // 3. Organisation suffix detection
        val orgSuffixes = setOf(
            "berhad", "bhd", "sdn", "corporation", "corp", "limited", "ltd",
            "holdings", "group", "bank", "airlines", "airways", "petroleum",
            "nasional", "authority", "commission", "board", "foundation",
            "ministry", "jabatan", "lembaga", "suruhanjaya", "majlis",
            "agency", "institute", "university", "universiti", "college"
        )
        if (orgSuffixes.any { lower.contains(it) }) return EntityType.ORGANISATION

        // 4. Malaysian city/place detection
        val myPlaces = setOf(
            "kuala lumpur", "kl", "petaling jaya", "pj", "shah alam", "johor bahru",
            "penang", "georgetown", "ipoh", "kota kinabalu", "kuching", "putrajaya",
            "cyberjaya", "subang", "klang", "ampang", "seremban", "melaka", "malacca"
        )
        if (myPlaces.contains(lower)) return EntityType.PLACE

        // 5. Heuristic: 2–4 words, all capitalised → likely a person name
        val words = entityName.trim().split(" ")
        if (words.size in 2..4 && words.all { it.first().isUpperCase() }) {
            return EntityType.PERSON
        }

        return EntityType.UNKNOWN
    }

    private val COUNTRY_NAMES = setOf(
        "malaysia", "singapore", "indonesia", "thailand", "philippines",
        "brunei", "myanmar", "vietnam", "cambodia", "laos", "timor-leste",
        "united states", "usa", "united kingdom", "uk", "china", "japan",
        "south korea", "north korea", "australia", "india", "pakistan",
        "bangladesh", "germany", "france", "italy", "spain", "netherlands",
        "russia", "brazil", "canada", "mexico", "saudi arabia", "uae",
        "israel", "turkey", "egypt", "south africa", "nigeria", "kenya",
        "new zealand", "ireland", "sweden", "norway", "denmark", "finland",
        "switzerland", "austria", "belgium", "portugal", "poland", "ukraine",
        "argentina", "chile", "colombia", "peru", "venezuela", "cuba"
    )
}
```

---

## Phase 6 — Entity Media Resolver

The orchestrator. Picks the right API based on entity type, applies quality validation, and returns a typed `EntityMedia` result.

```kotlin
// domain/usecase/EntityMediaResolver.kt

class EntityMediaResolver @Inject constructor(
    private val wikiClient      : WikipediaMediaClient,
    private val countriesClient : RestCountriesClient
) {
    companion object {
        private const val RESOLVER_TIMEOUT_MS = 5_000L

        // Fast-path ISO code lookup — avoids REST Countries API call
        // for the most common countries encountered in Malaysian fact-checking
        private val COUNTRY_ISO_MAP = mapOf(
            "malaysia"        to "MY",
            "singapore"       to "SG",
            "indonesia"       to "ID",
            "thailand"        to "TH",
            "philippines"     to "PH",
            "brunei"          to "BN",
            "myanmar"         to "MM",
            "vietnam"         to "VN",
            "cambodia"        to "KH",
            "laos"            to "LA",
            "timor-leste"     to "TL",
            "united states"   to "US",
            "usa"             to "US",
            "united kingdom"  to "GB",
            "uk"              to "GB",
            "china"           to "CN",
            "japan"           to "JP",
            "south korea"     to "KR",
            "north korea"     to "KP",
            "australia"       to "AU",
            "india"           to "IN",
            "pakistan"        to "PK",
            "germany"         to "DE",
            "france"          to "FR",
            "italy"           to "IT",
            "spain"           to "ES",
            "netherlands"     to "NL",
            "russia"          to "RU",
            "brazil"          to "BR",
            "canada"          to "CA",
            "saudi arabia"    to "SA",
            "uae"             to "AE",
            "turkey"          to "TR",
            "egypt"           to "EG",
            "south africa"    to "ZA",
            "new zealand"     to "NZ",
            "sweden"          to "SE",
            "norway"          to "NO",
            "denmark"         to "DK",
            "switzerland"     to "CH",
            "israel"          to "IL",
            "ireland"         to "IE"
        )
    }

    suspend fun resolve(
        entityName : String,
        entityType : EntityType,
        claimType  : ClaimType
    ): EntityMedia {

        // Skip media for claim types where visuals add no value or risk confusion
        if (claimType == ClaimType.STATISTICAL || claimType == ClaimType.SCIENTIFIC) {
            return EntityMedia.Skipped
        }

        if (entityType == EntityType.UNKNOWN || entityType == EntityType.CONCEPT) {
            return EntityMedia.Skipped
        }

        return try {
            withTimeout(RESOLVER_TIMEOUT_MS) {
                when (entityType) {
                    EntityType.PERSON       -> resolvePersonMedia(entityName)
                    EntityType.COUNTRY      -> resolveCountryMedia(entityName)
                    EntityType.ORGANISATION -> resolveOrgMedia(entityName)
                    EntityType.PLACE        -> resolvePlaceMedia(entityName)
                    else                    -> EntityMedia.Skipped
                }
            }
        } catch (e: TimeoutCancellationException) {
            EntityMedia.NotFound  // Timeout is silent — pipeline is not blocked
        } catch (e: Exception) {
            EntityMedia.NotFound
        }
    }

    // ── Person ──────────────────────────────────────────────────────────
    private suspend fun resolvePersonMedia(name: String): EntityMedia {
        val summary = wikiClient.fetchPersonSummary(name)
            ?: return EntityMedia.NotFound

        val thumbnail = summary.thumbnail ?: return EntityMedia.NotFound

        // Reject probable group photos — portrait aspect ratio is near 1:1
        // Group photos or landscape images should not be shown as a person portrait
        val aspectRatio = thumbnail.width.toFloat() / thumbnail.height
        if (aspectRatio > 1.5f) return EntityMedia.NotFound

        // Reject very small images — likely icons or placeholders
        if (thumbnail.width < 60 || thumbnail.height < 60) return EntityMedia.NotFound

        val confidence = when {
            summary.type == "standard" &&
            summary.description?.isNotBlank() == true -> MediaConfidence.HIGH
            summary.type == "standard"                -> MediaConfidence.MEDIUM
            else                                      -> MediaConfidence.LOW
        }

        return EntityMedia.Portrait(
            imageUrl   = thumbnail.source,
            altText    = name,
            sourceUrl  = summary.contentUrls?.desktop?.page ?: "",
            confidence = confidence
        )
    }

    // ── Country ─────────────────────────────────────────────────────────
    private suspend fun resolveCountryMedia(name: String): EntityMedia {
        val normalised = name.lowercase().trim()

        // Fast path: ISO code in local map → FlagCDN directly, zero API calls
        COUNTRY_ISO_MAP[normalised]?.let { isoCode ->
            return EntityMedia.CountryFlag(
                svgUrl      = RestCountriesClient.flagSvgUrl(isoCode),
                pngUrl      = RestCountriesClient.flagPngUrl(isoCode),
                isoCode     = isoCode,
                countryName = name.replaceFirstChar { it.uppercase() },
                capital     = null,
                population  = null
            )
        }

        // Fallback: REST Countries API for countries not in local map
        val country = countriesClient.fetchByName(name) ?: return EntityMedia.NotFound

        return EntityMedia.CountryFlag(
            svgUrl      = RestCountriesClient.flagSvgUrl(country.cca2),
            pngUrl      = RestCountriesClient.flagPngUrl(country.cca2),
            isoCode     = country.cca2,
            countryName = country.name.common,
            capital     = country.capital?.firstOrNull(),
            population  = country.population
        )
    }

    // ── Organisation ─────────────────────────────────────────────────────
    private suspend fun resolveOrgMedia(name: String): EntityMedia {
        // Fast path: known org domain → Clearbit URL (zero API calls)
        ClearbitLogoClient.guessOrgDomain(name)?.let { domain ->
            return EntityMedia.OrgLogo(
                imageUrl   = ClearbitLogoClient.logoUrl(domain),
                orgName    = name,
                domain     = domain,
                sourceUrl  = "https://$domain",
                confidence = MediaConfidence.HIGH
            )
        }

        // Fallback: Wikipedia for orgs not in known domain map
        val summary = wikiClient.fetchSummary(
            name.trim().replace(" ", "_")
        ) ?: return EntityMedia.NotFound

        val thumbnail = summary.thumbnail ?: return EntityMedia.NotFound

        return EntityMedia.OrgLogo(
            imageUrl   = thumbnail.source,
            orgName    = name,
            domain     = null,
            sourceUrl  = summary.contentUrls?.desktop?.page ?: "",
            confidence = MediaConfidence.MEDIUM
        )
    }

    // ── Place ────────────────────────────────────────────────────────────
    private suspend fun resolvePlaceMedia(name: String): EntityMedia {
        val summary = wikiClient.fetchSummary(
            name.trim().replace(" ", "_")
        ) ?: return EntityMedia.NotFound

        val thumbnail = summary.thumbnail ?: return EntityMedia.NotFound

        // Reject images that are clearly maps (usually landscape and very wide)
        val aspectRatio = thumbnail.width.toFloat() / thumbnail.height
        if (aspectRatio > 2.0f) return EntityMedia.NotFound

        return EntityMedia.PlacePhoto(
            imageUrl    = thumbnail.source,
            placeName   = name,
            description = summary.description,
            sourceUrl   = summary.contentUrls?.desktop?.page ?: ""
        )
    }
}
```

---

## Phase 7 — Room Persistence

Extend the existing `EntityContextEntity` with media cache columns:

```kotlin
// data/local/entity/EntityContextEntity.kt — updated

@Entity(tableName = "entity_context_cache")
data class EntityContextEntity(
    @PrimaryKey
    val entityKey   : String,      // "person:anwar_ibrahim", "country:MY", "org:petronas"
    val entityName  : String,
    val description : String?,
    val wikiExtract : String?,
    val wikiUrl     : String?,
    val entityType  : String,
    val mediaType   : String?,     // "portrait" | "flag" | "logo" | "place" | "none" | "skipped"
    val mediaJson   : String?,     // JSON-serialised EntityMedia subtype
    val cachedAt    : Long
)

// Room migration
val MIGRATION_X_TO_Y = object : Migration(X, Y) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE entity_context_cache ADD COLUMN mediaType TEXT")
        db.execSQL("ALTER TABLE entity_context_cache ADD COLUMN mediaJson TEXT")
    }
}

// Cache TTL by media type — flags never change, people's roles do
fun EntityContextEntity.isExpired(): Boolean {
    val ttlMs: Long = when (mediaType) {
        "flag"      -> 30L * 24 * 60 * 60 * 1000   // 30 days
        "logo"      -> 14L * 24 * 60 * 60 * 1000   // 14 days
        "portrait"  -> 7L  * 24 * 60 * 60 * 1000   // 7 days
        "place"     -> 14L * 24 * 60 * 60 * 1000   // 14 days
        else        -> 7L  * 24 * 60 * 60 * 1000   // 7 days default
    }
    return (System.currentTimeMillis() - cachedAt) > ttlMs
}

// Build the entity cache key deterministically
fun buildEntityKey(entityType: EntityType, entityName: String): String {
    val typePart = entityType.name.lowercase()
    val namePart = entityName.lowercase().trim().replace(" ", "_")
    return "$typePart:$namePart"
}
```

---

## Phase 8 — Pipeline Integration

Wire the resolver into the parallel enrichment block in `CorvusFactCheckUseCase`:

```kotlin
// In CorvusFactCheckUseCase.check() — extend the parallel enrichment coroutine

val kgAndMediaDeferred = async {
    runCatching {
        // Existing KG lookup
        val entityContext = kgEnricher.enrich(raw, classified, tokenCollector)

        if (entityContext != null) {
            // Attempt to resolve media for the primary entity
            // 5s hard timeout is enforced inside EntityMediaResolver
            val media = entityMediaResolver.resolve(
                entityName = entityContext.entityName,
                entityType = entityContext.entityType,
                claimType  = classified.type
            )
            entityContext.copy(media = media)
        } else {
            null
        }
    }.getOrNull()
}
```

---

## Phase 9 — Coil Dependency

Coil for async image loading in Compose:

```kotlin
// app/build.gradle.kts — add if not already present
implementation("io.coil-kt:coil-compose:2.6.0")
```

No new `uses-permission` entries needed. Wikipedia, FlagCDN, Clearbit, and REST Countries are all HTTPS and covered by the existing `android.permission.INTERNET` in the manifest.

---

## Phase 10 — UI Components

### EntityMediaPanel — Top-Level Router

```kotlin
// ui/result/components/EntityMediaPanel.kt

@Composable
fun EntityMediaPanel(
    context  : EntityContext,
    modifier : Modifier = Modifier
) {
    when (val media = context.media) {
        is EntityMedia.Portrait    -> PersonMediaCard(context, media, modifier)
        is EntityMedia.CountryFlag -> CountryMediaCard(context, media, modifier)
        is EntityMedia.OrgLogo     -> OrgMediaCard(context, media, modifier)
        is EntityMedia.PlacePhoto  -> PlaceMediaCard(context, media, modifier)
        // Text-only fallback — same display as before this feature existed
        is EntityMedia.NotFound,
        is EntityMedia.Skipped     -> EntityTextOnlyPanel(context, modifier)
    }
}
```

### PersonMediaCard

```kotlin
@Composable
fun PersonMediaCard(
    context  : EntityContext,
    portrait : EntityMedia.Portrait,
    modifier : Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        border   = BorderStroke(1.dp, CorvusTheme.colors.border),
        shape    = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier              = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment     = Alignment.Top
        ) {

            // Portrait with confidence overlay
            Box(modifier = Modifier.size(72.dp)) {
                AsyncImage(
                    model              = portrait.imageUrl,
                    contentDescription = portrait.altText,
                    modifier           = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale       = ContentScale.Crop,
                    placeholder        = painterResource(R.drawable.ic_person_placeholder),
                    error              = painterResource(R.drawable.ic_person_placeholder)
                )
                // LOW confidence warning overlay — image may not be the right person
                if (portrait.confidence == MediaConfidence.LOW) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp),
                        color    = CorvusTheme.colors.verdictMisleading.copy(alpha = 0.9f),
                        shape    = RoundedCornerShape(2.dp)
                    ) {
                        Text(
                            "?",
                            modifier  = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style     = CorvusTheme.typography.caption,
                            color     = CorvusTheme.colors.void_,
                            fontSize  = 9.sp
                        )
                    }
                }
            }

            Column(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        context.entityName,
                        style      = CorvusTheme.typography.labelSmall,
                        color      = CorvusTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    EntityTypeBadge(EntityType.PERSON)
                }
                context.description?.let {
                    Text(
                        it,
                        style      = CorvusTheme.typography.caption,
                        color      = CorvusTheme.colors.accent,
                        fontFamily = IbmPlexMono
                    )
                }
                context.wikiExtract?.let {
                    Text(
                        text     = it.take(180).trimEnd().let { s ->
                            if (it.length > 180) "$s…" else s
                        },
                        style    = CorvusTheme.typography.caption,
                        color    = CorvusTheme.colors.textSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                WikiAttributionLink(portrait.sourceUrl)
            }
        }
    }
}
```

### CountryMediaCard

```kotlin
@Composable
fun CountryMediaCard(
    context : EntityContext,
    flag    : EntityMedia.CountryFlag,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        border   = BorderStroke(1.dp, CorvusTheme.colors.border),
        shape    = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier              = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {

            // Flag — 3:2 aspect ratio
            AsyncImage(
                model              = flag.pngUrl,
                contentDescription = "${flag.countryName} flag",
                modifier           = Modifier
                    .width(80.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .border(1.dp, CorvusTheme.colors.border, RoundedCornerShape(3.dp)),
                contentScale       = ContentScale.Crop
            )

            Column(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        flag.countryName,
                        style      = CorvusTheme.typography.labelSmall,
                        color      = CorvusTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    // ISO code chip
                    Surface(
                        color  = CorvusTheme.colors.border,
                        shape  = RoundedCornerShape(2.dp)
                    ) {
                        Text(
                            flag.isoCode,
                            modifier  = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            style     = CorvusTheme.typography.caption,
                            fontSize  = 8.sp,
                            fontFamily = IbmPlexMono,
                            color     = CorvusTheme.colors.textSecondary
                        )
                    }
                }

                // Capital and population if available
                flag.capital?.let {
                    Text(
                        "Capital: $it",
                        style     = CorvusTheme.typography.caption,
                        color     = CorvusTheme.colors.textSecondary,
                        fontFamily = IbmPlexMono
                    )
                }
                flag.population?.let {
                    Text(
                        "Population: ${it.formatPopulation()}",
                        style     = CorvusTheme.typography.caption,
                        color     = CorvusTheme.colors.textTertiary,
                        fontFamily = IbmPlexMono
                    )
                }

                // Wikipedia extract if available
                context.wikiExtract?.let {
                    Text(
                        text     = it.take(140).let { s -> if (it.length > 140) "$s…" else s },
                        style    = CorvusTheme.typography.caption,
                        color    = CorvusTheme.colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
```

### OrgMediaCard

```kotlin
@Composable
fun OrgMediaCard(
    context : EntityContext,
    logo    : EntityMedia.OrgLogo,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        border   = BorderStroke(1.dp, CorvusTheme.colors.border),
        shape    = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier              = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {

            // Logo — white background (logos expect light bg)
            Surface(
                modifier = Modifier.size(56.dp),
                color    = Color.White,
                shape    = RoundedCornerShape(6.dp),
                border   = BorderStroke(1.dp, CorvusTheme.colors.border)
            ) {
                AsyncImage(
                    model              = logo.imageUrl,
                    contentDescription = "${logo.orgName} logo",
                    modifier           = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentScale       = ContentScale.Fit,
                    error              = painterResource(R.drawable.ic_org_placeholder)
                )
            }

            Column(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        logo.orgName,
                        style      = CorvusTheme.typography.labelSmall,
                        color      = CorvusTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    EntityTypeBadge(EntityType.ORGANISATION)
                }
                context.description?.let {
                    Text(
                        it,
                        style     = CorvusTheme.typography.caption,
                        color     = CorvusTheme.colors.accent,
                        fontFamily = IbmPlexMono
                    )
                }
                context.wikiExtract?.let {
                    Text(
                        text     = it.take(160).let { s -> if (it.length > 160) "$s…" else s },
                        style    = CorvusTheme.typography.caption,
                        color    = CorvusTheme.colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                WikiAttributionLink(logo.sourceUrl)
            }
        }
    }
}
```

### Shared Utility Composables

```kotlin
// ── Wikipedia attribution link ───────────────────────────────────────
@Composable
fun WikiAttributionLink(url: String) {
    if (url.isBlank()) return
    val uriHandler = LocalUriHandler.current
    Row(
        modifier              = Modifier.clickable { uriHandler.openUri(url) },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Icon(
            CorvusIcons.OpenExternal,
            modifier = Modifier.size(9.dp),
            tint     = CorvusTheme.colors.textTertiary
        )
        Text(
            "Wikipedia",
            style      = CorvusTheme.typography.caption,
            color      = CorvusTheme.colors.textTertiary,
            fontFamily = IbmPlexMono,
            fontSize   = 8.sp
        )
    }
}

// ── Entity type badge ────────────────────────────────────────────────
@Composable
fun EntityTypeBadge(type: EntityType) {
    val (label, color) = when (type) {
        EntityType.PERSON       -> "PERSON"  to CorvusTheme.colors.partial
        EntityType.COUNTRY      -> "COUNTRY" to CorvusTheme.colors.verdictTrue
        EntityType.ORGANISATION -> "ORG"     to CorvusTheme.colors.verdictMisleading
        EntityType.PLACE        -> "PLACE"   to CorvusTheme.colors.accent
        else                    -> return
    }
    Surface(
        color  = color.copy(alpha = 0.10f),
        shape  = RoundedCornerShape(1.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.30f))
    ) {
        Text(
            label,
            modifier      = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            style         = CorvusTheme.typography.caption,
            fontSize      = 7.5.sp,
            color         = color,
            fontFamily    = IbmPlexMono,
            letterSpacing = 0.5.sp
        )
    }
}

// ── Population formatter ─────────────────────────────────────────────
fun Long.formatPopulation(): String = when {
    this >= 1_000_000_000L -> "${this / 1_000_000_000}B"
    this >= 1_000_000L     -> "${this / 1_000_000}M"
    this >= 1_000L         -> "${this / 1_000}K"
    else                   -> this.toString()
}
```

---

## Phase 11 — Hilt Module

```kotlin
// di/MediaModule.kt

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    fun provideWikipediaMediaClient(
        httpClient: HttpClient
    ): WikipediaMediaClient = WikipediaMediaClient(httpClient)

    @Provides
    @Singleton
    fun provideRestCountriesClient(
        httpClient: HttpClient
    ): RestCountriesClient = RestCountriesClient(httpClient)

    @Provides
    @Singleton
    fun provideEntityMediaResolver(
        wikiClient      : WikipediaMediaClient,
        countriesClient : RestCountriesClient
    ): EntityMediaResolver = EntityMediaResolver(wikiClient, countriesClient)
}
```

---

## Implementation Tasks

### Data Models
- [ ] Define `EntityMedia` sealed class — Portrait, CountryFlag, OrgLogo, PlacePhoto, NotFound, Skipped
- [ ] Define `MediaConfidence` enum — HIGH, MEDIUM, LOW
- [ ] Add `media: EntityMedia` field to `EntityContext` data class (default: `EntityMedia.Skipped`)
- [ ] Add `entityType: EntityType` to `EntityContext` if not already present

### Network Clients
- [ ] Implement `WikipediaMediaClient` with `fetchSummary()` and `fetchPersonSummary()`
- [ ] Implement Wikipedia disambiguation retry with Malaysian qualifier list
- [ ] Implement `WikipediaSummary`, `WikiThumbnail`, `WikiContentUrls`, `WikiUrlPair` serializable models
- [ ] Implement `RestCountriesClient` with `fetchByName()` and `fetchByIso()`
- [ ] Implement `RestCountry`, `RestCountryName`, `RestCountryFlags` serializable models
- [ ] Implement `ClearbitLogoClient` — `logoUrl()` and `guessOrgDomain()` with `KNOWN_ORG_DOMAINS` map
- [ ] Add Malaysian organisations to `KNOWN_ORG_DOMAINS` map

### Resolver
- [ ] Implement `EntityTypeDetector.detect()` with KG type, country, org suffix, place, and person heuristics
- [ ] Implement `EntityMediaResolver.resolve()` — dispatches by entity type, wraps in `withTimeout(5_000L)`
- [ ] Implement `resolvePersonMedia()` with aspect ratio (>1.5f rejected) and minimum size (60×60) validation
- [ ] Implement `resolveCountryMedia()` — ISO fast path, REST Countries fallback
- [ ] Implement `resolveOrgMedia()` — Clearbit fast path, Wikipedia fallback
- [ ] Implement `resolvePlaceMedia()` — Wikipedia only, aspect ratio >2.0f rejected
- [ ] Build `COUNTRY_ISO_MAP` for common countries

### Persistence
- [ ] Add `mediaType` and `mediaJson` columns to `entity_context_cache` Room table
- [ ] Write Room migration for new columns
- [ ] Implement `EntityContextEntity.isExpired()` with per-media-type TTL
- [ ] Implement `buildEntityKey()` utility
- [ ] Update `EntityContextRepository` to serialise/deserialise `EntityMedia` to/from `mediaJson`
- [ ] Update repository to return cached media and check TTL before re-fetching

### Pipeline
- [ ] Wire `EntityMediaResolver` into `CorvusFactCheckUseCase` parallel enrichment block
- [ ] Pass `EntityTypeDetector.detect()` result into `entityMediaResolver.resolve()`
- [ ] Add `io.coil-kt:coil-compose:2.6.0` to `app/build.gradle.kts`

### UI
- [ ] Implement `EntityMediaPanel` composable — routes to correct card via `when`
- [ ] Implement `PersonMediaCard` with portrait, confidence overlay, attribution
- [ ] Implement `CountryMediaCard` with flag, ISO chip, capital, population
- [ ] Implement `OrgMediaCard` with white-bg logo surface
- [ ] Implement `PlaceMediaCard` with photo and description
- [ ] Implement `EntityTextOnlyPanel` text-only fallback for NotFound/Skipped
- [ ] Implement `WikiAttributionLink` composable
- [ ] Implement `EntityTypeBadge` composable with per-type colours
- [ ] Implement `Long.formatPopulation()` extension
- [ ] Add `ic_person_placeholder` drawable (48dp, neutral icon)
- [ ] Add `ic_org_placeholder` drawable (48dp, neutral building icon)
- [ ] Replace existing `EntityContextPanel` with `EntityMediaPanel` in result screen `LazyColumn`

### Hilt
- [ ] Create `MediaModule.kt` in `di/`
- [ ] Register `WikipediaMediaClient` as Singleton
- [ ] Register `RestCountriesClient` as Singleton
- [ ] Register `EntityMediaResolver` as Singleton

### Tests
- [ ] Unit test: `resolveCountryMedia("malaysia")` → `CountryFlag(isoCode = "MY")`, no API call made
- [ ] Unit test: `resolveCountryMedia("Wakanda")` → `NotFound`
- [ ] Unit test: portrait `aspectRatio = 1.8f` → `NotFound` (group photo rejection)
- [ ] Unit test: portrait `width = 40` → `NotFound` (too small)
- [ ] Unit test: `resolveOrgMedia("Petronas")` → `OrgLogo` with Clearbit URL, no API call
- [ ] Unit test: `EntityTypeDetector.detect("Petronas Berhad", null)` → `ORGANISATION`
- [ ] Unit test: `EntityTypeDetector.detect("Malaysia", null)` → `COUNTRY`
- [ ] Unit test: `EntityTypeDetector.detect("Anwar Ibrahim", "Person")` → `PERSON`
- [ ] Unit test: `Long.formatPopulation()` — 33_573_874L → "33M", 1_400_000_000L → "1B"
- [ ] Integration test: Room cache hit returns `EntityMedia` without firing API call
- [ ] Integration test: expired cache (>7 days for portrait) triggers fresh Wikipedia fetch
- [ ] Integration test: Wikipedia timeout (>5s) → `EntityMedia.NotFound`, pipeline not blocked
- [ ] UI test: `CountryMediaCard` renders with FlagCDN PNG URL for "MY"
- [ ] UI test: `PersonMediaCard` shows amber `?` overlay when `confidence == LOW`
- [ ] UI test: `EntityTextOnlyPanel` renders when `media = EntityMedia.NotFound`
- [ ] UI test: `EntityTypeBadge(COUNTRY)` renders in green colour

**Estimated duration: 7 days**

---

## New Dependencies

```kotlin
// app/build.gradle.kts
implementation("io.coil-kt:coil-compose:2.6.0")
// No other new dependencies — Wikipedia, FlagCDN, REST Countries, Clearbit are keyless HTTP
```

No new entries in `local.properties`, `gradle.properties`, or `local.properties.example` required.

---

## New API Keys Required

None.

| Service | Authentication | Notes |
|---|---|---|
| Wikipedia REST API | None | Requires descriptive `User-Agent` header only |
| REST Countries API | None | Open source project |
| FlagCDN | None | CDN-served SVG/PNG flags |
| Clearbit Logo API | None | ~600 implicit monthly requests on free tier |

---

## Definition of Done

- `EntityMedia` sealed class covers all 6 states and compiles without warnings
- `resolveCountryMedia("malaysia")` returns `CountryFlag` with `isoCode = "MY"` using FlagCDN with zero API calls
- `resolvePersonMedia("Anwar Ibrahim")` returns `Portrait` with Wikipedia thumbnail for the Prime Minister, not a disambiguation page
- Portrait aspect ratio > 1.5f returns `NotFound` — no group photos shown as individual portraits
- `resolveOrgMedia("Petronas")` returns `OrgLogo` with Clearbit URL via `KNOWN_ORG_DOMAINS` map with zero API calls
- Wikipedia timeout (simulated at >5s) returns `EntityMedia.NotFound` silently — pipeline verdict is never delayed
- `PersonMediaCard` renders portrait at 72dp × 72dp with `ContentScale.Crop`
- `CountryMediaCard` renders flag at 80dp × 54dp (3:2 ratio)
- `OrgMediaCard` renders logo on white `Surface` background at 56dp × 56dp
- `WikiAttributionLink` visible on all Wikipedia-sourced media cards
- LOW confidence portrait shows amber `?` overlay at bottom-right corner
- Room cache hit for entity checked in last 7 days (portrait) / 30 days (flag) returns without API call
- `EntityTextOnlyPanel` renders correctly when `media = NotFound` or `Skipped`
- All entity type badge colours match Corvus colour tokens (PERSON=partial blue, COUNTRY=true green, ORG=misleading amber, PLACE=accent chartreuse)
- STATISTICAL and SCIENTIFIC claims always return `EntityMedia.Skipped` — no media shown
