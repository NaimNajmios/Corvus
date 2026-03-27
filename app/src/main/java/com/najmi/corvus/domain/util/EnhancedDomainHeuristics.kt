package com.najmi.corvus.domain.util

import com.najmi.corvus.domain.model.*

object EnhancedDomainHeuristics {

    data class DomainSignals(
        val credibility     : Int,
        val sourceType      : SourceType,
        val credibilityTier : CredibilityTier,
        val flags           : Set<CredibilityFlag>,
        val confidence      : Float   // How confident we are in this heuristic
    )

    fun analyse(domain: String): DomainSignals {
        val lower = domain.lowercase()

        return when {
            // ── Official government / institutions ─────────────
            lower.endsWith(".gov.my")  -> DomainSignals(88, SourceType.GOVERNMENT_DATA, CredibilityTier.PRIMARY, setOf(CredibilityFlag.PRIMARY_SOURCE, CredibilityFlag.GOVERNMENT_AFFILIATED), 0.90f)
            lower.endsWith(".gov")     -> DomainSignals(84, SourceType.GOVERNMENT_DATA, CredibilityTier.PRIMARY, setOf(CredibilityFlag.PRIMARY_SOURCE, CredibilityFlag.GOVERNMENT_AFFILIATED), 0.88f)
            lower.endsWith(".edu.my")  -> DomainSignals(82, SourceType.ACADEMIC, CredibilityTier.VERIFIED, setOf(), 0.82f)
            lower.endsWith(".edu")     -> DomainSignals(80, SourceType.ACADEMIC, CredibilityTier.VERIFIED, setOf(), 0.80f)
            lower.contains("parlimen") -> DomainSignals(92, SourceType.OFFICIAL_TRANSCRIPT, CredibilityTier.PRIMARY, setOf(CredibilityFlag.PRIMARY_SOURCE), 0.95f)
            lower.contains("pmo.gov")  -> DomainSignals(88, SourceType.OFFICIAL_TRANSCRIPT, CredibilityTier.PRIMARY, setOf(CredibilityFlag.PRIMARY_SOURCE, CredibilityFlag.GOVERNMENT_AFFILIATED), 0.90f)
            lower.contains("who.int")  -> DomainSignals(90, SourceType.ACADEMIC, CredibilityTier.PRIMARY, setOf(CredibilityFlag.STRONG_SCIENTIFIC_REPORTING, CredibilityFlag.PRIMARY_SOURCE), 0.92f)
            lower.contains("ncbi.nlm") -> DomainSignals(92, SourceType.ACADEMIC, CredibilityTier.PRIMARY, setOf(CredibilityFlag.STRONG_SCIENTIFIC_REPORTING, CredibilityFlag.PRIMARY_SOURCE), 0.94f)
            lower.contains("worldbank")-> DomainSignals(86, SourceType.GOVERNMENT_DATA, CredibilityTier.PRIMARY, setOf(CredibilityFlag.PRIMARY_SOURCE), 0.88f)

            // ── Known news wire services ────────────────────────
            lower.contains("reuters")  -> DomainSignals(90, SourceType.NEWS_ARCHIVE, CredibilityTier.VERIFIED, setOf(), 0.88f)
            lower.contains("apnews")   -> DomainSignals(90, SourceType.NEWS_ARCHIVE, CredibilityTier.VERIFIED, setOf(), 0.88f)
            lower.contains("afp.com")  -> DomainSignals(88, SourceType.NEWS_ARCHIVE, CredibilityTier.VERIFIED, setOf(), 0.85f)

            // ── User-generated / social ─────────────────────────
            lower.contains("facebook") ||
            lower.contains("twitter")  ||
            lower.contains("tiktok")   ||
            lower.contains("instagram")||
            lower.contains("whatsapp") -> DomainSignals(15, SourceType.WEB_SEARCH, CredibilityTier.GENERAL, setOf(CredibilityFlag.USER_GENERATED), 0.95f)

            // ── Known low-quality patterns ──────────────────────
            lower.contains("wordpress")||
            lower.contains("blogspot") ||
            lower.contains("tumblr")   ||
            lower.contains("medium.com")-> DomainSignals(28, SourceType.WEB_SEARCH, CredibilityTier.GENERAL, setOf(CredibilityFlag.USER_GENERATED), 0.75f)

            lower.contains("wiki") &&
            !lower.contains("wikipedia")-> DomainSignals(35, SourceType.WEB_SEARCH, CredibilityTier.GENERAL, setOf(CredibilityFlag.USER_GENERATED), 0.65f)

            // ── Fact-checkers ───────────────────────────────────
            lower.contains("snopes")   -> DomainSignals(88, SourceType.FACT_CHECK_DB, CredibilityTier.VERIFIED, setOf(CredibilityFlag.FACT_CHECKER), 0.90f)
            lower.contains("fullfact") -> DomainSignals(87, SourceType.FACT_CHECK_DB, CredibilityTier.VERIFIED, setOf(CredibilityFlag.FACT_CHECKER), 0.88f)
            lower.contains("sebenarnya")-> DomainSignals(88, SourceType.FACT_CHECK_DB, CredibilityTier.VERIFIED, setOf(CredibilityFlag.FACT_CHECKER, CredibilityFlag.GOVERNMENT_AFFILIATED), 0.92f)

            // ── Default unknown ─────────────────────────────────
            else -> DomainSignals(50, SourceType.WEB_SEARCH, CredibilityTier.GENERAL, setOf(), 0.20f)
        }
    }
}
