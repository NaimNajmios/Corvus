package com.najmi.corvus.data.remote.llm

import com.najmi.corvus.domain.model.LlmProvider
import com.najmi.corvus.domain.model.Source
import javax.inject.Inject

class SourceContextBuilder @Inject constructor() {

    private val SNIPPET_LENGTH_BY_PROVIDER = mapOf(
        LlmProvider.GEMINI        to 3000,
        LlmProvider.GROQ          to 2500,
        LlmProvider.CEREBRAS      to 800,
        LlmProvider.OPENROUTER    to 2000,
        LlmProvider.MISTRAL_SABA  to 1200,
        LlmProvider.MISTRAL_SMALL to 2500,
        LlmProvider.COHERE_R      to 2500,
        LlmProvider.COHERE_R_PLUS to 2500
    )

    private val MAX_TOTAL_CONTEXT_BY_PROVIDER = mapOf(
        LlmProvider.GEMINI        to 15_000,
        LlmProvider.GROQ          to 12_000,
        LlmProvider.CEREBRAS      to 4_000,
        LlmProvider.OPENROUTER    to 8_000,
        LlmProvider.MISTRAL_SABA  to 6_000,
        LlmProvider.MISTRAL_SMALL to 12_000,
        LlmProvider.COHERE_R      to 12_000,
        LlmProvider.COHERE_R_PLUS to 12_000
    )

    fun build(
        sources: List<Source>,
        provider: LlmProvider
    ): String {
        val snippetLength = getSnippetLength(provider)
        val maxTotal = MAX_TOTAL_CONTEXT_BY_PROVIDER[provider] ?: 8_000

        // Sort by credibility — most reliable sources first in context window (PRIMARY has ordinal 0)
        val sorted = sources.sortedBy {
            it.credibilityTier.ordinal
        }

        val builder = StringBuilder()
        var totalChars = 0

        sorted.forEachIndexed { index, source ->
            if (totalChars >= maxTotal) return@forEachIndexed

            // Prefer rawContent (full article), fall back to snippet
            val contentText = (source.rawContent ?: source.snippet ?: "")
                .take(snippetLength)

            // Trim to whole sentence boundary where possible
            val trimmed = trimToSentenceBoundary(contentText, snippetLength)

            val entry = buildString {
                appendLine("--- SOURCE [$index] ---")
                appendLine("Publisher : ${source.publisher ?: "Unknown"}")
                appendLine("Title     : ${source.title}")
                source.publishedDate?.let { appendLine("Date      : $it") }
                appendLine("URL       : ${source.url}")
                appendLine("Credibility: ${source.credibilityTier.name}")
                appendLine()
                appendLine("CONTENT:")
                appendLine(trimmed)
                appendLine()
            }

            if (totalChars + entry.length <= maxTotal) {
                builder.append(entry)
                totalChars += entry.length
            }
        }

        return builder.toString()
    }

    // Trim to nearest sentence end to avoid mid-sentence cuts
    fun trimToSentenceBoundary(text: String, maxLength: Int): String {
        if (text.length <= maxLength) return text

        val truncated = text.take(maxLength)
        val lastSentenceEnd = truncated.lastIndexOfAny(charArrayOf('.', '!', '?'))

        return if (lastSentenceEnd > maxLength * 0.7) {
            // Found a sentence boundary in the last 30% — use it
            truncated.take(lastSentenceEnd + 1)
        } else {
            // No good boundary — use word boundary instead
            val lastSpace = truncated.lastIndexOf(' ')
            truncated.take(if (lastSpace > 0) lastSpace else maxLength) + "..."
        }
    }

    private fun getSnippetLength(provider: LlmProvider) =
        SNIPPET_LENGTH_BY_PROVIDER[provider] ?: 1500
}
