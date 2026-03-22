package com.najmi.corvus.domain.usecase

import com.najmi.corvus.data.local.entity.KgCacheDao
import com.najmi.corvus.data.local.entity.KgCacheEntity
import com.najmi.corvus.data.remote.knowledgegraph.KnowledgeGraphClient
import com.najmi.corvus.data.remote.knowledgegraph.KgEntityMapper
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.ClassifiedClaim
import com.najmi.corvus.domain.model.EntityContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

class KgEnricherUseCase @Inject constructor(
    private val entityExtractor: EntityExtractorUseCase,
    private val kgClient: KnowledgeGraphClient,
    private val kgCacheDao: KgCacheDao,
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

        // 1. Check cache first
        try {
            val cached = kgCacheDao.get(cacheKey)
            if (cached != null) {
                val age = System.currentTimeMillis() - cached.cachedAt
                if (age < 7.days.inWholeMilliseconds) {
                    return json.decodeFromString<EntityContext>(cached.entityJson)
                }
            }
        } catch (e: Exception) {
            // Cache error shouldn't block
        }

        // 2. Query Knowledge Graph
        val kgItem = if (classified.type == ClaimType.PERSON_FACT) {
            kgClient.searchEntityByName(entity.name, types = listOf("Person"))
        } else {
            kgClient.searchEntity(entity.name)
        } ?: return null

        // 3. Map to domain model
        val requiresFreshnessWarning =
            entity.claimInvolveCurrentStatus &&
            kgItem.result.types.any { it == "Person" }

        val entityContext = KgEntityMapper.map(kgItem, requiresFreshnessWarning)

        // 4. Persist to cache
        try {
            kgCacheDao.insert(
                KgCacheEntity(
                    queryKey   = cacheKey,
                    entityJson = json.encodeToString(entityContext)
                )
            )
        } catch (e: Exception) {
            // Cache persistence failure shouldn't block
        }

        return entityContext
    }
}
