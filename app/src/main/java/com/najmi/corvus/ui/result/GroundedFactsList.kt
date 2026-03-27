package com.najmi.corvus.ui.result

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najmi.corvus.domain.model.*
import com.najmi.corvus.ui.components.*
import com.najmi.corvus.ui.theme.*

@Composable
fun GroundedFactsList(
    facts: List<GroundedFact>,
    sources: List<Source>,
    onSourceClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = CorvusTheme.colors.sectionFacts.copy(alpha = 0.05f),
                shape = CorvusShapes.medium
            )
            .border(
                border = BorderStroke(1.dp, CorvusTheme.colors.sectionFacts.copy(alpha = 0.1f)),
                shape = CorvusShapes.medium
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "KEY FACTS",
            style = MaterialTheme.typography.labelLarge.copy(
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        facts.forEach { fact ->
            GroundedFactRow(
                fact = fact,
                source = fact.sourceIndex?.let { sources.getOrNull(it) },
                onCitationClick = { fact.sourceIndex?.let { onSourceClick(it) } }
            )
        }
    }
}

@Composable
fun GroundedFactRow(
    fact: GroundedFact,
    source: Source?,
    onCitationClick: () -> Unit,
    leftBarColor: Color? = null
) {
    var isExpanded by remember { mutableStateOf(false) }
    val hasDetails = fact.verification?.matchedFragment != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hasDetails) { isExpanded = !isExpanded }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Left bar indicator — color based on verification
        val barColor = fact.verification?.confidence?.barColor()
            ?: leftBarColor
            ?: if (fact.sourceIndex != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
               else MaterialTheme.colorScheme.outlineVariant

        Box(
            modifier = Modifier
                .width(4.dp)
                .height(intrinsicSize = IntrinsicSize.Min)
                .padding(vertical = 2.dp)
                .background(
                    color = barColor,
                    shape = RoundedCornerShape(2.dp)
                )
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (fact.isDirectQuote) "\"${fact.statement}\"" else fact.statement,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontStyle = if (fact.isDirectQuote) FontStyle.Italic else FontStyle.Normal,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (fact.sourceIndex != null && source != null) {
                    CitationBadge(
                        index = fact.sourceIndex,
                        publisherName = source.publisher ?: "Source ${fact.sourceIndex + 1}",
                        onClick = onCitationClick
                    )
                } else {
                    Text(
                        text = "General knowledge",
                        style = MaterialTheme.typography.bodySmall,
                        color = CorvusTextTertiary,
                        fontStyle = FontStyle.Italic
                    )
                }

                // Verification confidence badge
                fact.verification?.let { v ->
                    CitationConfidenceBadge(v.confidence)
                }

                if (hasDetails) {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle details",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                }
            }

            // Matched fragment context
            AnimatedVisibility(
                visible = isExpanded && hasDetails,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                fact.verification?.matchedFragment?.let { fragment ->
                    Spacer(Modifier.height(8.dp))
                    MatchedFragmentCard(fragment)
                }
            }
        }
    }
}

@Composable
fun CitationBadge(
    index: Int,
    publisherName: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "[${index + 1}]",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )

            Text(
                text = publisherName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                maxLines = 1
            )

            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Go to source",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        }
    }
}
