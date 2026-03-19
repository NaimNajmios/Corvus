package com.najmi.corvus.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "corvus_history")
data class CorvusHistoryEntity(
    @PrimaryKey
    val id: String,
    val claim: String,
    val verdict: String,
    val confidence: Float,
    val explanation: String,
    val sourcesJson: String,
    val providerUsed: String,
    val language: String,
    val checkedAt: Long,
    val isFromKnownFactCheck: Boolean
)