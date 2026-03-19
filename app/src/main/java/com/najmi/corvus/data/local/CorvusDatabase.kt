package com.najmi.corvus.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

import androidx.room.AutoMigration

@Database(
    entities = [
        CorvusHistoryEntity::class,
        ViralHoaxEntity::class
    ],
    version = 3,
    exportSchema = false 
)
abstract class CorvusDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun viralHoaxDao(): ViralHoaxDao
    
    companion object {
        const val DATABASE_NAME = "corvus_database"
    }
}