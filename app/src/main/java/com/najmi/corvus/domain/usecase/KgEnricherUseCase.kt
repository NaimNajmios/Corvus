package com.najmi.corvus.domain.usecase

import com.najmi.corvus.data.local.entity.KgCacheDao
import com.najmi.corvus.data.local.entity.KgCacheEntity
import com.najmi.corvus.data.remote.knowledgegraph.KnowledgeGraphClient
import com.najmi.corvus.data.remote.knowledgegraph.KgEntityMapper
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.ClassifiedClaim
import com.najmi.corvus.domain.model.EntityContext
import com.najmi.corvus.domain.model.MediaEntityType
import com.najmi.corvus.domain.util.EntityTypeDetector
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

class KgEnricherUseCase @Inject constructor(
    private val entityExtractor: EntityExtractorUseCase,
    private val kgClient: KnowledgeGraphClient,
    private val kgCacheDao: KgCacheDao,
    private val entityMediaResolver: EntityMediaResolver,
    private val json: Json
) {
    private val SKIP_CLAIM_TYPES = setOf(
        ClaimType.STATISTICAL,
        ClaimType.SCIENTIFIC,
        ClaimType.CURRENT_EVENT
    )

    suspend fun enrich(
        claim: String,
        classified: ClassifiedClaim
    ): EntityContext? {
        if (classified.type in SKIP_CLAIM_TYPES) return null

        val entity = entityExtractor.extract(claim, classified) ?: return null
        val cacheKey = entity.name.lowercase().trim()

        try {
            val cached = kgCacheDao.get(cacheKey)
            if (cached != null) {
                val age = System.currentTimeMillis() - cached.cachedAt
                if (age < 7.days.inWholeMilliseconds) {
                    val cachedContext = json.decodeFromString<EntityContext>(cached.entityJson)
                    return cachedContext
                }
            }
        } catch (e: Exception) {
            // Cache error shouldn't block
        }

        val kgItem = if (classified.type == ClaimType.PERSON_FACT) {
            kgClient.searchEntityByName(entity.name, types = listOf("Person"))
        } else {
            kgClient.searchEntity(entity.name)
        } ?: return null

        val requiresFreshnessWarning =
            entity.claimInvolveCurrentStatus &&
            kgItem.result.types.any { it == "Person" }

        val entityContext = KgEntityMapper.map(kgItem, requiresFreshnessWarning)

        val mediaEntityType = EntityTypeDetector.detect(entityContext.name, entityContext.entityTypes)
        
        val media = entityMediaResolver.resolve(
            entityName  = entityContext.name,
            entityTypes = entityContext.entityTypes,
            claimType   = classified.type
        )

        val enrichedContext = entityContext.copy(
            media = media,
            mediaEntityType = mediaEntityType
        )

        try {
            val mediaTypeStr = when (enrichedContext.media) {
                is com.najmi.corvus.domain.model.EntityMedia.Portrait -> "portrait"
                is com.najmi.corvus.domain.model.EntityMedia.CountryFlag -> "flag"
                is com.najmi.corvus.domain.model.EntityMedia.OrgLogo -> "logo"
                is com.najmi.corvus.domain.model.EntityMedia.PlacePhoto -> "place"
                else -> "none"
            }
            kgCacheDao.insert(
                KgCacheEntity(
                    queryKey   = cacheKey,
                    entityJson = json.encodeToString(enrichedContext),
                    mediaType  = if (mediaTypeStr == "none") null else mediaTypeStr,
                    mediaJson  = if (mediaTypeStr == "none") null else json.encodeToString(enrichedContext.media)
                )
            )
        } catch (e: Exception) {
            // Cache persistence failure shouldn't block
        }

        return enrichedContext
    }
}
