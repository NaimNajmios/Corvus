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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
    modifier: Modifier = Modifier
) {
    var isRevealed by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        isRevealed = true
    }

    val scale by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0.92f,
        animationSpec = tween(380),
        label = "verdictScale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0f,
        animationSpec = tween(380),
        label = "verdictAlpha"
    )

    val verdictColor = getVerdictColor(result.compositeVerdict)
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(alpha)
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
                Text(
                    text = result.compositeVerdict.name.replace("_", " "),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 28.sp,
                        letterSpacing = 2.sp
                    ),
                    color = verdictColor
                )
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
                subClaim = subClaim
            )
            if (index < result.subClaims.lastIndex) {
                 HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }
        }

        Text(
            text = result.compositeSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun SubClaimRow(index: Int, subClaim: SubClaim) {
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
                val color = when (res) {
                    is CorvusCheckResult.GeneralResult -> getVerdictColor(res.verdict)
                    is CorvusCheckResult.QuoteResult -> getQuoteVerdictColor(res.quoteVerdict)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color, CircleShape)
                )
            }
        }

        if (expanded && subClaim.result != null) {
            SubClaimDetail(subClaim.result)
        }
    }
}

@Composable
fun SubClaimDetail(result: CorvusCheckResult) {
    Column(
        modifier = Modifier
            .padding(start = 28.dp, top = 8.dp, bottom = 8.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val verdictText = when (result) {
            is CorvusCheckResult.GeneralResult -> result.verdict.name.replace("_", " ")
            is CorvusCheckResult.QuoteResult -> result.quoteVerdict.name.replace("_", " ")
            else -> "UNKNOWN"
        }
        
        val verdictColor = when (result) {
            is CorvusCheckResult.GeneralResult -> getVerdictColor(result.verdict)
            is CorvusCheckResult.QuoteResult -> getQuoteVerdictColor(result.quoteVerdict)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = verdictText,
                style = MaterialTheme.typography.labelMedium,
                color = verdictColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "• ${(result.confidence * 100).toInt()}% confidence",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

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
    }
}
