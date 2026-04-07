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
    val queryKey    : String,
    val entityJson  : String,
    val mediaType   : String?,
    val mediaJson   : String?,
    val cachedAt     : Long = System.currentTimeMillis()
)

@Dao
interface KgCacheDao {
    @Query("SELECT * FROM kg_cache WHERE queryKey = :key LIMIT 1")
    suspend fun get(key: String): KgCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: KgCacheEntity)

    @Query("DELETE FROM kg_cache WHERE cachedAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)
}

fun KgCacheEntity.isExpired(): Boolean {
    val ttlMs: Long = when (mediaType) {
        "flag"      -> 30L * 24 * 60 * 60 * 1000
        "logo"      -> 14L * 24 * 60 * 60 * 1000
        "portrait"  -> 7L  * 24 * 60 * 60 * 1000
        "place"     -> 14L * 24 * 60 * 60 * 1000
        else        -> 7L  * 24 * 60 * 60 * 1000
    }
    return (System.currentTimeMillis() - cachedAt) > ttlMs
}

fun buildEntityKey(entityName: String): String {
    return entityName.lowercase().trim().replace(" ", "_")
}
