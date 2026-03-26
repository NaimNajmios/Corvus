package com.najmi.corvus.di

import com.najmi.corvus.data.remote.CerebrasClient
import com.najmi.corvus.data.remote.GeminiClient
import com.najmi.corvus.data.remote.GroqClient
import com.najmi.corvus.data.remote.LlmClient
import com.najmi.corvus.data.remote.MistralClient
import com.najmi.corvus.data.remote.OpenRouterClient
import com.najmi.corvus.data.remote.cohere.CohereClient
import com.najmi.corvus.domain.model.LlmProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
abstract class LlmModule {

    @Binds
    @IntoMap
    @LlmProviderKey(LlmProvider.GEMINI)
    abstract fun bindGeminiClient(client: GeminiClient): LlmClient

    @Binds
    @IntoMap
    @LlmProviderKey(LlmProvider.GROQ)
    abstract fun bindGroqClient(client: GroqClient): LlmClient

    @Binds
    @IntoMap
    @LlmProviderKey(LlmProvider.CEREBRAS)
    abstract fun bindCerebrasClient(client: CerebrasClient): LlmClient

    @Binds
    @IntoMap
    @LlmProviderKey(LlmProvider.OPENROUTER)
    abstract fun bindOpenRouterClient(client: OpenRouterClient): LlmClient

    @Binds
    @IntoMap
    @LlmProviderKey(LlmProvider.MISTRAL_SABA)
    abstract fun bindMistralSaba(client: MistralClient): LlmClient

    @Binds
    @IntoMap
    @LlmProviderKey(LlmProvider.MISTRAL_SMALL)
    abstract fun bindMistralSmall(client: MistralClient): LlmClient

    @Binds
    @IntoMap
    @LlmProviderKey(LlmProvider.COHERE_R)
    abstract fun bindCohereR(client: CohereClient): LlmClient

    @Binds
    @IntoMap
    @LlmProviderKey(LlmProvider.COHERE_R_PLUS)
    abstract fun bindCohereRPlus(client: CohereClient): LlmClient
}
