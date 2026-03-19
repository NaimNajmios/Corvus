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

    @Query("SELECT * FROM corvus_history WHERE claim LIKE '%' || :query || '%' ORDER BY checkedAt DESC")
    fun searchHistory(query: String): Flow<List<CorvusHistoryEntity>>

    @Query("SELECT * FROM corvus_history WHERE verdict = :verdict ORDER BY checkedAt DESC")
    fun filterByVerdict(verdict: String): Flow<List<CorvusHistoryEntity>>

    @Query("SELECT * FROM corvus_history WHERE id = :id")
    suspend fun getById(id: String): CorvusHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CorvusHistoryEntity)

    @Delete
    suspend fun delete(entity: CorvusHistoryEntity)

    @Query("DELETE FROM corvus_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM corvus_history")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM corvus_history")
    suspend fun getCount(): Int
}