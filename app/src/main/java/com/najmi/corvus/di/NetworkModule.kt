package com.najmi.corvus.di

import android.util.Log
import com.najmi.corvus.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
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
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideHttpClient(json: Json): HttpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d("KtorClient", message.take(500))
                }
            }
            level = LogLevel.ALL
        }
        engine {
            connectTimeout = 60_000
            socketTimeout = 120_000
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

    @Provides
    @Singleton
    @Named("cerebras")
    fun provideCerebrasApiKey(): String = BuildConfig.CEREBRAS_API_KEY

    @Provides
    @Singleton
    @Named("openrouter")
    fun provideOpenRouterApiKey(): String = BuildConfig.OPENROUTER_API_KEY

    @Provides
    @Singleton
    @Named("mistral")
    fun provideMistralApiKey(): String = BuildConfig.MISTRAL_API_KEY

    @Provides
    @Singleton
    @Named("cohere")
    fun provideCohereApiKey(): String = BuildConfig.COHERE_API_KEY

    @Provides
    @Singleton
    @Named("google_cse_api_key")
    fun provideGoogleCseApiKey(): String = BuildConfig.GOOGLE_CUSTOM_SEARCH_API_KEY

    @Provides
    @Singleton
    @Named("google_cse_id")
    fun provideGoogleCseId(): String = BuildConfig.GOOGLE_CSE_ID
}
