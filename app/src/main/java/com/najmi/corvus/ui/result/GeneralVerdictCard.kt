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
import com.najmi.corvus.domain.model.Verdict
import com.najmi.corvus.domain.util.confidenceColor
import com.najmi.corvus.domain.util.confidenceLabel
import com.najmi.corvus.ui.components.ConfidenceBar
import com.najmi.corvus.ui.theme.CorvusShapes
import com.najmi.corvus.ui.theme.VerdictFalse
import com.najmi.corvus.ui.theme.VerdictMisleading

@Composable
fun GeneralVerdictCard(
    result: CorvusCheckResult.GeneralResult,
    modifier: Modifier = Modifier
) {
    val verdictColor = getVerdictColor(result.verdict)
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
            VerdictSignalChips(
                recencySignal = result.recencySignal,
                viralDetection = result.viralDetection
            )
            
            if (result.claimType != ClaimType.GENERAL) {
                TypeBadge(result.claimType)
            }
        }

        // LARGE TYPOGRAPHY VERDICT
        LargeVerdictBadge(verdict = result.verdict)

        // Misleading Kernel Summary
        if (result.verdict == Verdict.MISLEADING && result.kernelOfTruth != null) {
            KernelSummaryRow(kernel = result.kernelOfTruth)
        }

        // Plausibility sub-label for UNVERIFIABLE
        if (result.verdict == Verdict.UNVERIFIABLE && result.plausibility != null) {
            Text(
                text = "↳ ${result.plausibility.score.displayLabel()}",
                style = MaterialTheme.typography.labelSmall,
                color = result.plausibility.score.labelColor(),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // MODERATE harm — inline tag (Demoted from banner)
        if (harm.level == HarmLevel.MODERATE) {
            HarmInlineTag(harm)
            Spacer(Modifier.height(4.dp))
        }

        // HIGH harm — prominent warning block (Retained for safety)
        if (harm.level == HarmLevel.HIGH) {
            HarmWarningBlock(harm)
            Spacer(Modifier.height(4.dp))
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

        if ((result.verdict == Verdict.UNVERIFIABLE || result.verdict == Verdict.RECENCY_UNVERIFIABLE) 
            && result.plausibility != null) {
            Spacer(Modifier.height(8.dp))
            PlausibilityDetailCard(result.plausibility)
        }
    }
}
