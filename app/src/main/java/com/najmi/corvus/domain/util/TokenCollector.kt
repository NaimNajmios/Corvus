package com.najmi.corvus.domain.util

import com.najmi.corvus.domain.model.CheckTokenReport
import com.najmi.corvus.domain.model.TokenUsage
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenCollector @Inject constructor() {
    
    // Thread-safe list to collect usage from concurrent pipeline steps if any
    private val usages = Collections.synchronizedList(mutableListOf<TokenUsage>())

    fun collect(usage: TokenUsage) {
        if (usage == TokenUsage.EMPTY) return
        usages.add(usage)
    }

    fun generateReport(): CheckTokenReport {
        val currentUsages = usages.toList()
        return CheckTokenReport(
            totalPrompt = currentUsages.sumOf { it.promptTokens },
            totalCompletion = currentUsages.sumOf { it.completionTokens },
            totalCombined = currentUsages.sumOf { it.totalTokens },
            breakdown = currentUsages
        )
    }

    fun clear() {
        usages.clear()
    }
}
