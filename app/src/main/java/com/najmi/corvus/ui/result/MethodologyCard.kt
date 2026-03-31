package com.najmi.corvus.ui.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChangeCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.MethodologyMetadata
import com.najmi.corvus.domain.model.displayLabel
import com.najmi.corvus.ui.theme.VerdictTrue
import com.najmi.corvus.ui.theme.CorvusTheme
import com.najmi.corvus.ui.theme.CorvusShapes
import com.najmi.corvus.ui.theme.SectionMethodology
import java.text.SimpleDateFormat
import java.util.*

import com.najmi.corvus.domain.model.CorvusCheckResult

@Composable
fun MethodologyCard(result: CorvusCheckResult?) {
    if (result == null) return
    val metadata = result.methodology ?: return
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, CorvusTheme.colors.sectionMethodology.copy(alpha = 0.1f)),
        shape = CorvusShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = CorvusTheme.colors.sectionMethodology.copy(alpha = 0.05f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HOW CORVUS CHECKED THIS",
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Show less" else "Show more",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val correctionsLog = when (result) {
                        is CorvusCheckResult.GeneralResult -> result.correctionsLog
                        is CorvusCheckResult.CompositeResult -> result.correctionsLog
                        else -> null
                    }
                    val fabrications = correctionsLog?.filter { it.startsWith("Algorithmic Reject") } ?: emptyList()
                    if (fabrications.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = CorvusShapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Fabrication Warning",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "WARNING: ${fabrications.size} cited quotes could not be found in the source text and were stripped from the analysis.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    // Pipeline steps
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        metadata.pipelineStepsCompleted.forEach { step ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            CorvusTheme.colors.sectionMethodology,
                                            CircleShape
                                        )
                                )
                                Text(
                                    text = step.step.displayLabel(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(0.35f)
                                )
                                Text(
                                    text = step.outcome,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(0.65f)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Summary stats
                    MethodologyStatRow("Claim type", metadata.claimTypeDetected.displayLabel())
                    
                    // Retrieval Metadata (Query Rewriting)
                    val retrievalMetadata = when (result) {
                        is CorvusCheckResult.GeneralResult -> result.retrievalMetadata
                        is CorvusCheckResult.CompositeResult -> result.retrievalMetadata
                        else -> null
                    }
                    retrievalMetadata?.let { retrieval ->
                        if (retrieval.rewrittenQueries.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(8.dp))
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = CorvusTheme.colors.sectionMethodology
                                )
                                Text(
                                    "RETRIEVAL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(4.dp))

                            if (retrieval.coreQuestion != retrieval.originalClaim) {
                                Column {
                                    Text(
                                        "Core question:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        retrieval.coreQuestion,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                            }

                            Text(
                                "Search queries (${retrieval.rewrittenQueries.size}):",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            retrieval.rewrittenQueries.forEach { query ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "\"$query\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            MethodologyStatRow(
                                "Sources",
                                "${retrieval.totalRawSources} raw → ${retrieval.dedupedSources} unique → ${retrieval.finalSources} used"
                            )
                        }
                    }

                    if (metadata.sourcesRetrieved > 0) {
                        MethodologyStatRow("Sources retrieved", "${metadata.sourcesRetrieved}")
                    }
                    
                    if (metadata.avgSourceCredibility > 0) {
                        MethodologyStatRow("Avg. credibility", "${metadata.avgSourceCredibility}%")
                    }
                    
                    if (metadata.llmProviderUsed.isNotBlank() && metadata.llmProviderUsed != "unknown") {
                        MethodologyStatRow("Analysis provider", metadata.llmProviderUsed)
                    }
                    
                    if (metadata.routingRationale.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Route,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = CorvusTheme.colors.sectionMethodology
                            )
                            Text(
                                "ROUTING",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            metadata.routingRationale,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontStyle = FontStyle.Italic
                        )
                    }
                    
                    MethodologyStatRow("Checked at", metadata.checkedAt.toFormattedDate())

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Disclaimer
                    Text(
                        text = "Corvus uses AI analysis and may make errors. Always verify critical claims with primary sources.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 16.sp
                    )

                    val reasoningScratchpad = when (result) {
                        is CorvusCheckResult.GeneralResult -> result.reasoningScratchpad
                        is CorvusCheckResult.CompositeResult -> result.reasoningScratchpad
                        else -> null
                    }
                    reasoningScratchpad?.let { scratchpad ->
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(8.dp))

                        var showReasoning by remember { mutableStateOf(true) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showReasoning = !showReasoning }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "FULL REASONING",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "EXPANDED",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Icon(
                                if (showReasoning) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Show reasoning",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(visible = showReasoning) {
                            val reindexed = scratchpad.reindexCitations()
                            val items = parseReasoningItems(reindexed)
                            
                            Column(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items.forEachIndexed { index, item ->
                                    ReasoningItem(
                                        index = index + 1,
                                        content = item
                                    )
                                }
                            }
                        }
                    }

                    correctionsLog?.takeIf { it.isNotEmpty() }?.let { corrections ->
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "CRITIC CORRECTIONS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "${corrections.size} made",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        // Group corrections by type
                        val groupedCorrections = corrections.groupBy { correction ->
                            when {
                                correction.contains("citation", ignoreCase = true) ||
                                correction.contains("source", ignoreCase = true) ||
                                correction.contains("attribution", ignoreCase = true) -> CorrectionType.CITATION
                                correction.contains("verdict", ignoreCase = true) ||
                                correction.contains("verdict", ignoreCase = true) -> CorrectionType.VERDICT
                                correction.contains("confidence", ignoreCase = true) -> CorrectionType.CONFIDENCE
                                correction.contains("explanation", ignoreCase = true) ||
                                correction.contains("rewritten", ignoreCase = true) -> CorrectionType.EXPLANATION
                                else -> CorrectionType.OTHER
                            }
                        }

                        groupedCorrections.forEach { (type, typeCorrections) ->
                            CorrectionGroup(
                                type = type,
                                corrections = typeCorrections
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReasoningItem(
    index: Int,
    content: String
) {
    val hasCitation = content.contains(Regex("\\[\\d+\\]"))
    val cleanContent = content.replace(Regex("^\\d+\\.\\s*"), "")
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (hasCitation) {
                Text(
                    text = cleanContent,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    text = cleanContent,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

enum class CorrectionType {
    CITATION,
    VERDICT,
    CONFIDENCE,
    EXPLANATION,
    OTHER
}

@Composable
private fun CorrectionGroup(
    type: CorrectionType,
    corrections: List<String>
) {
    val (icon, color, label) = when (type) {
        CorrectionType.CITATION -> Triple(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.error,
            "Citation Errors"
        )
        CorrectionType.VERDICT -> Triple(
            Icons.Default.SwapHoriz,
            MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
            "Verdict Corrections"
        )
        CorrectionType.CONFIDENCE -> Triple(
            Icons.Default.ChangeCircle,
            MaterialTheme.colorScheme.tertiary,
            "Confidence Adjustments"
        )
        CorrectionType.EXPLANATION -> Triple(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            "Explanation Updates"
        )
        CorrectionType.OTHER -> Triple(
            Icons.AutoMirrored.Filled.ArrowForward,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Other Corrections"
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = color
            )
        }

        corrections.forEach { correction ->
            CorrectionItem(
                correction = correction,
                color = color
            )
        }
        
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CorrectionItem(
    correction: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color.copy(alpha = 0.05f),
                RoundedCornerShape(6.dp)
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .padding(top = 8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = correction,
            style = MaterialTheme.typography.bodySmall.copy(
                lineHeight = 18.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun MethodologyStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
        )
    }
}

private fun Long.toFormattedDate(): String {
    val date = Date(this)
    val format = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return format.format(date)
}

private fun String.reindexCitations(): String =
    this.replace(Regex("\\[(\\d+)\\]")) { match ->
        "[${match.groupValues[1].toInt() + 1}]"
    }

private fun parseReasoningItems(scratchpad: String): List<String> {
    val items = scratchpad.split(Regex("(?=\\d+\\.\\s)"))
    return items.filter { it.isNotBlank() }.map { it.trim() }
}