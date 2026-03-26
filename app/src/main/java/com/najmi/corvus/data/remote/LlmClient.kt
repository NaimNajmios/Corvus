package com.najmi.corvus.data.remote

/**
 * Common interface for all LLM providers.
 * Temporarily downgraded to return String to isolate KSP issues.
 */
interface LlmClient {
    suspend fun chat(prompt: String): String
}
