package com.najmi.corvus.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "viral_hoaxes")
data class ViralHoaxEntity(
    @PrimaryKey
    val id: String,
    val claim: String,
    val summary: String,
    val debunkUrls: String, // Comma-separated or JSON
    val firstSeen: String?,
    val searchedAt: Long = System.currentTimeMillis()
)
