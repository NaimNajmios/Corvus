package com.najmi.corvus.ui.result

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najmi.corvus.domain.model.ClaimType
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.HarmLevel
import com.najmi.corvus.domain.model.QuoteVerdict
import com.najmi.corvus.ui.components.ConfidenceBar
import com.najmi.corvus.ui.theme.CorvusShapes
import com.najmi.corvus.ui.theme.VerdictFalse
import com.najmi.corvus.ui.theme.VerdictMisleading

@Composable
fun QuoteVerdictCard(
    result: CorvusCheckResult.QuoteResult,
    modifier: Modifier = Modifier
) {
    val verdictColor = getQuoteVerdictColor(result.quoteVerdict)
    val harm = result.harmAssessment

    val borderColor = when {
        harm.level == HarmLevel.HIGH -> VerdictFalse.copy(alpha = 0.7f)
        harm.level == HarmLevel.MODERATE -> VerdictMisleading
        else -> verdictColor
    }

    val borderWidth = when (harm.level) {
        HarmLevel.HIGH -> 3.dp
        HarmLevel.MODERATE -> 2.dp
        else -> 1.5.dp
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = when (harm.level) {
                    HarmLevel.HIGH -> VerdictFalse.copy(alpha = 0.05f)
                    else -> MaterialTheme.colorScheme.surface
                },
                shape = CorvusShapes.medium
            )
            .border(
                width = borderWidth,
                color = borderColor,
                shape = CorvusShapes.medium
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                LargeQuoteVerdictBadge(verdict = result.quoteVerdict)
                
                // Plausibility sub-label
                if (result.quoteVerdict == QuoteVerdict.UNVERIFIABLE && result.plausibility != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "↳ ${result.plausibility.score.displayLabel()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = result.plausibility.score.labelColor()
                    )
                }
            }
            
            TypeBadge(ClaimType.QUOTE)
        }

        // HIGH harm — prominent warning block
        if (harm.level == HarmLevel.HIGH) {
            HarmWarningBlock(harm)
        }

        // MODERATE harm — inline tag
        if (harm.level == HarmLevel.MODERATE) {
            HarmInlineTag(harm)
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Speaker: ${result.speaker}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            
            if (result.originalDate != null) {
                Text(
                    text = "Claimed Date: ${result.originalDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Confidence:",
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

        if (result.quoteVerdict == QuoteVerdict.UNVERIFIABLE && result.plausibility != null) {
            PlausibilityDetailCard(result.plausibility)
        }
    }
}
