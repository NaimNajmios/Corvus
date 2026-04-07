package com.najmi.corvus.domain.usecase

import com.najmi.corvus.data.remote.media.ClearbitLogoClient
import com.najmi.corvus.data.remote.media.RestCountriesClient
import com.najmi.corvus.data.remote.media.WikipediaMediaClient
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.EntityMedia
import com.najmi.corvus.domain.model.MediaConfidence
import com.najmi.corvus.domain.model.MediaEntityType
import com.najmi.corvus.domain.util.EntityTypeDetector
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntityMediaResolver @Inject constructor(
    private val wikiClient      : WikipediaMediaClient,
    private val countriesClient : RestCountriesClient
) {
    companion object {
        private const val RESOLVER_TIMEOUT_MS = 5_000L

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
            "ireland"         to "IE",
            "iran"            to "IR",
            "iraq"            to "IQ",
            "qatar"           to "QA",
            "kuwait"          to "KW",
            "bahrain"         to "BH",
            "oman"            to "OM",
            "jordan"          to "JO",
            "lebanon"         to "LB",
            "syria"           to "SY",
            "yemen"           to "YE",
            "afghanistan"     to "AF",
            "taiwan"          to "TW",
            "hong kong"       to "HK",
            "bangladesh"      to "BD",
            "sri lanka"       to "LK",
            "nepal"           to "NP",
            "bhutan"          to "BT",
            "maldives"        to "MV",
            "mongolia"        to "MN",
            "mexico"          to "MX",
            "argentina"       to "AR",
            "chile"           to "CL",
            "colombia"        to "CO",
            "peru"            to "PE",
            "venezuela"       to "VE",
            "poland"          to "PL",
            "ukraine"         to "UA",
            "austria"         to "AT",
            "belgium"         to "BE",
            "portugal"        to "PT",
            "finland"         to "FI",
            "norway"          to "NO",
            "denmark"         to "DK",
            "nigeria"         to "NG",
            "kenya"           to "KE",
            "israel"          to "IL"
        )
    }

    suspend fun resolve(
        entityName   : String,
        entityTypes  : List<com.najmi.corvus.domain.model.EntityType>,
        claimType    : ClaimType
    ): EntityMedia {
        val entityType = EntityTypeDetector.detect(entityName, entityTypes)

        if (claimType == ClaimType.STATISTICAL || claimType == ClaimType.SCIENTIFIC) {
            return EntityMedia.Skipped
        }

        if (entityType == MediaEntityType.UNKNOWN) {
            return EntityMedia.Skipped
        }

        return try {
            withTimeout(RESOLVER_TIMEOUT_MS) {
                when (entityType) {
                    MediaEntityType.PERSON       -> resolvePersonMedia(entityName)
                    MediaEntityType.COUNTRY      -> resolveCountryMedia(entityName)
                    MediaEntityType.ORGANISATION -> resolveOrgMedia(entityName)
                    MediaEntityType.PLACE        -> resolvePlaceMedia(entityName)
                    MediaEntityType.UNKNOWN      -> EntityMedia.Skipped
                }
            }
        } catch (e: TimeoutCancellationException) {
            EntityMedia.NotFound
        } catch (e: Exception) {
            EntityMedia.NotFound
        }
    }

    private suspend fun resolvePersonMedia(name: String): EntityMedia {
        val summary = wikiClient.fetchPersonSummary(name)
            ?: return EntityMedia.NotFound

        val thumbnail = summary.thumbnail ?: return EntityMedia.NotFound

        val aspectRatio = thumbnail.width.toFloat() / thumbnail.height
        if (aspectRatio > 1.5f) return EntityMedia.NotFound

        if (thumbnail.width < 60 || thumbnail.height < 60) return EntityMedia.NotFound

        val confidence = when {
            summary.type == "standard" &&
            summary.description?.isNotBlank() == true -> MediaConfidence.HIGH
            summary.type == "standard"                -> MediaConfidence.MEDIUM
            else                                       -> MediaConfidence.LOW
        }

        return EntityMedia.Portrait(
            imageUrl   = thumbnail.source,
            altText    = name,
            sourceUrl  = summary.contentUrls?.desktop?.page ?: "",
            confidence = confidence
        )
    }

    private suspend fun resolveCountryMedia(name: String): EntityMedia {
        val normalised = name.lowercase().trim()

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

    private suspend fun resolveOrgMedia(name: String): EntityMedia {
        ClearbitLogoClient.guessOrgDomain(name)?.let { domain ->
            return EntityMedia.OrgLogo(
                imageUrl   = ClearbitLogoClient.logoUrl(domain),
                orgName    = name,
                domain     = domain,
                sourceUrl  = "https://$domain",
                confidence = MediaConfidence.HIGH
            )
        }

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

    private suspend fun resolvePlaceMedia(name: String): EntityMedia {
        val summary = wikiClient.fetchSummary(
            name.trim().replace(" ", "_")
        ) ?: return EntityMedia.NotFound

        val thumbnail = summary.thumbnail ?: return EntityMedia.NotFound

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
