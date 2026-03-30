package com.najmi.corvus.di

import android.content.Context
import androidx.room.Room
import com.najmi.corvus.data.local.BookmarkDao
import com.najmi.corvus.data.local.CorvusDatabase
import com.najmi.corvus.data.local.HistoryDao
import com.najmi.corvus.data.local.TokenReportDao
import com.najmi.corvus.data.local.ViralHoaxDao
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
        ).addMigrations(*CorvusDatabase.MIGRATIONS)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideHistoryDao(database: CorvusDatabase): HistoryDao {
        return database.historyDao()
    }

    @Provides
    @Singleton
    fun provideViralHoaxDao(database: CorvusDatabase): ViralHoaxDao {
        return database.viralHoaxDao()
    }

    @Provides
    @Singleton
    fun provideTokenReportDao(database: CorvusDatabase): TokenReportDao {
        return database.tokenReportDao()
    }

    @Provides
    @Singleton
    fun provideBookmarkDao(database: CorvusDatabase): BookmarkDao {
        return database.bookmarkDao()
    }
}