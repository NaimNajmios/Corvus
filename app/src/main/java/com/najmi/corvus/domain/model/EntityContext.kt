package com.najmi.corvus.domain.model

import kotlinx.serialization.Serializable

@Serializable
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
    val requiresFreshnessWarning: Boolean = false,
    val media: EntityMedia = EntityMedia.Skipped,
    val mediaEntityType: MediaEntityType = MediaEntityType.UNKNOWN
)

@Serializable
enum class MediaEntityType {
    PERSON,
    COUNTRY,
    ORGANISATION,
    PLACE,
    UNKNOWN
}

@Serializable
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
