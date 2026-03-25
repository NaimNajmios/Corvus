package com.najmi.corvus.domain.util

object StringSimilarity {

    // Normalised Levenshtein similarity: 0.0 (completely different) → 1.0 (identical)
    fun normalizedLevenshtein(a: String, b: String): Float {
        if (a == b) return 1.0f
        if (a.isEmpty() || b.isEmpty()) return 0.0f

        val maxLength = maxOf(a.length, b.length)
        val distance  = levenshteinDistance(a, b)
        return 1f - (distance.toFloat() / maxLength)
    }

    // Standard Levenshtein distance — space-optimised DP
    private fun levenshteinDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length

        var previousRow = IntArray(n + 1) { it }
        var currentRow  = IntArray(n + 1)

        for (i in 1..m) {
            currentRow[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                currentRow[j] = minOf(
                    currentRow[j - 1] + 1,         // Insertion
                    previousRow[j] + 1,             // Deletion
                    previousRow[j - 1] + cost       // Substitution
                )
            }
            previousRow = currentRow.also { currentRow = previousRow }
        }

        return previousRow[n]
    }

    // Sliding window search — find best match of query within a longer text
    // Used when quote may be a substring of the source content
    fun bestSubstringMatch(query: String, text: String): Float {
        if (query.length > text.length) return 0f
        if (text.contains(query, ignoreCase = true)) return 1.0f

        // Slide query-sized window across text
        val windowSize  = query.length
        var bestScore   = 0f

        for (i in 0..(text.length - windowSize)) {
            val window = text.substring(i, i + windowSize)
            val score  = normalizedLevenshtein(
                query.lowercase(),
                window.lowercase()
            )
            if (score > bestScore) bestScore = score
            if (bestScore >= 0.95f) break   // Good enough — stop early
        }

        return bestScore
    }
}
