package com.najmi.corvus.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CorvusHistoryEntity::class,
        ViralHoaxEntity::class,
        com.najmi.corvus.data.local.entity.KgCacheEntity::class,
        com.najmi.corvus.data.local.entity.TokenReportEntity::class
    ],
    version = 6,
    exportSchema = false 
)
abstract class CorvusDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun viralHoaxDao(): ViralHoaxDao
    abstract fun kgCacheDao(): com.najmi.corvus.data.local.entity.KgCacheDao
    abstract fun tokenReportDao(): TokenReportDao
    
    companion object {
        const val DATABASE_NAME = "corvus_database"

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE corvus_history ADD COLUMN harmLevel TEXT NOT NULL DEFAULT 'NONE'")
                database.execSQL("ALTER TABLE corvus_history ADD COLUMN harmCategory TEXT NOT NULL DEFAULT 'NONE'")
                database.execSQL("ALTER TABLE corvus_history ADD COLUMN plausibilityScore TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS token_reports (
                        checkId TEXT NOT NULL PRIMARY KEY,
                        totalPromptTokens INTEGER NOT NULL,
                        totalCompletionTokens INTEGER NOT NULL,
                        totalCombinedTokens INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        breakdownJson TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_4_5, MIGRATION_5_6)
    }
}