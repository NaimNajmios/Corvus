package com.najmi.corvus.ui.result

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.najmi.corvus.domain.util.confidenceColor
import com.najmi.corvus.domain.util.confidenceLabel
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = CorvusShapes.medium
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Upper signals row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Signal chips could be added here if needed for quotes
            Text(
                text = "QUOTE VERIFICATION",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            
            TypeBadge(ClaimType.QUOTE)
        }

        // LARGE TYPOGRAPHY VERDICT
        LargeQuoteVerdictBadge(verdict = result.quoteVerdict)

        // Speaker Info
        if (result.speaker.isNotBlank() && result.speaker != "Unknown") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                )
                Text(
                    text = "Attributed to ${result.speaker}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (result.originalDate != null && result.originalDate.isNotBlank()) {
                    Text(
                        text = "• ${result.originalDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Plausibility sub-label
        if (result.quoteVerdict == QuoteVerdict.UNVERIFIABLE && result.plausibility != null) {
            Text(
                text = "↳ ${result.plausibility.score.displayLabel()}",
                style = MaterialTheme.typography.labelSmall,
                color = result.plausibility.score.labelColor(),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Harm Tags
        if (harm.level == HarmLevel.MODERATE) {
            HarmInlineTag(harm)
        }
        if (harm.level == HarmLevel.HIGH) {
            HarmWarningBlock(harm)
        }

        // Confidence Area
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = result.confidence.confidenceLabel(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = result.confidence.confidenceColor(
                        highColor = MaterialTheme.colorScheme.primary,
                        midColor = VerdictMisleading,
                        lowColor = VerdictFalse.copy(alpha = 0.7f)
                    )
                )
                Text(
                    text = "${(result.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ConfidenceBar(
                confidence = result.confidence,
                modifier = Modifier.height(8.dp)
            )
        }

        if (result.quoteVerdict == QuoteVerdict.UNVERIFIABLE && result.plausibility != null) {
            Spacer(Modifier.height(8.dp))
            PlausibilityDetailCard(result.plausibility)
        }
    }
}
