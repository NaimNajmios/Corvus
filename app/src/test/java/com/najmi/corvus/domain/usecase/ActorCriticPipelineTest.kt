package com.najmi.corvus.domain.usecase

import com.najmi.corvus.data.remote.SourceContextBuilder
import com.najmi.corvus.data.remote.LlmResponse
import com.najmi.corvus.domain.model.*
import com.najmi.corvus.domain.router.LlmProviderHealthTracker
import com.najmi.corvus.domain.router.LlmProviderRouter
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class ActorCriticPipelineTest {

    private val router: LlmProviderRouter = mock()
    private val contextBuilder: SourceContextBuilder = mock()
    private val providerSelector: ActorCriticProviderSelector = mock()
    private val healthTracker: LlmProviderHealthTracker = mock()
    private val domainJson = Json { ignoreUnknownKeys = true }
    private val tokenCollector: com.najmi.corvus.domain.util.TokenCollector = mock()

    private lateinit var pipeline: ActorCriticPipeline

    @Before
    fun setup() {
        pipeline = ActorCriticPipeline(
            router,
            contextBuilder,
            providerSelector,
            domainJson,
            healthTracker,
            tokenCollector
        )
    }

    @Test
    fun `analyze executes actor and then critic pass`() = runBlocking {
        // Arrange
        val claim = "Test Claim"
        val classified = ClassifiedClaim(raw = claim, type = ClaimType.GENERAL, entities = emptyList())
        val sources = listOf(Source("Title", "url", snippet = "Snippet"))
        
        val assignment = ProviderAssignment(LlmProvider.GROQ, LlmProvider.GEMINI, "Test rationale")
        whenever(providerSelector.select(any(), any())).thenReturn(assignment)
        whenever(contextBuilder.build(any(), any())).thenReturn("context")
        whenever(healthTracker.isHealthy(any())).thenReturn(true)

        val actorJson = """
            {
              "evidentiary_analysis": "actor reasoning",
              "draft_verdict": "TRUE",
              "draft_confidence": 0.8,
              "draft_explanation": "actor draft",
              "sources_used": [0],
              "unsupported_assumptions": ["assumption"]
            }
        """.trimIndent()

        val criticJson = """
            {
              "evidentiary_analysis": "critic review",
              "verdict": "TRUE",
              "confidence": 0.9,
              "explanation": "final explanation",
              "key_facts": [],
              "sources_used": [0],
              "corrections_made": ["corrected assumption"],
              "harm_assessment": { "level": "NONE", "category": "NONE", "reason": "" }
            }
        """.trimIndent()

        whenever(router.execute(any(), eq(LlmProvider.GROQ))).thenReturn(LlmResponse(actorJson, TokenUsage.EMPTY))
        whenever(router.execute(any(), eq(LlmProvider.GEMINI))).thenReturn(LlmResponse(criticJson, TokenUsage.EMPTY))

        // Act
        val result = pipeline.analyze(claim, classified, sources)

        // Assert
        assertEquals(Verdict.TRUE, result.generalResult.verdict)
        assertEquals(0.9f, result.generalResult.confidence)
        assertEquals("final explanation", result.generalResult.explanation)
        assertEquals(LlmProvider.GROQ, result.generalResult.actorProvider)
        assertEquals(LlmProvider.GEMINI, result.generalResult.criticProvider)
        
        // Verify both passes happened
        verify(router).execute(argThat { contains("analyst") }, eq(LlmProvider.GROQ))
        verify(router).execute(argThat { contains("editor") }, eq(LlmProvider.GEMINI))
    }
}
