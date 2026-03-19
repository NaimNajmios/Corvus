package com.najmi.corvus.data.repository

import android.util.Log
import com.najmi.corvus.data.local.CorvusHistoryEntity
import com.najmi.corvus.data.local.HistoryDao
import com.najmi.corvus.domain.model.ClaimLanguage
import com.najmi.corvus.domain.model.CorvusResult
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

    fun getAllHistory(): Flow<List<CorvusResult>> {
        return historyDao.getAllHistory().map { entities ->
            entities.map { it.toCorvusResult() }
        }
    }

    fun searchHistory(query: String): Flow<List<CorvusResult>> {
        return historyDao.searchHistory(query).map { entities ->
            entities.map { it.toCorvusResult() }
        }
    }

    fun filterByVerdict(verdict: String): Flow<List<CorvusResult>> {
        return historyDao.filterByVerdict(verdict).map { entities ->
            entities.map { it.toCorvusResult() }
        }
    }

    suspend fun saveResult(result: CorvusResult) {
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

    private fun CorvusHistoryEntity.toCorvusResult(): CorvusResult {
        val sources = try {
            json.decodeFromString<List<Source>>(sourcesJson)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse sources: ${e.message}")
            emptyList()
        }

        return CorvusResult(
            id = id,
            claim = claim,
            verdict = Verdict.valueOf(verdict),
            confidence = confidence,
            explanation = explanation,
            keyFacts = emptyList(),
            sources = sources,
            providerUsed = providerUsed,
            language = ClaimLanguage.valueOf(language),
            checkedAt = checkedAt,
            isFromKnownFactCheck = isFromKnownFactCheck
        )
    }

    private fun CorvusResult.toEntity(): CorvusHistoryEntity {
        val sourcesJson = json.encodeToString(sources)
        return CorvusHistoryEntity(
            id = id,
            claim = claim,
            verdict = verdict.name,
            confidence = confidence,
            explanation = explanation,
            sourcesJson = sourcesJson,
            providerUsed = providerUsed,
            language = language.name,
            checkedAt = checkedAt,
            isFromKnownFactCheck = isFromKnownFactCheck
        )
    }
}