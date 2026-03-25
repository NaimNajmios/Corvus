package com.najmi.corvus.domain.usecase

object FactKeywordExtractor {

    // Stop words — words that appear everywhere, useless for matching
    private val STOP_WORDS = setOf(
        "the", "a", "an", "is", "was", "are", "were", "be", "been",
        "have", "has", "had", "will", "would", "could", "should",
        "in", "on", "at", "to", "of", "and", "or", "but", "with",
        "by", "from", "for", "as", "that", "this", "it", "not",
        "i", "you", "he", "she", "it", "we", "they", "me", "him", "her",
        "us", "them", "my", "your", "his", "hers", "its", "our", "their",
        // BM stop words
        "yang", "dan", "di", "ke", "dari", "pada", "oleh", "untuk",
        "dengan", "dalam", "adalah", "akan", "telah", "tidak", "ia",
        "kita", "mereka", "itu", "ini", "bagaimana", "mengapa", "apa"
    )

    fun extract(statement: String): List<String> {
        return statement
            .lowercase()
            .replace(Regex("[^a-z0-9\\s%.]"), " ")
            .split(Regex("\\s+"))
            .filter { word ->
                word.length >= 3 &&
                word !in STOP_WORDS
            }
            .distinct()
    }

    // Extract numeric values specifically — these are the most verifiable
    fun extractNumerics(statement: String): List<String> {
        return Regex("""(\d+\.?\d*\s*%?|\b\d{4}\b)""")
            .findAll(statement)
            .map { it.value.trim() }
            .toList()
    }
}
