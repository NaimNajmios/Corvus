package com.najmi.corvus.data.local

import androidx.room.*
import com.najmi.corvus.data.local.entity.TokenReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TokenReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: TokenReportEntity)

    @Query("SELECT * FROM token_reports WHERE checkId = :id")
    suspend fun getReportForCheck(id: String): TokenReportEntity?

    @Query("SELECT * FROM token_reports ORDER BY timestamp DESC")
    fun getAllReports(): Flow<List<TokenReportEntity>>

    @Query("SELECT SUM(totalCombinedTokens) FROM token_reports")
    fun getTotalTokensFlow(): Flow<Int?>

    @Query("DELETE FROM token_reports")
    suspend fun clearAll()
}
