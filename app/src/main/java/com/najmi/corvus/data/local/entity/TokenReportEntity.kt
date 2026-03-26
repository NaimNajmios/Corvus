package com.najmi.corvus.data.local.entity

import androidx.room.*
import com.najmi.corvus.domain.model.TokenUsage

@Entity(tableName = "token_reports")
data class TokenReportEntity(
    @PrimaryKey val checkId: String,
    val totalPromptTokens: Int,
    val totalCompletionTokens: Int,
    val totalCombinedTokens: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val breakdownJson: String // Serialized List<TokenUsage>
)
