package com.najmi.corvus.domain.router

import com.najmi.corvus.data.remote.LlmClient
import com.najmi.corvus.domain.model.LlmProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*

@RunWith(MockitoJUnitRunner::class)
class LlmProviderRouterTest {

    private val groqClient: LlmClient = mock()
    private val geminiClient: LlmClient = mock()
    private val healthTracker: LlmProviderHealthTracker = mock()

    private lateinit var router: LlmProviderRouter
    private lateinit var clients: Map<LlmProvider, LlmClient>

    @Before
    fun setup() {
        clients = mapOf(
            LlmProvider.GROQ to groqClient,
            LlmProvider.GEMINI to geminiClient
        )
        router = LlmProviderRouter(clients, healthTracker)
    }

    @Test
    fun executeUsesPreferredProviderWhenHealthy() {
        runBlocking {
            whenever(healthTracker.isAvailable(LlmProvider.GROQ)).thenReturn(true)
            whenever(groqClient.chat(any())).thenReturn("Groq result")

            val result = router.execute("test prompt", LlmProvider.GROQ)

            assertEquals("Groq result", result.text)
            verify(groqClient).chat("test prompt")
            verify(geminiClient, never()).chat(any())
        }
    }

    @Test
    fun executeFallsBackToGeminiWhenPreferredProviderFails() {
        runBlocking {
            whenever(healthTracker.isAvailable(LlmProvider.GROQ)).thenReturn(true)
            whenever(healthTracker.isAvailable(LlmProvider.GEMINI)).thenReturn(true)
            
            // Use doAnswer for suspend functions throwing exceptions
            doAnswer { throw RuntimeException("429 Too Many Requests") }
                .whenever(groqClient).chat(any())
                
            whenever(geminiClient.chat(any())).thenReturn("Gemini fallback result")

            val result = router.execute("test prompt", LlmProvider.GROQ)

            assertEquals("Gemini fallback result", result.text)
            verify(healthTracker).reportError(LlmProvider.GROQ.name)
            verify(geminiClient).chat("test prompt")
        }
    }

    @Test
    fun executeSkipsUnhealthyPreferredProvider() {
        runBlocking {
            whenever(healthTracker.isAvailable(LlmProvider.GROQ)).thenReturn(false)
            whenever(healthTracker.isAvailable(LlmProvider.GEMINI)).thenReturn(true)
            whenever(geminiClient.chat(any())).thenReturn("Gemini result")

            val result = router.execute("test prompt", LlmProvider.GROQ)

            assertEquals("Gemini result", result.text)
            verify(groqClient, never()).chat(any())
            verify(geminiClient).chat("test prompt")
        }
    }
}
