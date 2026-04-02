package com.najmi.corvus.domain.usecase

import com.najmi.corvus.data.remote.SourceContextBuilder
import com.najmi.corvus.data.remote.LlmResponse
import com.najmi.corvus.domain.model.*
import com.najmi.corvus.domain.router.LlmProviderRouter
import com.najmi.corvus.domain.util.TokenCollector
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class HolisticClaimVerifierTest {

    private val router: LlmProviderRouter = mock()
    private val contextBuilder: SourceContextBuilder = mock()
    private val tokenCollector: TokenCollector = mock()
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var verifier: HolisticClaimVerifier

    @Before
    fun setup() {
        verifier = HolisticClaimVerifier(router, contextBuilder, tokenCollector)
        whenever(contextBuilder.build(any(), any())).thenReturn("Mock source context")
    }

    @Test
    fun `verify returns UNVERIFIABLE when subClaims are empty`() = runBlocking {
        val result = verifier.verify("Original claim", emptyList(), emptyList())
        
        assertEquals(Verdict.UNVERIFIABLE, result.holisticVerdict)
        assertTrue(result.issuesFound.isEmpty())
    }

    @Test
    fun `verify returns UNVERIFIABLE when sources are empty`() = runBlocking {
        val subClaims = listOf(
            SubClaim(text = "Sub 1", index = 0, result = CorvusCheckResult.GeneralResult(verdict = Verdict.TRUE))
        )
        
        val result = verifier.verify("Original claim", subClaims, emptyList())
        
        assertEquals(Verdict.UNVERIFIABLE, result.holisticVerdict)
    }

    @Test
    fun `verify returns MISLEADING when all subclaims true but issues found`() = runBlocking {
        val subClaims = listOf(
            SubClaim(
                text = "Company X increased revenue",
                index = 0,
                result = CorvusCheckResult.GeneralResult(verdict = Verdict.TRUE, explanation = "Revenue increased 5%")
            ),
            SubClaim(
                text = "Company X increased profit",
                index = 1,
                result = CorvusCheckResult.GeneralResult(verdict = Verdict.TRUE, explanation = "Profit decreased 20%")
            )
        )
        
        val sources = listOf(
            Source("Financial Report", "url1", snippet = "Revenue up 5%"),
            Source("Financial Report", "url2", snippet = "Profit down 20%")
        )
        
        val holisticResponse = """
            {
              "holistic_verdict": "MISLEADING",
              "holistic_explanation": "While individual facts are true, omitting the profit decline creates a misleading impression",
              "confidence": 0.85,
              "evidentiary_analysis": "Both subclaims are factually correct",
              "issues_found": [
                {
                  "type": "SELECTIVE_TRUTH",
                  "description": "Revenue increase shown but profit decline omitted",
                  "affected_subclaims": [0],
                  "severity": "HIGH"
                }
              ],
              "corrections_applied": []
            }
        """.trimIndent()
        
        whenever(router.execute(any(), eq(LlmProvider.GEMINI)))
            .thenReturn(LlmResponse(holisticResponse, TokenUsage.EMPTY))
        
        val result = verifier.verify("Company X is doing great", subClaims, sources)
        
        assertEquals(Verdict.MISLEADING, result.holisticVerdict)
        assertEquals(1, result.issuesFound.size)
        assertEquals(HolisticIssueType.SELECTIVE_TRUTH, result.issuesFound[0].type)
        assertEquals(0.85f, result.confidence)
    }

    @Test
    fun `verify returns TRUE when all subclaims true and no issues`() = runBlocking {
        val subClaims = listOf(
            SubClaim(
                text = "Water freezes at 0C",
                index = 0,
                result = CorvusCheckResult.GeneralResult(verdict = Verdict.TRUE)
            ),
            SubClaim(
                text = "Water boils at 100C",
                index = 1,
                result = CorvusCheckResult.GeneralResult(verdict = Verdict.TRUE)
            )
        )
        
        val sources = listOf(
            Source("Science Book", "url1", snippet = "Freezing point of water is 0C"),
            Source("Science Book", "url2", snippet = "Boiling point of water is 100C")
        )
        
        val holisticResponse = """
            {
              "holistic_verdict": "TRUE",
              "holistic_explanation": "Both subclaims are accurate and together form a true statement",
              "confidence": 0.95,
              "evidentiary_analysis": "Both claims are scientifically accurate",
              "issues_found": [],
              "corrections_applied": []
            }
        """.trimIndent()
        
        whenever(router.execute(any(), eq(LlmProvider.GEMINI)))
            .thenReturn(LlmResponse(holisticResponse, TokenUsage.EMPTY))
        
        val result = verifier.verify("Water properties", subClaims, sources)
        
        assertEquals(Verdict.TRUE, result.holisticVerdict)
        assertTrue(result.issuesFound.isEmpty())
    }

    @Test
    fun `verify handles MISSING_CONTEXT issue type`() = runBlocking {
        val subClaims = listOf(
            SubClaim(
                text = "X is the best city",
                index = 0,
                result = CorvusCheckResult.GeneralResult(verdict = Verdict.TRUE)
            )
        )
        
        val sources = listOf(
            Source("Travel Magazine", "url", snippet = "X was voted best city in 1990")
        )
        
        val holisticResponse = """
            {
              "holistic_verdict": "MISLEADING",
              "holistic_explanation": "The award was from 1990, not current",
              "confidence": 0.9,
              "evidentiary_analysis": "Historical context missing",
              "issues_found": [
                {
                  "type": "MISSING_CONTEXT",
                  "description": "Voting context was 35 years ago",
                  "affected_subclaims": [0],
                  "severity": "MODERATE"
                }
              ],
              "corrections_applied": []
            }
        """.trimIndent()
        
        whenever(router.execute(any(), eq(LlmProvider.GEMINI)))
            .thenReturn(LlmResponse(holisticResponse, TokenUsage.EMPTY))
        
        val result = verifier.verify("X is the best city", subClaims, sources)
        
        assertEquals(Verdict.MISLEADING, result.holisticVerdict)
        assertEquals(HolisticIssueType.MISSING_CONTEXT, result.issuesFound[0].type)
    }

    @Test
    fun `verify handles CHERRY_PICKING issue type`() = runBlocking {
        val subClaims = listOf(
            SubClaim(
                text = "Product sales increased in January",
                index = 0,
                result = CorvusCheckResult.GeneralResult(verdict = Verdict.TRUE)
            )
        )
        
        val sources = listOf(
            Source("Sales Report", "url", snippet = "January sales up 50%, February sales down 40%")
        )
        
        val holisticResponse = """
            {
              "holistic_verdict": "MISLEADING",
              "holistic_explanation": "Cherry-picking the best month",
              "confidence": 0.88,
              "evidentiary_analysis": "Only one month highlighted",
              "issues_found": [
                {
                  "type": "CHERRY_PICKING",
                  "description": "Only January highlighted, February decline ignored",
                  "affected_subclaims": [0],
                  "severity": "HIGH"
                }
              ],
              "corrections_applied": []
            }
        """.trimIndent()
        
        whenever(router.execute(any(), eq(LlmProvider.GEMINI)))
            .thenReturn(LlmResponse(holisticResponse, TokenUsage.EMPTY))
        
        val result = verifier.verify("Product sales are great", subClaims, sources)
        
        assertEquals(HolisticIssueType.CHERRY_PICKING, result.issuesFound[0].type)
    }

    @Test
    fun `verify handles OMISSION issue type`() = runBlocking {
        val subClaims = listOf(
            SubClaim(
                text = "Candidate X supports education",
                index = 0,
                result = CorvusCheckResult.GeneralResult(verdict = Verdict.TRUE)
            )
        )
        
        val sources = listOf(
            Source("Article", "url", snippet = "Candidate X supports education but opposes healthcare funding")
        )
        
        val holisticResponse = """
            {
              "holistic_verdict": "MISLEADING",
              "holistic_explanation": "Important stance omitted",
              "confidence": 0.9,
              "evidentiary_analysis": "Partial information presented",
              "issues_found": [
                {
                  "type": "OMISSION",
                  "description": "Healthcare opposition stance not mentioned",
                  "affected_subclaims": [0],
                  "severity": "HIGH"
                }
              ],
              "corrections_applied": []
            }
        """.trimIndent()
        
        whenever(router.execute(any(), eq(LlmProvider.GEMINI)))
            .thenReturn(LlmResponse(holisticResponse, TokenUsage.EMPTY))
        
        val result = verifier.verify("Candidate X is a good choice", subClaims, sources)
        
        assertEquals(HolisticIssueType.OMISSION, result.issuesFound[0].type)
    }

    @Test
    fun `verify falls back to UNVERIFIABLE on LLM error`() = runBlocking {
        val subClaims = listOf(
            SubClaim(text = "Test", index = 0, result = CorvusCheckResult.GeneralResult(verdict = Verdict.TRUE))
        )
        val sources = listOf(Source("Test", "url", snippet = "Test"))
        
        whenever(router.execute(any(), eq(LlmProvider.GEMINI)))
            .thenThrow(RuntimeException("Network error"))
        
        val result = verifier.verify("Test claim", subClaims, sources)
        
        assertEquals(Verdict.UNVERIFIABLE, result.holisticVerdict)
        assertTrue(result.holisticExplanation.contains("failed"))
    }

    @Test
    fun `verify falls back to UNVERIFIABLE on JSON parse error`() = runBlocking {
        val subClaims = listOf(
            SubClaim(text = "Test", index = 0, result = CorvusCheckResult.GeneralResult(verdict = Verdict.TRUE))
        )
        val sources = listOf(Source("Test", "url", snippet = "Test"))
        
        whenever(router.execute(any(), eq(LlmProvider.GEMINI)))
            .thenReturn(LlmResponse("Invalid JSON {{{", TokenUsage.EMPTY))
        
        val result = verifier.verify("Test claim", subClaims, sources)
        
        assertEquals(Verdict.UNVERIFIABLE, result.holisticVerdict)
    }

    @Test
    fun `verify returns PARTIALLY_TRUE for mixed verdicts`() = runBlocking {
        val subClaims = listOf(
            SubClaim(text = "Fact A", index = 0, result = CorvusCheckResult.GeneralResult(verdict = Verdict.TRUE)),
            SubClaim(text = "Fact B", index = 1, result = CorvusCheckResult.GeneralResult(verdict = Verdict.FALSE))
        )
        
        val sources = listOf(
            Source("Source A", "url1", snippet = "Fact A is true"),
            Source("Source B", "url2", snippet = "Fact B is false")
        )
        
        val holisticResponse = """
            {
              "holistic_verdict": "PARTIALLY_TRUE",
              "holistic_explanation": "Mixed claims with some truth",
              "confidence": 0.75,
              "evidentiary_analysis": "Mixed analysis",
              "issues_found": [],
              "corrections_applied": []
            }
        """.trimIndent()
        
        whenever(router.execute(any(), eq(LlmProvider.GEMINI)))
            .thenReturn(LlmResponse(holisticResponse, TokenUsage.EMPTY))
        
        val result = verifier.verify("Mixed claim", subClaims, sources)
        
        assertEquals(Verdict.PARTIALLY_TRUE, result.holisticVerdict)
    }

    @Test
    fun `verify handles QuoteResult subclaims`() = runBlocking {
        val subClaims = listOf(
            SubClaim(
                text = "Person said X",
                index = 0,
                result = CorvusCheckResult.QuoteResult(quoteVerdict = QuoteVerdict.VERIFIED)
            )
        )
        
        val sources = listOf(
            Source("Article", "url", snippet = "Person said X")
        )
        
        val holisticResponse = """
            {
              "holistic_verdict": "TRUE",
              "holistic_explanation": "Quote verified",
              "confidence": 0.95,
              "evidentiary_analysis": "Quote analysis",
              "issues_found": [],
              "corrections_applied": []
            }
        """.trimIndent()
        
        whenever(router.execute(any(), eq(LlmProvider.GEMINI)))
            .thenReturn(LlmResponse(holisticResponse, TokenUsage.EMPTY))
        
        val result = verifier.verify("Quote claim", subClaims, sources)
        
        assertEquals(Verdict.TRUE, result.holisticVerdict)
    }

    @Test
    fun `verify aggregates corrections from LLM response`() = runBlocking {
        val subClaims = listOf(
            SubClaim(text = "Test", index = 0, result = CorvusCheckResult.GeneralResult(verdict = Verdict.TRUE))
        )
        val sources = listOf(Source("Test", "url", snippet = "Test"))
        
        val holisticResponse = """
            {
              "holistic_verdict": "MISLEADING",
              "holistic_explanation": "Missing context",
              "confidence": 0.8,
              "evidentiary_analysis": "Analysis",
              "issues_found": [
                {
                  "type": "MISSING_CONTEXT",
                  "description": "Context missing",
                  "affected_subclaims": [0],
                  "severity": "MODERATE"
                }
              ],
              "corrections_applied": ["Add historical context", "Include opposing view"]
            }
        """.trimIndent()
        
        whenever(router.execute(any(), eq(LlmProvider.GEMINI)))
            .thenReturn(LlmResponse(holisticResponse, TokenUsage.EMPTY))
        
        val result = verifier.verify("Test claim", subClaims, sources)
        
        assertEquals(2, result.correctionsApplied.size)
        assertTrue(result.correctionsApplied.contains("Add historical context"))
    }
}
