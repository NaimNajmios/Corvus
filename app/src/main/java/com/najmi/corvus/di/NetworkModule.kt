package com.najmi.corvus.di

import com.najmi.corvus.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideHttpClient(json: Json): HttpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
        engine {
            connectTimeout = 30_000
            socketTimeout = 60_000
        }
    }

    @Provides
    @Singleton
    @Named("google_fact_check")
    fun provideGoogleFactCheckApiKey(): String = BuildConfig.GOOGLE_FACT_CHECK_API_KEY

    @Provides
    @Singleton
    @Named("tavily")
    fun provideTavilyApiKey(): String = BuildConfig.TAVILY_API_KEY

    @Provides
    @Singleton
    @Named("gemini")
    fun provideGeminiApiKey(): String = BuildConfig.GEMINI_API_KEY

    @Provides
    @Singleton
    @Named("groq")
    fun provideGroqApiKey(): String = BuildConfig.GROQ_API_KEY
}
