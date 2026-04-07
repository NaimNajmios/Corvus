package com.najmi.corvus.di

import com.najmi.corvus.data.remote.media.RestCountriesClient
import com.najmi.corvus.data.remote.media.WikipediaMediaClient
import com.najmi.corvus.domain.usecase.EntityMediaResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    fun provideWikipediaMediaClient(
        httpClient: HttpClient
    ): WikipediaMediaClient = WikipediaMediaClient(httpClient)

    @Provides
    @Singleton
    fun provideRestCountriesClient(
        httpClient: HttpClient
    ): RestCountriesClient = RestCountriesClient(httpClient)

    @Provides
    @Singleton
    fun provideEntityMediaResolver(
        wikiClient      : WikipediaMediaClient,
        countriesClient : RestCountriesClient
    ): EntityMediaResolver = EntityMediaResolver(wikiClient, countriesClient)
}
