package com.najmi.corvus.di

import com.najmi.corvus.data.remote.CerebrasClient
import com.najmi.corvus.data.remote.GeminiClient
import com.najmi.corvus.data.remote.GroqClient
import com.najmi.corvus.data.remote.LlmClient
import com.najmi.corvus.data.remote.MistralClient
import com.najmi.corvus.data.remote.OpenRouterClient
import com.najmi.corvus.data.remote.cohere.CohereClient
import com.najmi.corvus.domain.model.LlmProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Singleton

// Custom key for LlmProvider enum in Hilt multibinding
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@dagger.MapKey
annotation class LlmProviderKey(val value: LlmProvider)

@Module
@InstallIn(SingletonComponent::class)
object LlmModule {

    @Provides
    @IntoMap
    @LlmProviderKey(LlmProvider.GROQ)
    fun bindGroqClient(client: GroqClient): LlmClient = client

    @Provides
    @IntoMap
    @LlmProviderKey(LlmProvider.GEMINI)
    fun bindGeminiClient(client: GeminiClient): LlmClient = client

    @Provides
    @IntoMap
    @LlmProviderKey(LlmProvider.CEREBRAS)
    fun bindCerebrasClient(client: CerebrasClient): LlmClient = client

    @Provides
    @IntoMap
    @LlmProviderKey(LlmProvider.MISTRAL_SMALL)
    fun bindMistralClient(client: MistralClient): LlmClient = client

    @Provides
    @IntoMap
    @LlmProviderKey(LlmProvider.MISTRAL_SABA)
    fun bindMistralSabaClient(client: MistralClient): LlmClient = object : LlmClient {
        override suspend fun chat(prompt: String): String = client.chatSaba(prompt)
    }

    @Provides
    @IntoMap
    @LlmProviderKey(LlmProvider.OPENROUTER)
    fun bindOpenRouterClient(client: OpenRouterClient): LlmClient = client

    @Provides
    @IntoMap
    @LlmProviderKey(LlmProvider.COHERE_R)
    fun bindCohereRClient(client: CohereClient): LlmClient = object : LlmClient {
        override suspend fun chat(prompt: String): String = client.chatR(prompt)
    }

    @Provides
    @IntoMap
    @LlmProviderKey(LlmProvider.COHERE_R_PLUS)
    fun bindCohereRPlusClient(client: CohereClient): LlmClient = object : LlmClient {
        override suspend fun chat(prompt: String): String = client.chatRPlus(prompt)
    }
}
