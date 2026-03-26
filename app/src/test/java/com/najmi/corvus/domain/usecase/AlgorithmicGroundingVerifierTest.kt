package com.najmi.corvus.domain.usecase

import com.najmi.corvus.domain.model.CitationConfidence
import com.najmi.corvus.domain.model.FactVerification
import com.najmi.corvus.domain.model.GroundedFact
import com.najmi.corvus.domain.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AlgorithmicGroundingVerifierTest {

    private lateinit var verifier: AlgorithmicGroundingVerifier

    @Before
    fun setup() {
        verifier = AlgorithmicGroundingVerifier()
    }

    @Test
    fun `verify handles non-quote facts without penalty`() {
        val facts = listOf(GroundedFact("Regular statement", sourceIndex = 0, isDirectQuote = false))
        val sources = listOf(Source(title = "S1", url = "http://s1.com", snippet = "any content"))
        
        val result = verifier.verify(facts, sources)
        
        assertEquals(0f, result.totalConfidencePenalty)
        assertTrue(result.fabricatedCitations.isEmpty())
        assertEquals(facts[0], result.verifiedFacts[0])
    }

    @Test
    fun `verify confirms exact quote in source`() {
        val quote = "The ringgit opened higher against the US dollar"
        val facts = listOf(GroundedFact("\"$quote\"", sourceIndex = 0, isDirectQuote = true))
        val sources = listOf(Source(title = "Bernama", url = "http://bn.com", snippet = "Today, $quote following positive trade data."))
        
        val result = verifier.verify(facts, sources)
        
        assertEquals(0f, result.totalConfidencePenalty)
        assertEquals(CitationConfidence.VERIFIED, result.verifiedFacts[0].verification?.confidence)
        assertNotNull(result.verifiedFacts[0].verification?.matchedFragment)
    }

    @Test
    fun `verify detects fabricated citation and applies penalty`() {
        val facts = listOf(GroundedFact("\"This quote does not exist\"", sourceIndex = 0, isDirectQuote = true))
        val sources = listOf(Source(title = "S1", url = "http://s1.com", snippet = "Completely different content."))
        
        val result = verifier.verify(facts, sources)
        
        assertEquals(AlgorithmicGroundingVerifier.FABRICATED_CITATION_PENALTY, result.totalConfidencePenalty)
        assertEquals(1, result.fabricatedCitations.size)
        // Citation should be stripped (sourceIndex null)
        assertNull(result.verifiedFacts[0].sourceIndex)
        assertFalse(result.verifiedFacts[0].isDirectQuote)
        assertEquals(CitationConfidence.LOW_CONFIDENCE, result.verifiedFacts[0].verification?.confidence)
    }

    @Test
    fun `verify handles partial matches by downgrading to paraphrase`() {
        // High similarity but not exact (e.g. 1 word diff)
        val quote = "The economy is growing by five percent"
        val facts = listOf(GroundedFact("\"$quote\"", sourceIndex = 0, isDirectQuote = true))
        // Source says "four percent" invece di "five percent"
        val sources = listOf(Source(title = "S1", url = "http://s1.com", snippet = "The economy is growing by four percent this year."))
        
        val result = verifier.verify(facts, sources)
        
        // Should be between 0.55 and 0.80 similarity
        // economy is growing by (5 words) -> 5/8 match? No, let's just assert result state.
        val verifiedFact = result.verifiedFacts[0]
        
        // It shouldn't be VERIFIED (exact), but it might be PARTIAL
        assertFalse(verifiedFact.isDirectQuote)
        assertNotNull(verifiedFact.sourceIndex) // Keeps citation if partial
        assertEquals(CitationConfidence.PARTIAL, verifiedFact.verification?.confidence)
    }

    @Test
    fun `verify caps total penalty`() {
        val facts = (1..10).map { 
            GroundedFact("\"Fake Quote $it\"", sourceIndex = 0, isDirectQuote = true) 
        }
        val sources = listOf(Source(title = "S1", url = "http://s1.com", snippet = "Nothing here."))
        
        val result = verifier.verify(facts, sources)
        
        assertEquals(AlgorithmicGroundingVerifier.MAX_TOTAL_PENALTY, result.totalConfidencePenalty)
    }
}
