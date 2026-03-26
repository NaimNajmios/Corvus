package com.najmi.corvus.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StringSimilarityTest {

    @Test
    fun `normalizedLevenshtein returns 1 for identical strings`() {
        val a = "The quick brown fox"
        val b = "The quick brown fox"
        assertEquals(1.0f, StringSimilarity.normalizedLevenshtein(a, b), 0.001f)
    }

    @Test
    fun `normalizedLevenshtein returns 0 for completely different strings`() {
        val a = "abc"
        val b = "xyz"
        // abc -> xyz requires 3 substitutions. max length 3. distance 3. 1 - 3/3 = 0.
        assertEquals(0.0f, StringSimilarity.normalizedLevenshtein(a, b), 0.001f)
    }

    @Test
    fun `normalizedLevenshtein handles empty strings`() {
        assertEquals(0.0f, StringSimilarity.normalizedLevenshtein("", "test"), 0.001f)
        assertEquals(0.0f, StringSimilarity.normalizedLevenshtein("test", ""), 0.001f)
        assertEquals(1.0f, StringSimilarity.normalizedLevenshtein("", ""), 0.001f)
    }

    @Test
    fun `normalizedLevenshtein handles partial matches`() {
        val a = "kitten"
        val b = "sitting"
        // kitten -> sitting: 
        // 1. k -> s (sub)
        // 2. e -> i (sub)
        // 3. add g (ins)
        // distance 3. maxLength 7. 1 - 3/7 = 0.5714
        assertEquals(0.5714f, StringSimilarity.normalizedLevenshtein(a, b), 0.001f)
    }

    @Test
    fun `bestSubstringMatch finds exact match within text`() {
        val query = "hello world"
        val text = "some text hello world more text"
        assertEquals(1.0f, StringSimilarity.bestSubstringMatch(query, text), 0.001f)
    }

    @Test
    fun `bestSubstringMatch handles case insensitivity`() {
        val query = "HELLO WORLD"
        val text = "some text hello world more text"
        // The implementation uses lowercase() internally
        assertEquals(1.0f, StringSimilarity.bestSubstringMatch(query, text), 0.001f)
    }

    @Test
    fun `bestSubstringMatch finding approximate match`() {
        val query = "hello worlx"
        val text = "some text hello world more text"
        val score = StringSimilarity.bestSubstringMatch(query, text)
        // "hello worlx" vs "hello world" -> 1 char diff. 1 - 1/11 = 10/11 = 0.909
        assertTrue(score > 0.9f)
    }
}
