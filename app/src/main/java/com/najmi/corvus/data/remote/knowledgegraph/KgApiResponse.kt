package com.najmi.corvus.data.remote.knowledgegraph

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
