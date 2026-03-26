package com.najmi.corvus.domain.util

import com.najmi.corvus.domain.model.DateConfidence
import com.najmi.corvus.domain.model.PublicationDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object PublicationDateExtractor {

    private val ISO_DATE_PATTERN = Regex("""(\d{4})-(\d{2})-(\d{2})""")
    private val DATE_PATTERN_1 = Regex("""(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+(\d{4})""", RegexOption.IGNORE_CASE)
    private val DATE_PATTERN_2 = Regex("""(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+(\d{1,2}),?\s+(\d{4})""", RegexOption.IGNORE_CASE)
    private val MONTH_YEAR_PATTERN = Regex("""(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+(\d{4})""", RegexOption.IGNORE_CASE)
    private val YEAR_ONLY_PATTERN = Regex("""\b(20\d{2})\b""")

    fun extract(rawDateString: String?): PublicationDate? {
        if (rawDateString.isNullOrBlank()) return null

        val trimmed = rawDateString.trim()

        ISO_DATE_PATTERN.find(trimmed)?.let { match ->
            return parseIsoDate(trimmed, match)
        }

        DATE_PATTERN_1.find(trimmed)?.let { match ->
            return parseArticleDate(trimmed, match.value, DateConfidence.EXACT)
        }

        DATE_PATTERN_2.find(trimmed)?.let { match ->
            return parseArticleDate(trimmed, match.value, DateConfidence.EXACT)
        }

        MONTH_YEAR_PATTERN.find(trimmed)?.let { match ->
            return parseMonthYearDate(trimmed, match.value)
        }

        YEAR_ONLY_PATTERN.find(trimmed)?.let { match ->
            val year = match.value.toIntOrNull() ?: return null
            val date = LocalDate.of(year, 6, 15)
            return PublicationDate(
                raw = trimmed,
                epochDay = date.toEpochDay(),
                confidence = DateConfidence.YEAR_ONLY,
                formattedDisplay = "~${year}"
            )
        }

        return null
    }

    fun extractFromContent(content: String): PublicationDate? {
        val header = content.take(500)
        
        listOf(ISO_DATE_PATTERN, DATE_PATTERN_1, DATE_PATTERN_2).forEach { pattern ->
            val match = pattern.find(header)
            if (match != null) {
                return extract(match.value)?.copy(confidence = DateConfidence.ESTIMATED)
            }
        }
        return null
    }

    private fun parseIsoDate(raw: String, match: MatchResult): PublicationDate? {
        return try {
            val year = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val day = match.groupValues[3].toInt()
            val date = LocalDate.of(year, month, day)
            PublicationDate(
                raw = raw,
                epochDay = date.toEpochDay(),
                confidence = DateConfidence.EXACT,
                formattedDisplay = date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseArticleDate(raw: String, matchedValue: String, confidence: DateConfidence): PublicationDate? {
        return try {
            val formatter1 = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
            val formatter2 = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
            
            val cleaned = matchedValue.replace(",".toRegex(), "").trim()
            val date = try {
                LocalDate.parse(cleaned, formatter1)
            } catch (e: DateTimeParseException) {
                LocalDate.parse(cleaned, formatter2)
            }

            PublicationDate(
                raw = raw,
                epochDay = date.toEpochDay(),
                confidence = confidence,
                formattedDisplay = date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseMonthYearDate(raw: String, matchedValue: String): PublicationDate? {
        return try {
            val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
            val monthYear = matchedValue.trim()
            val date = java.time.YearMonth.parse(monthYear, formatter).atDay(1)

            PublicationDate(
                raw = raw,
                epochDay = date.toEpochDay(),
                confidence = DateConfidence.MONTH_YEAR,
                formattedDisplay = date.format(DateTimeFormatter.ofPattern("MMM yyyy"))
            )
        } catch (e: Exception) {
            null
        }
    }
}
