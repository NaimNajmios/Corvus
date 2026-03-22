package com.najmi.corvus.data.remote.knowledgegraph

import com.najmi.corvus.domain.model.EntityContext
import com.najmi.corvus.domain.model.EntityType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
            detailedSnippet        = if (cleanSnippet != null && cleanSnippet.length > 60) cleanSnippet else null,
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
