package com.najmi.corvus.domain.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Parses and highlights attribution phrases in explanations.
 * Enhancement 7 — Attribution Typography
 */
object AttributionFormatter {

    // Phrases that signal attribution to a specific source
    private val ATTRIBUTION_PHRASES = listOf(
        // English
        "According to ", "According to data from ",
        "Official ", "As confirmed by ",
        "Multiple verified", "Multiple sources",
        "Bernama reports", "Bernama, Malaysia's",
        "DOSM data", "Department of Statistics",
        "Hansard transcripts", "Official Hansard",
        "WHO ", "World Health Organization",
        "World Bank ", "IMF data",
        "Prime Minister's Office",
        "No verified source", "No official source",
        "Peer-reviewed", "Published studies",
        // BM
        "Menurut ", "Berdasarkan ",
        "Bernama melaporkan", "Data DOSM",
        "Rekod Hansard", "Sumber rasmi",
        "Pelbagai sumber", "Tiada sumber"
    )

    fun format(
        explanation: String,
        accentColor: Color,
        baseColor: Color
    ): AnnotatedString {
        val builder = AnnotatedString.Builder()
        builder.pushStyle(SpanStyle(color = baseColor))
        builder.append(explanation)
        builder.pop()

        // Find and highlight all attribution phrases
        ATTRIBUTION_PHRASES.forEach { phrase ->
            var startIndex = 0
            while (true) {
                val index = explanation.indexOf(phrase, startIndex, ignoreCase = true)
                if (index == -1) break

                builder.addStyle(
                    style = SpanStyle(
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold // SemiBold for better visibility
                    ),
                    start = index,
                    end = index + phrase.length
                )
                startIndex = index + phrase.length
            }
        }

        return builder.toAnnotatedString()
    }
}
