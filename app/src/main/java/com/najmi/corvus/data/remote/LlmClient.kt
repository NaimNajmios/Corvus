package com.najmi.corvus.data.remote

/**
 * Common interface for all LLM providers (Groq, Gemini, Mistral, etc.)
 * to enable provider-agnostic routing and fallbacks.
 */
interface LlmClient {
    /**
     * Executes a chat completion request with the given prompt.
     * @param prompt The full text prompt to send to the LLM.
     * @return The text content of the LLM's response.
     * @throws Exception if the request fails or returns an error.
     */
    suspend fun chat(prompt: String): String
}
