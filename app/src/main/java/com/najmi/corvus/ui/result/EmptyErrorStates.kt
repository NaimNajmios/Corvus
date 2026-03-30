package com.najmi.corvus.ui.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najmi.corvus.domain.model.NoSourceReason
import com.najmi.corvus.domain.model.HelpRequestType
import com.najmi.corvus.domain.model.HelpRequestInfo
import com.najmi.corvus.ui.theme.*

@Composable
fun NoSourcesFoundCard(
    reasons: List<NoSourceReason>,
    onRequestHumanReview: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.5.dp, VerdictMisleading),
        shape = CorvusShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = VerdictMisleading.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.SearchOff,
                    modifier = Modifier.size(20.dp),
                    contentDescription = null,
                    tint = VerdictMisleading
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "NO RELIABLE SOURCES FOUND",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = VerdictMisleading
                    )
                    Text(
                        text = "We couldn't find reliable sources for this claim. This often happens with:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CorvusTextSecondary
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (reasons.contains(NoSourceReason.BREAKING_NEWS) || reasons.isEmpty()) {
                    ReasonItem("Breaking news - events are still developing")
                }
                if (reasons.contains(NoSourceReason.RECENT_EVENT) || reasons.isEmpty()) {
                    ReasonItem("Very recent events - not yet covered by major sources")
                }
                if (reasons.contains(NoSourceReason.NICHE_TOPIC) || reasons.isEmpty()) {
                    ReasonItem("Highly niche topics with limited coverage")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onRequestHumanReview,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VerdictMisleading,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = CorvusShapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.PersonSearch,
                    modifier = Modifier.size(18.dp),
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Request Human Review")
            }
        }
    }
}

@Composable
private fun ReasonItem(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Circle,
            modifier = Modifier.size(6.dp),
            contentDescription = null,
            tint = VerdictMisleading.copy(alpha = 0.6f)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = CorvusTextSecondary
        )
    }
}

@Composable
fun ApiFailureCard(
    errorMessage: String,
    partialResultsAvailable: Boolean,
    onRetry: () -> Unit,
    onNotifyWhenComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.5.dp, VerdictFalse),
        shape = CorvusShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = VerdictFalse.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    modifier = Modifier.size(20.dp),
                    contentDescription = null,
                    tint = VerdictFalse
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "VERIFICATION PAUSED",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = VerdictFalse
                    )
                    Text(
                        text = "Results so far saved.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CorvusTextSecondary
                    )
                    if (partialResultsAvailable) {
                        Text(
                            text = "You can view partial results below while we retry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CorvusTextTertiary
                        )
                    }
                }
            }

            if (errorMessage.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = CorvusShapes.small,
                    border = BorderStroke(1.dp, CorvusBorder.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = CorvusTextTertiary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = VerdictFalse
                    ),
                    border = BorderStroke(1.dp, VerdictFalse.copy(alpha = 0.5f)),
                    shape = CorvusShapes.small,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        modifier = Modifier.size(16.dp),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry")
                }

                Button(
                    onClick = onNotifyWhenComplete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = CorvusShapes.small,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        modifier = Modifier.size(16.dp),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Notify Me")
                }
            }
        }
    }
}

@Composable
fun LowConfidenceCard(
    currentConfidence: Float,
    knownFactors: List<String>,
    helpRequests: List<HelpRequestInfo>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.5.dp, VerdictMisleading),
        shape = CorvusShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = VerdictMisleading.copy(alpha = 0.05f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        modifier = Modifier.size(20.dp),
                        contentDescription = null,
                        tint = VerdictMisleading
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "DIFFICULT TO VERIFY",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = VerdictMisleading
                        )
                        Text(
                            text = "This claim is difficult to verify. Here's what we know:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CorvusTextSecondary
                        )
                    }
                }

                Surface(
                    color = VerdictMisleading.copy(alpha = 0.15f),
                    shape = CorvusShapes.small
                ) {
                    Text(
                        text = "${(currentConfidence * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = VerdictMisleading,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            if (knownFactors.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    knownFactors.forEach { factor ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                modifier = Modifier.size(12.dp),
                                contentDescription = null,
                                tint = VerdictMisleading.copy(alpha = 0.7f)
                            )
                            Text(
                                text = factor,
                                style = MaterialTheme.typography.bodySmall,
                                color = CorvusTextSecondary
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                onClick = { expanded = !expanded },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                shape = CorvusShapes.small,
                border = BorderStroke(1.dp, CorvusBorder.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            modifier = Modifier.size(16.dp),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "What would help us verify this?",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        modifier = Modifier.size(20.dp),
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    val displayHelpRequests = if (helpRequests.isEmpty()) {
                        listOf(
                            HelpRequestInfo(HelpRequestType.MORE_RECENT_SOURCES, "More recent sources from the past few weeks"),
                            HelpRequestInfo(HelpRequestType.PRIMARY_SOURCES, "Primary sources or official statements"),
                            HelpRequestInfo(HelpRequestType.ADDITIONAL_CONTEXT, "Additional context about the claim")
                        )
                    } else {
                        helpRequests
                    }

                    displayHelpRequests.forEach { helpRequest ->
                        HelpRequestItem(helpRequest)
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpRequestItem(helpRequest: HelpRequestInfo) {
    val icon = when (helpRequest.type) {
        HelpRequestType.MORE_RECENT_SOURCES -> Icons.Default.AccessTime
        HelpRequestType.PRIMARY_SOURCES -> Icons.Default.Article
        HelpRequestType.OFFICIAL_STATEMENTS -> Icons.Default.Gavel
        HelpRequestType.PEER_REVIEWED -> Icons.Default.School
        HelpRequestType.ADDITIONAL_CONTEXT -> Icons.Default.MoreHoriz
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            modifier = Modifier.size(16.dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        )
        Text(
            text = helpRequest.description,
            style = MaterialTheme.typography.bodySmall,
            color = CorvusTextSecondary
        )
    }
}
