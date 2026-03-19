package com.najmi.corvus.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CorvusHistoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class CorvusDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    
    companion object {
        const val DATABASE_NAME = "corvus_database"
    }
}