package com.najmi.corvus.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ViralHoaxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHoax(hoax: ViralHoaxEntity)

    @Query("SELECT * FROM viral_hoaxes")
    suspend fun getAllHoaxes(): List<ViralHoaxEntity>

    @Query("DELETE FROM viral_hoaxes")
    suspend fun deleteAll()
    
    @Query("DELETE FROM viral_hoaxes WHERE searchedAt < :timestamp")
    suspend fun deleteOldHoaxes(timestamp: Long)
}
