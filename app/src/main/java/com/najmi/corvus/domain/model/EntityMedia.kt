package com.najmi.corvus.domain.model

import kotlinx.serialization.Serializable

@Serializable
sealed class EntityMedia {

    @Serializable
    data class Portrait(
        val imageUrl   : String,
        val altText    : String,
        val sourceUrl  : String,
        val confidence : MediaConfidence
    ) : EntityMedia()

    @Serializable
    data class CountryFlag(
        val svgUrl      : String,
        val pngUrl      : String,
        val isoCode     : String,
        val countryName : String,
        val capital     : String? = null,
        val population  : Long? = null
    ) : EntityMedia()

    @Serializable
    data class OrgLogo(
        val imageUrl   : String,
        val orgName    : String,
        val domain     : String? = null,
        val sourceUrl  : String,
        val confidence : MediaConfidence
    ) : EntityMedia()

    @Serializable
    data class PlacePhoto(
        val imageUrl    : String,
        val placeName   : String,
        val description : String? = null,
        val sourceUrl   : String
    ) : EntityMedia()

    @Serializable
    data object NotFound : EntityMedia()

    @Serializable
    data object Skipped : EntityMedia()
}

@Serializable
enum class MediaConfidence {
    HIGH,
    MEDIUM,
    LOW
}
