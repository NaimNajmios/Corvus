package com.najmi.corvus.data.util

import com.najmi.corvus.domain.model.HistorySummary

data class RedundantGroup(
    val original: HistorySummary,
    val duplicates: List<HistorySummary>
)

object RedundantQueryDetector {
    
    private const val DEFAULT_SIMILARITY_THRESHOLD = 0.85f

    fun findRedundantQueries(
        items: List<HistorySummary>,
        threshold: Float = DEFAULT_SIMILARITY_THRESHOLD
    ): List<RedundantGroup> {
        if (items.size < 2) return emptyList()
        
        val sortedByTime = items.sortedByDescending { it.checkedAt }
        val processed = mutableSetOf<String>()
        val redundantGroups = mutableListOf<RedundantGroup>()
        
        for (i in sortedByTime.indices) {
            val current = sortedByTime[i]
            if (current.id in processed) continue
            
            val duplicates = mutableListOf<HistorySummary>()
            
            for (j in (i + 1) until sortedByTime.size) {
                val other = sortedByTime[j]
                if (other.id in processed) continue
                
                val similarity = calculateSimilarity(current.claim, other.claim)
                if (similarity >= threshold) {
                    duplicates.add(other)
                    processed.add(other.id)
                }
            }
            
            if (duplicates.isNotEmpty()) {
                processed.add(current.id)
                redundantGroups.add(RedundantGroup(
                    original = current,
                    duplicates = duplicates.sortedByDescending { it.checkedAt }
                ))
            }
        }
        
        return redundantGroups.sortedByDescending { it.duplicates.size }
    }

    fun findExactDuplicates(
        items: List<HistorySummary>
    ): List<RedundantGroup> {
        if (items.size < 2) return emptyList()
        
        val sortedByTime = items.sortedByDescending { it.checkedAt }
        val processed = mutableSetOf<String>()
        val redundantGroups = mutableListOf<RedundantGroup>()
        val groupedByClaim = sortedByTime.groupBy { it.claim.lowercase().trim() }
        
        for ((_, group) in groupedByClaim) {
            if (group.size < 2) continue
            
            val original = group.first()
            if (original.id in processed) continue
            
            val duplicates = group.drop(1).filter { it.id !in processed }
            if (duplicates.isNotEmpty()) {
                processed.add(original.id)
                duplicates.forEach { processed.add(it.id) }
                redundantGroups.add(RedundantGroup(
                    original = original,
                    duplicates = duplicates.sortedByDescending { it.checkedAt }
                ))
            }
        }
        
        return redundantGroups.sortedByDescending { it.duplicates.size }
    }

    private fun calculateSimilarity(str1: String, str2: String): Float {
        val s1 = str1.lowercase().trim()
        val s2 = str2.lowercase().trim()
        
        if (s1 == s2) return 1.0f
        
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f
        
        val maxLength = maxOf(s1.length, s2.length)
        if (maxLength == 0) return 1.0f
        
        val distance = levenshteinDistance(s1, s2)
        return 1.0f - (distance.toFloat() / maxLength)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1) { it }
        
        for (i in 1..s1.length) {
            var lastValue = i
            for (j in 1..s2.length) {
                val newValue = if (s1[i - 1] == s2[j - 1]) {
                    costs[j - 1]
                } else {
                    minOf(
                        costs[j - 1] + 1,
                        lastValue + 1,
                        costs[j] + 1
                    )
                }
                costs[j - 1] = lastValue
                lastValue = newValue
            }
            costs[s2.length] = lastValue
        }
        
        return costs[s2.length]
    }

    fun getAllRedundantItems(groups: List<RedundantGroup>): List<HistorySummary> {
        return groups.flatMap { it.duplicates }
    }

    fun getTotalRedundantCount(groups: List<RedundantGroup>): Int {
        return groups.sumOf { it.duplicates.size }
    }
}
