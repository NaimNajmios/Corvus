package com.najmi.corvus.data.repository

import android.util.Log
import com.najmi.corvus.data.local.CorvusHistoryEntity
import com.najmi.corvus.data.local.HistoryDao
import com.najmi.corvus.domain.model.ClaimLanguage
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.Source
import com.najmi.corvus.domain.model.Verdict
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val historyDao: HistoryDao,
    private val json: Json
) {
    companion object {
        private const val TAG = "HistoryRepository"
    }

    fun getAllHistory(): Flow<List<CorvusCheckResult>> {
        return historyDao.getAllHistory().map { entities ->
            entities.mapNotNull { it.toCorvusResult() }
        }
    }

    fun searchHistory(query: String): Flow<List<CorvusCheckResult>> {
        return historyDao.searchHistory(query).map { entities ->
            entities.mapNotNull { it.toCorvusResult() }
        }
    }

    fun filterByVerdict(verdict: String): Flow<List<CorvusCheckResult>> {
        return historyDao.filterByVerdict(verdict).map { entities ->
            entities.mapNotNull { it.toCorvusResult() }
        }
    }

    suspend fun getResultById(id: String): CorvusCheckResult? {
        return historyDao.getById(id)?.toCorvusResult()
    }

    suspend fun saveResult(result: CorvusCheckResult) {
        val entity = result.toEntity()
        historyDao.insert(entity)
        Log.d(TAG, "Saved result with id: ${result.id}")
    }

    suspend fun deleteResult(id: String) {
        historyDao.deleteById(id)
        Log.d(TAG, "Deleted result with id: $id")
    }

    suspend fun clearAll() {
        historyDao.clearAll()
        Log.d(TAG, "Cleared all history")
    }

    suspend fun getCount(): Int {
        return historyDao.getCount()
    }

    private fun CorvusHistoryEntity.toCorvusResult(): CorvusCheckResult? {
        return try {
            val result = when (resultType) {
                "GENERAL" -> json.decodeFromString<CorvusCheckResult.GeneralResult>(dataJson)
                "QUOTE" -> json.decodeFromString<CorvusCheckResult.QuoteResult>(dataJson)
                "COMPOSITE" -> json.decodeFromString<CorvusCheckResult.CompositeResult>(dataJson)
                "VIRAL" -> json.decodeFromString<CorvusCheckResult.ViralHoaxResult>(dataJson)
                else -> null
            }
            
            // Fix: ensure the timestamp from the entity is used if the JSON has it as 0
            result?.let {
                if (it.checkedAt == 0L) {
                    when (it) {
                        is CorvusCheckResult.GeneralResult -> it.copy(checkedAt = checkedAt)
                        is CorvusCheckResult.QuoteResult -> it.copy(checkedAt = checkedAt)
                        is CorvusCheckResult.CompositeResult -> it.copy(checkedAt = checkedAt)
                        is CorvusCheckResult.ViralHoaxResult -> it.copy(checkedAt = checkedAt)
                    }
                } else it
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse result $id: ${e.message}")
            null
        }
    }

    private fun CorvusCheckResult.toEntity(): CorvusHistoryEntity {
        val dataJson = when (this) {
            is CorvusCheckResult.GeneralResult -> json.encodeToString(this)
            is CorvusCheckResult.QuoteResult -> json.encodeToString(this)
            is CorvusCheckResult.CompositeResult -> json.encodeToString(this)
            is CorvusCheckResult.ViralHoaxResult -> json.encodeToString(this)
        }
        
        val resultType = when (this) {
            is CorvusCheckResult.QuoteResult -> "QUOTE"
            is CorvusCheckResult.GeneralResult -> "GENERAL"
            is CorvusCheckResult.CompositeResult -> "COMPOSITE"
            is CorvusCheckResult.ViralHoaxResult -> "VIRAL"
        }
        
        val verdictStr = when (this) {
            is CorvusCheckResult.GeneralResult -> verdict.name
            is CorvusCheckResult.QuoteResult -> quoteVerdict.name
            is CorvusCheckResult.CompositeResult -> compositeVerdict.name
            is CorvusCheckResult.ViralHoaxResult -> "FALSE"
        }

        return CorvusHistoryEntity(
            id = id,
            claim = claim,
            resultType = resultType,
            verdict = verdictStr,
            confidence = confidence,
            dataJson = dataJson,
            checkedAt = checkedAt
        )
    }
}
