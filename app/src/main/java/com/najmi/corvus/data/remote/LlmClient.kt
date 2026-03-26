package com.najmi.corvus.data.remote

/**
 * Common interface for all LLM providers.
 */
interface LlmClient {
    suspend fun chat(prompt: String): LlmResponse
}
