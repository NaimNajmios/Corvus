package com.najmi.corvus.di

import com.najmi.corvus.BuildConfig
import com.najmi.corvus.data.local.CorvusDatabase
import com.najmi.corvus.data.local.entity.KgCacheDao
import com.najmi.corvus.data.remote.GroqClient
import com.najmi.corvus.data.remote.knowledgegraph.KnowledgeGraphClient
import com.najmi.corvus.data.remote.knowledgegraph.KgEntityMapper
import com.najmi.corvus.domain.usecase.EntityExtractorUseCase
import com.najmi.corvus.domain.usecase.KgEnricherUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object KgModule {

    @Provides
    @Singleton
    fun provideKnowledgeGraphClient(
        httpClient: HttpClient,
        @Named("kgApiKey") apiKey: String
    ): KnowledgeGraphClient = KnowledgeGraphClient(httpClient, apiKey)

    @Provides
    @Named("kgApiKey")
    fun provideKgApiKey(): String = BuildConfig.GOOGLE_KG_API_KEY

    @Provides
    @Singleton
    fun provideKgEntityMapper(): KgEntityMapper = KgEntityMapper

    @Provides
    @Singleton
    fun provideEntityExtractorUseCase(
        groqClient: GroqClient
    ): EntityExtractorUseCase = EntityExtractorUseCase(groqClient)

    @Provides
    @Singleton
    fun provideKgEnricherUseCase(
        extractor: EntityExtractorUseCase,
        client: KnowledgeGraphClient,
        kgCacheDao: KgCacheDao,
        json: Json
    ): KgEnricherUseCase = KgEnricherUseCase(extractor, client, kgCacheDao, json)

    @Provides
    @Singleton
    fun provideKgCacheDao(database: CorvusDatabase): KgCacheDao = database.kgCacheDao()
}
