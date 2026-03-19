package com.najmi.corvus.di

import android.content.Context
import androidx.room.Room
import com.najmi.corvus.data.local.CorvusDatabase
import com.najmi.corvus.data.local.HistoryDao
import com.najmi.corvus.data.local.UserPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CorvusDatabase {
        return Room.databaseBuilder(
            context,
            CorvusDatabase::class.java,
            CorvusDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    @Singleton
    fun provideHistoryDao(database: CorvusDatabase): HistoryDao {
        return database.historyDao()
    }

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(@ApplicationContext context: Context): UserPreferencesRepository {
        return UserPreferencesRepository(context)
    }
}