package com.najmi.corvus.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "corvus_history")
data class CorvusHistoryEntity(
    @PrimaryKey
    val id: String,
    val claim: String,
    val resultType: String, // "GENERAL" or "QUOTE"
    val verdict: String,
    val confidence: Float,
    val dataJson: String,   // Full serialized CorvusCheckResult
    val checkedAt: Long,
    val harmLevel: String = "NONE",
    val harmCategory: String = "NONE",
    val plausibilityScore: String? = null
)