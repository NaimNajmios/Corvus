package com.najmi.corvus.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najmi.corvus.domain.model.TemporalMismatchReport
import com.najmi.corvus.domain.model.RecencySignal
import com.najmi.corvus.ui.theme.*

@Composable
fun TemporalContextBanner(
    mismatchReport: TemporalMismatchReport?,
    recencySignal: RecencySignal?,
    modifier: Modifier = Modifier,
    onLearnMore: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    val hasZombieWarning = mismatchReport?.hasSignificantMismatch == true
    val isBreakingNews = recencySignal == RecencySignal.BREAKING

    if (!hasZombieWarning && !isBreakingNews && mismatchReport == null) return

    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(
            1.5.dp,
            when {
                isBreakingNews -> VerdictPartiallyTrue
                hasZombieWarning -> VerdictMisleading
                else -> VerdictMisleading.copy(alpha = 0.5f)
            }
        ),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isBreakingNews -> VerdictPartiallyTrue.copy(alpha = 0.08f)
                hasZombieWarning -> VerdictMisleading.copy(alpha = 0.08f)
                else -> VerdictMisleading.copy(alpha = 0.05f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = when {
                        isBreakingNews -> Icons.Default.Newspaper
                        hasZombieWarning -> Icons.Default.History
                        else -> Icons.Default.Schedule
                    },
                    modifier = Modifier
                        .size(18.dp)
                        .padding(top = 2.dp),
                    contentDescription = "Temporal warning",
                    tint = when {
                        isBreakingNews -> VerdictPartiallyTrue
                        hasZombieWarning -> VerdictMisleading
                        else -> VerdictMisleading
                    }
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isBreakingNews -> "BREAKING NEWS"
                            hasZombieWarning -> "TEMPORAL MISMATCH DETECTED"
                            else -> "TEMPORAL CONTEXT"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = when {
                            isBreakingNews -> VerdictPartiallyTrue
                            hasZombieWarning -> VerdictMisleading
                            else -> VerdictMisleading
                        }
                    )
                    
                    if (isBreakingNews) {
                        Text(
                            text = "This claim describes a breaking news event. Evidence is still emerging and may change as more information becomes available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CorvusTextSecondary
                        )
                    } else if (hasZombieWarning) {
                        mismatchReport?.oldestSourceAge?.let { age ->
                            Text(
                                text = "This claim implies a current event, but retrieved sources are ${age / 30} month(s) old. This may be a recycled claim presented as recent news.",
                                style = MaterialTheme.typography.bodySmall,
                                color = CorvusTextSecondary
                            )
                        }
                    }
                }

                Surface(
                    onClick = { expanded = !expanded },
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    shape = CorvusShapes.extraSmall
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Details",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            modifier = Modifier.size(14.dp),
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    HorizontalDivider(
                        color = CorvusBorder.copy(alpha = 0.2f)
                    )

                    if (mismatchReport != null) {
                        SourceDateSummary(mismatchReport)
                    }

                    if (recencySignal != null) {
                        RecencySignalDisplay(recencySignal)
                    }

                    if (hasZombieWarning) {
                        ZombieExplanation()
                    }

                    if (onLearnMore != null) {
                        TextButton(
                            onClick = onLearnMore,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                modifier = Modifier.size(14.dp),
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Learn about zombie hoaxes", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceDateSummary(report: TemporalMismatchReport) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "SOURCE DATES",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = CorvusTextTertiary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = CorvusShapes.small,
                border = BorderStroke(1.dp, CorvusBorder.copy(alpha = 0.2f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Oldest",
                        style = MaterialTheme.typography.labelSmall,
                        color = CorvusTextTertiary
                    )
                    report.oldestSourceAge?.let { age ->
                        Text(
                            text = formatAge(age),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = VerdictMisleading
                        )
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = CorvusShapes.small,
                border = BorderStroke(1.dp, CorvusBorder.copy(alpha = 0.2f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Newest",
                        style = MaterialTheme.typography.labelSmall,
                        color = CorvusTextTertiary
                    )
                    report.newestSourceAge?.let { age ->
                        Text(
                            text = formatAge(age),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = VerdictTrue
                        )
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = CorvusShapes.small,
                border = BorderStroke(1.dp, CorvusBorder.copy(alpha = 0.2f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Dated",
                        style = MaterialTheme.typography.labelSmall,
                        color = CorvusTextTertiary
                    )
                    Text(
                        text = "${report.sourcesWithDates}/${report.sourcesWithDates + report.sourcesWithoutDates}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun RecencySignalDisplay(signal: RecencySignal) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "CLAIM RECENCY:",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = CorvusTextTertiary
        )

        val (label, color) = when (signal) {
            RecencySignal.BREAKING -> "Breaking News" to VerdictPartiallyTrue
            RecencySignal.RECENT -> "Recent" to VerdictTrue
            RecencySignal.HISTORICAL -> "Historical" to VerdictUnverifiable
            RecencySignal.NOT_APPLICABLE -> "N/A" to CorvusTextTertiary
        }

        Surface(
            color = color.copy(alpha = 0.15f),
            shape = CorvusShapes.extraSmall,
            border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = color,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun ZombieExplanation() {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        shape = CorvusShapes.small
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                modifier = Modifier.size(14.dp),
                contentDescription = null,
                tint = VerdictMisleading
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "What is a zombie hoax?",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "A zombie hoax is an old false claim that resurfaces as if it were current news. These often spread during election cycles or health crises.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CorvusTextSecondary,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

private fun formatAge(days: Int): String {
    return when {
        days < 7 -> "$days days"
        days < 30 -> "${days / 7} weeks"
        days < 365 -> "${days / 30} months"
        else -> "${days / 365} years"
    }
}
