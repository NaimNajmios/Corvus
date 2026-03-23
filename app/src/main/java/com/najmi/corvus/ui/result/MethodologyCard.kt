package com.najmi.corvus.ui.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
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

@Composable
fun MethodologyCard(metadata: MethodologyMetadata?) {
    if (metadata == null) return
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
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Pipeline steps
                    Text(
                        text = "Pipeline steps completed:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    metadata.pipelineStepsCompleted.forEach { step ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft, // Placeholder for check
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = VerdictTrue
                            )
                            Text(
                                text = step.step.displayLabel().padEnd(25),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "— ${step.outcome}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Summary stats
                    MethodologyStatRow("Claim type", metadata.claimTypeDetected.displayLabel())
                    
                    if (metadata.sourcesRetrieved > 0) {
                        MethodologyStatRow("Sources retrieved", "${metadata.sourcesRetrieved}")
                    }
                    
                    if (metadata.avgSourceCredibility > 0) {
                        MethodologyStatRow("Avg. credibility", "${metadata.avgSourceCredibility}%")
                    }
                    
                    if (metadata.llmProviderUsed.isNotBlank() && metadata.llmProviderUsed != "unknown") {
                        MethodologyStatRow("Analysis provider", metadata.llmProviderUsed)
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
                }
            }
        }
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
