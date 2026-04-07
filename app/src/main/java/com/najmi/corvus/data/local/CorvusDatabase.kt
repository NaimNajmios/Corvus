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
        com.najmi.corvus.data.local.entity.TokenReportEntity::class,
        BookmarkEntity::class
    ],
    version = 8,
    exportSchema = false 
)
abstract class CorvusDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun viralHoaxDao(): ViralHoaxDao
    abstract fun kgCacheDao(): com.najmi.corvus.data.local.entity.KgCacheDao
    abstract fun tokenReportDao(): TokenReportDao
    abstract fun bookmarkDao(): BookmarkDao
    
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

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS bookmarks (
                        id TEXT NOT NULL PRIMARY KEY,
                        resultId TEXT NOT NULL,
                        claim TEXT NOT NULL,
                        resultType TEXT NOT NULL,
                        verdict TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        bookmarkedAt INTEGER NOT NULL,
                        userNotes TEXT NOT NULL DEFAULT '',
                        tags TEXT NOT NULL DEFAULT '',
                        lastEditedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE kg_cache ADD COLUMN mediaType TEXT")
                database.execSQL("ALTER TABLE kg_cache ADD COLUMN mediaJson TEXT")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
    }
}