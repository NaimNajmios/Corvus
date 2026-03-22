package com.najmi.corvus.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CorvusHistoryEntity::class,
        ViralHoaxEntity::class,
        com.najmi.corvus.data.local.entity.KgCacheEntity::class
    ],
    version = 5,
    exportSchema = false 
)
abstract class CorvusDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun viralHoaxDao(): ViralHoaxDao
    abstract fun kgCacheDao(): com.najmi.corvus.data.local.entity.KgCacheDao
    
    companion object {
        const val DATABASE_NAME = "corvus_database"

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE corvus_history ADD COLUMN harmLevel TEXT NOT NULL DEFAULT 'NONE'")
                database.execSQL("ALTER TABLE corvus_history ADD COLUMN harmCategory TEXT NOT NULL DEFAULT 'NONE'")
                database.execSQL("ALTER TABLE corvus_history ADD COLUMN plausibilityScore TEXT DEFAULT NULL")
            }
        }
    }
}