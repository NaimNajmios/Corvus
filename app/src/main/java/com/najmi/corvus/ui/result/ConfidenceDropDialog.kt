package com.najmi.corvus.ui.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.najmi.corvus.domain.model.ConfidencePoint
import com.najmi.corvus.domain.model.Source
import com.najmi.corvus.ui.theme.*

@Composable
fun ConfidenceDropDialog(
    confidencePoint: ConfidencePoint,
    previousConfidence: Float,
    contradictingSource: Source?,
    contradictingSourceIndex: Int?,
    allSources: List<Source>,
    onDismiss: () -> Unit,
    onViewSource: (() -> Unit)? = null
) {
    val dropAmount = ((previousConfidence - confidencePoint.confidence) * 100).toInt()
    val changeReason = confidencePoint.changeReason ?: "unknown"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = CorvusShapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, VerdictFalse.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = VerdictFalse,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Confidence Dropped",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    color = VerdictFalse.copy(alpha = 0.1f),
                    shape = CorvusShapes.small
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${(previousConfidence * 100).toInt()}%",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = VerdictTrue
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = VerdictFalse,
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .size(24.dp)
                        )
                        Text(
                            text = "${(confidencePoint.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = VerdictFalse
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = VerdictFalse.copy(alpha = 0.2f),
                            shape = CorvusShapes.extraSmall
                        ) {
                            Text(
                                text = "-$dropAmount%",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = VerdictFalse,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Why did confidence drop?",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val reasonText = when (changeReason) {
                        "source_contradicted" -> "A source contradicted earlier evidence, reducing overall confidence in the claim."
                        "evidence_weakened" -> "The retrieved evidence was insufficient or unreliable."
                        "grounding_failed" -> "The analysis could not be properly grounded in the sources."
                        else -> "New information reduced our confidence in this claim."
                    }

                    Text(
                        text = reasonText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CorvusTextSecondary
                    )
                }

                contradictingSource?.let { source ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Contradicting Source",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = VerdictFalse
                        )

                        Surface(
                            border = BorderStroke(1.dp, VerdictFalse.copy(alpha = 0.3f)),
                            shape = CorvusShapes.small,
                            color = VerdictFalse.copy(alpha = 0.05f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                contradictingSourceIndex?.let { index ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CorvusShapes.extraSmall
                                    ) {
                                        Text(
                                            text = "[${index + 1}]",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = source.publisher ?: "Unknown Source",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = source.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CorvusTextSecondary,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = CorvusBorder.copy(alpha = 0.3f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun ConfidenceDropIndicator(
    confidencePoint: ConfidencePoint,
    previousConfidence: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val drop = previousConfidence - confidencePoint.confidence
    if (drop <= 0.05f) return

    val dropPercent = (drop * 100).toInt()

    Surface(
        onClick = onClick,
        modifier = modifier,
        color = VerdictFalse.copy(alpha = 0.15f),
        shape = CorvusShapes.extraSmall,
        border = BorderStroke(1.dp, VerdictFalse.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.TrendingDown,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = VerdictFalse
            )
            Text(
                text = "-$dropPercent%",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = VerdictFalse
            )
        }
    }
}
