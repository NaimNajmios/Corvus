package com.najmi.corvus.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM corvus_history ORDER BY checkedAt DESC")
    fun getAllHistory(): Flow<List<CorvusHistoryEntity>>

    @Query("SELECT id, claim, resultType, verdict, confidence, checkedAt, harmLevel, harmCategory, plausibilityScore FROM corvus_history ORDER BY checkedAt DESC")
    fun getAllHistorySummaries(): Flow<List<HistorySummaryProjection>>

    @Query("SELECT * FROM corvus_history WHERE claim LIKE '%' || :query || '%' ORDER BY checkedAt DESC")
    fun searchHistory(query: String): Flow<List<CorvusHistoryEntity>>

    @Query("SELECT id, claim, resultType, verdict, confidence, checkedAt, harmLevel, harmCategory, plausibilityScore FROM corvus_history WHERE claim LIKE '%' || :query || '%' ORDER BY checkedAt DESC")
    fun searchHistorySummaries(query: String): Flow<List<HistorySummaryProjection>>

    @Query("SELECT * FROM corvus_history WHERE verdict = :verdict ORDER BY checkedAt DESC")
    fun filterByVerdict(verdict: String): Flow<List<CorvusHistoryEntity>>

    @Query("SELECT id, claim, resultType, verdict, confidence, checkedAt, harmLevel, harmCategory, plausibilityScore FROM corvus_history WHERE verdict = :verdict ORDER BY checkedAt DESC")
    fun filterByVerdictSummaries(verdict: String): Flow<List<HistorySummaryProjection>>

    @Query("SELECT * FROM corvus_history WHERE id = :id")
    suspend fun getById(id: String): CorvusHistoryEntity?

    @Query("SELECT * FROM corvus_history ORDER BY checkedAt DESC LIMIT 1")
    suspend fun getLatestResultFull(): CorvusHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CorvusHistoryEntity)

    @Delete
    suspend fun delete(entity: CorvusHistoryEntity)

    @Query("DELETE FROM corvus_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM corvus_history WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM corvus_history")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM corvus_history")
    suspend fun getCount(): Int
}