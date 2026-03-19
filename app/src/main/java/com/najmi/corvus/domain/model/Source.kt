package com.najmi.corvus.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Source(
    val title: String,
    val url: String,
    val publisher: String? = null,
    val snippet: String? = null
)
