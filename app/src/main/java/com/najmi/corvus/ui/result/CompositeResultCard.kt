package com.najmi.corvus.ui.result

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.SubClaim
import com.najmi.corvus.ui.components.ConfidenceBar
import com.najmi.corvus.ui.theme.CorvusShapes

@Composable
fun CompositeResultCard(
    result: CorvusCheckResult.CompositeResult,
    modifier: Modifier = Modifier,
    onSourceClick: (Int) -> Unit = {}
) {
    val verdictColor = getVerdictColor(result.compositeVerdict)
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, CorvusShapes.medium)
            .border(
                width = 3.dp,
                color = verdictColor,
                shape = CorvusShapes.medium
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                LargeVerdictBadge(verdict = result.compositeVerdict)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Compound claim • ${result.subClaims.size} sub-claims",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            TypeBadge(ClaimType.GENERAL) 
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Avg. Confidence:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${(result.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary
            )
        }

        ConfidenceBar(confidence = result.confidence)

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        result.subClaims.forEachIndexed { index, subClaim ->
            SubClaimRow(
                index = index + 1,
                subClaim = subClaim,
                onSourceClick = onSourceClick
            )
            if (index < result.subClaims.lastIndex) {
                 HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }
        }

    }
}

@Composable
fun SubClaimRow(
    index: Int,
    subClaim: SubClaim,
    onSourceClick: (Int) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = subClaim.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            subClaim.result?.let { res ->
                when (res) {
                    is CorvusCheckResult.GeneralResult -> VerdictBadge(verdict = res.verdict)
                    is CorvusCheckResult.QuoteResult -> QuoteVerdictBadge(verdict = res.quoteVerdict)
                    else -> {}
                }
            }
        }

        if (expanded && subClaim.result != null) {
            SubClaimDetail(
                result = subClaim.result,
                onSourceClick = onSourceClick
            )
        }
    }
}

@Composable
fun SubClaimDetail(
    result: CorvusCheckResult,
    onSourceClick: (Int) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .padding(start = 28.dp, top = 8.dp, bottom = 8.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
                text = "• ${(result.confidence * 100).toInt()}% confidence",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )

        val explanation = when (result) {
            is CorvusCheckResult.GeneralResult -> result.explanation
            is CorvusCheckResult.QuoteResult -> result.contextExplanation
            else -> ""
        }

        Text(
            text = explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Added source citations for subclaims
        if (result.sources.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Sources:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    result.sources.take(3).forEachIndexed { sourceIndex, source ->
                        CitationBadge(
                            index = sourceIndex,
                            publisherName = source.publisher ?: "Source",
                            onClick = { onSourceClick(sourceIndex) }
                        )
                    }
                }
            }
        }
    }
}
