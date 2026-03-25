package com.najmi.corvus.data.remote.llm

import com.najmi.corvus.domain.model.LlmProvider
import com.najmi.corvus.domain.model.CredibilityTier
import com.najmi.corvus.domain.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceContextBuilderTest {

    private val builder = SourceContextBuilder()

    @Test
    fun testTrimToSentenceBoundary() {
        val text1 = "Short text."
        assertEquals("Short text.", builder.trimToSentenceBoundary(text1, 50))

        val text2 = "This is a sentence. And another one. But we cut here. Extra stuff."
        val res2 = builder.trimToSentenceBoundary(text2, 57)
        assertEquals("This is a sentence. And another one. But we cut here.", res2.trim())

        val text3 = "A very long sentence without any punctuation that just keeps going and going until it gets cut off"
        val res3 = builder.trimToSentenceBoundary(text3, 50)
        assertTrue(res3.endsWith("..."))
        assertTrue(res3.length <= 53)
        
        val text4 = "Sentence one. Sentence two."
        assertEquals(text4, builder.trimToSentenceBoundary(text4, text4.length))
        
        val text5 = "What is this? An exclamation! Oh yes."
        val res5 = builder.trimToSentenceBoundary(text5, 30)
        assertEquals("What is this? An exclamation!", res5.trim())
    }

    @Test
    fun testSortByCredibilityTier() {
        val s1 = Source(title = "General", url = "", credibilityTier = CredibilityTier.GENERAL, snippet = "text")
        val s2 = Source(title = "Primary", url = "", credibilityTier = CredibilityTier.PRIMARY, snippet = "text")
        val s3 = Source(title = "Unknown", url = "", credibilityTier = CredibilityTier.UNKNOWN, snippet = "text")
        val s4 = Source(title = "Verified", url = "", credibilityTier = CredibilityTier.VERIFIED, snippet = "text")
        
        val context = builder.build(listOf(s1, s2, s3, s4), LlmProvider.CEREBRAS)
        
        val primaryIdx = context.indexOf("Title     : Primary")
        val verifiedIdx = context.indexOf("Title     : Verified")
        val generalIdx = context.indexOf("Title     : General")
        val unknownIdx = context.indexOf("Title     : Unknown")
        
        assertTrue(primaryIdx < verifiedIdx)
        assertTrue(verifiedIdx < generalIdx)
        assertTrue(generalIdx < unknownIdx)
    }

    @Test
    fun testMaxTotalContextLimit() {
        val longText = "A".repeat(5000)
        val sources = listOf(
            Source(title = "1", url = "", snippet = longText),
            Source(title = "2", url = "", snippet = longText)
        )
        
        val context = builder.build(sources, LlmProvider.CEREBRAS)
        assertTrue(context.length <= 4000)
        assertTrue(context.contains("Title     : 1"))
    }
}
