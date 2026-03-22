package com.najmi.corvus.data.local.entity

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "kg_cache")
data class KgCacheEntity(
    @PrimaryKey
    val queryKey   : String,         // Normalised entity name (lowercase, trimmed)
    val entityJson : String,         // JSON-serialised EntityContext
    val cachedAt   : Long = System.currentTimeMillis()
)

@Dao
interface KgCacheDao {
    @Query("SELECT * FROM kg_cache WHERE queryKey = :key LIMIT 1")
    suspend fun get(key: String): KgCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: KgCacheEntity)

    // Expire entries older than 7 days
    @Query("DELETE FROM kg_cache WHERE cachedAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)
}
