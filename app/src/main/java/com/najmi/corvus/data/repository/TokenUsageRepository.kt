package com.najmi.corvus.data.repository

import com.najmi.corvus.data.local.TokenReportDao
import com.najmi.corvus.data.local.entity.TokenReportEntity
import com.najmi.corvus.domain.model.CheckTokenReport
import com.najmi.corvus.domain.model.TokenUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenUsageRepository @Inject constructor(
    private val tokenReportDao: TokenReportDao,
    private val json: Json
) {
    suspend fun saveReport(checkId: String, report: CheckTokenReport) {
        val entity = TokenReportEntity(
            checkId = checkId,
            totalPromptTokens = report.totalPrompt,
            totalCompletionTokens = report.totalCompletion,
            totalCombinedTokens = report.totalCombined,
            breakdownJson = json.encodeToString(report.breakdown)
        )
        tokenReportDao.insertReport(entity)
    }

    fun getAllReports(): Flow<List<CheckTokenReport>> {
        return tokenReportDao.getAllReports().map { entities ->
            entities.map { entity ->
                CheckTokenReport(
                    totalPrompt = entity.totalPromptTokens,
                    totalCompletion = entity.totalCompletionTokens,
                    totalCombined = entity.totalCombinedTokens,
                    breakdown = try {
                        json.decodeFromString<List<TokenUsage>>(entity.breakdownJson)
                    } catch (e: Exception) {
                        emptyList()
                    }
                )
            }
        }
    }

    fun getTotalTokens(): Flow<Int> {
        return tokenReportDao.getTotalTokensFlow().map { it ?: 0 }
    }
}
