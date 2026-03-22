package com.najmi.corvus.domain.util

import com.najmi.corvus.domain.model.HarmCategory

object HarmPreScreener {

    private val HIGH_HARM_PATTERNS = mapOf(
        HarmCategory.HEALTH to listOf(
            "cures", "bleach", "mms", "miracle mineral", "prevents cancer",
            "vaccine causes", "do not vaccinate", "ivermectin cures",
            "drink", "inject", "overdose", "self-medicate"
        ),
        HarmCategory.RACIAL_ETHNIC to listOf(
            "race war", "ethnic cleansing", "all [race]", "blame the",
            "pendatang", "kafir harus", "kaum cina", "kaum melayu"
        ),
        HarmCategory.POLITICAL to listOf(
            "take to the streets", "revolt", "armed resistance",
            "overthrow", "pilihan raya dicuri", "undi dicuri"
        ),
        HarmCategory.FINANCIAL to listOf(
            "guaranteed returns", "double your money", "amanah hartanah",
            "pelaburan tanpa risiko", "forex guaranteed"
        )
    )

    fun preScreen(claim: String): HarmCategory? {
        val lower = claim.lowercase()
        return HIGH_HARM_PATTERNS.entries.firstOrNull { (_, keywords) ->
            keywords.any { keyword -> lower.contains(keyword) }
        }?.key
    }
}
